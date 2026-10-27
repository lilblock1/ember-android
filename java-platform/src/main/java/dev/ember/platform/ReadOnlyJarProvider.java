package dev.ember.platform;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Original, bounded, read-only ZIP/JAR filesystem. No extraction, writes or native binaries. */
public final class ReadOnlyJarProvider extends FileSystemProvider {
    private final Map<Path, ArchiveFileSystem> open = new ConcurrentHashMap<>();
    @Override public String getScheme() { return "jar"; }
    private Path archive(URI uri) {
        if (!"jar".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Expected jar URI");
        String value = uri.getRawSchemeSpecificPart();
        int separator = value.indexOf("!/");
        URI file = URI.create(separator < 0 ? value : value.substring(0, separator));
        if (!"file".equalsIgnoreCase(file.getScheme()) || file.getQuery() != null || file.getFragment() != null || file.getAuthority() != null) throw new IllegalArgumentException("Only local archives are supported");
        return Paths.get(file).toAbsolutePath().normalize();
    }
    @Override public synchronized FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException { return newFileSystem(archive(uri), env); }
    @Override public synchronized FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
        if (path instanceof ArchivePath || !path.getFileSystem().provider().getScheme().equalsIgnoreCase("file")) throw new ProviderMismatchException();
        if (path.toUri().getAuthority() != null) throw new IllegalArgumentException("Only local archives are supported");
        if (env != null && "true".equalsIgnoreCase(String.valueOf(env.get("create")))) throw new ReadOnlyFileSystemException();
        Path key = path.toRealPath();
        ArchiveFileSystem existing = open.get(key);
        if (existing != null && existing.isOpen()) throw new FileSystemAlreadyExistsException();
        ArchiveFileSystem created = new ArchiveFileSystem(this, key);
        open.put(key, created);
        return created;
    }
    @Override public FileSystem getFileSystem(URI uri) {
        Path key;
        try { key = archive(uri).toRealPath(); } catch (IOException failure) { throw new FileSystemNotFoundException(); }
        ArchiveFileSystem value = open.get(key);
        if (value == null || !value.isOpen()) throw new FileSystemNotFoundException();
        return value;
    }
    @Override public Path getPath(URI uri) {
        ArchiveFileSystem fs = (ArchiveFileSystem)getFileSystem(uri);
        String raw = uri.getRawSchemeSpecificPart(); int separator = raw.indexOf("!/");
        if (separator < 0) return fs.getPath("/");
        return fs.getPath(URI.create("file:" + raw.substring(separator + 1)).getPath());
    }
    void closed(ArchiveFileSystem fs) { open.remove(fs.archive, fs); }
    private ArchivePath path(Path value) {
        if (!(value instanceof ArchivePath) || ((ArchivePath)value).fs.provider() != this) throw new ProviderMismatchException();
        ArchivePath result = (ArchivePath)value; result.fs.requireOpen(); return result;
    }
    @Override public void checkAccess(Path path, AccessMode... modes) throws IOException {
        ArchivePath p = path(path); p.fs.entry(p);
        for (AccessMode mode : modes) if (mode != AccessMode.READ) throw new AccessDeniedException(p.toString(), null, "Read-only archive");
    }
    @Override public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        for (OpenOption option : options) if (option != StandardOpenOption.READ) throw new ReadOnlyFileSystemException();
        ArchivePath p = path(path); ArchiveFileSystem.Entry entry = p.fs.entry(p);
        if (entry.isDirectory()) throw new FileSystemException(path.toString(), null, "Is a directory");
        return p.fs.zip.getInputStream(entry.zip);
    }
    @Override public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        for (OpenOption option : options) if (option != StandardOpenOption.READ) throw new ReadOnlyFileSystemException();
        ArchivePath p = path(path); ArchiveFileSystem.Entry entry = p.fs.entry(p);
        if (entry.isDirectory()) throw new FileSystemException(path.toString(), null, "Is a directory");
        if (entry.size() > 32L * 1024 * 1024) throw new IOException("Archive entry exceeds seekable read limit");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = newInputStream(path)) {
            byte[] block = new byte[8192];
            for (int count; (count = input.read(block)) >= 0;) {
                if ((long)output.size() + count > 32L * 1024 * 1024) throw new IOException("Archive entry exceeds limit");
                output.write(block, 0, count);
            }
        }
        return new MemoryChannel(output.toByteArray(), p.fs);
    }
    private static final class MemoryChannel implements SeekableByteChannel {
        private final byte[] bytes; private final ArchiveFileSystem owner; private long position; private boolean open = true;
        MemoryChannel(byte[] bytes, ArchiveFileSystem owner) { this.bytes = bytes; this.owner = owner; }
        private void check() throws ClosedChannelException { if (!isOpen()) throw new ClosedChannelException(); }
        @Override public synchronized int read(ByteBuffer target) throws IOException {
            check(); if (!target.hasRemaining()) return 0; if (position >= bytes.length) return -1;
            int count = (int)Math.min(target.remaining(), bytes.length - position); target.put(bytes, (int)position, count); position += count; return count;
        }
        @Override public int write(ByteBuffer source) { throw new NonWritableChannelException(); }
        @Override public synchronized long position() throws IOException { check(); return position; }
        @Override public synchronized SeekableByteChannel position(long value) throws IOException { check(); if (value < 0) throw new IllegalArgumentException(); position = value; return this; }
        @Override public synchronized long size() throws IOException { check(); return bytes.length; }
        @Override public SeekableByteChannel truncate(long value) { throw new NonWritableChannelException(); }
        @Override public synchronized boolean isOpen() { return open && owner.isOpen(); }
        @Override public synchronized void close() { open = false; }
    }
    @Override public DirectoryStream<Path> newDirectoryStream(Path directory, DirectoryStream.Filter<? super Path> filter) throws IOException {
        ArchivePath p = path(directory); if (!p.fs.entry(p).isDirectory()) throw new NotDirectoryException(p.toString());
        List<Path> children = new ArrayList<>(); String key = p.key(); String prefix = key.isEmpty() ? "" : key + "/";
        for (String name : p.fs.entries.keySet()) {
            if (!name.startsWith(prefix) || name.length() <= prefix.length() || name.substring(prefix.length()).contains("/")) continue;
            Path child = p.resolve(name.substring(prefix.length())); if (filter.accept(child)) children.add(child);
        }
        Collections.sort(children);
        return new DirectoryStream<Path>() {
            private volatile boolean closed;
            private boolean iterated;
            @Override public synchronized Iterator<Path> iterator() {
                if (closed || iterated) throw new IllegalStateException(); iterated = true;
                Iterator<Path> source = children.iterator();
                return new Iterator<Path>() {
                    @Override public boolean hasNext() { return !closed && p.fs.isOpen() && source.hasNext(); }
                    @Override public Path next() { if (!hasNext()) throw new NoSuchElementException(); return source.next(); }
                    @Override public void remove() { throw new ReadOnlyFileSystemException(); }
                };
            }
            @Override public synchronized void close() { closed = true; }
        };
    }
    @Override public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type != BasicFileAttributes.class) throw new UnsupportedOperationException("Only basic archive attributes are supported");
        ArchivePath p = path(path); return type.cast(p.fs.entry(p));
    }
    @Override public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        if (type != BasicFileAttributeView.class) return null;
        return type.cast(new BasicFileAttributeView() {
            @Override public String name() { return "basic"; }
            @Override public BasicFileAttributes readAttributes() throws IOException { return ReadOnlyJarProvider.this.readAttributes(path, BasicFileAttributes.class, options); }
            @Override public void setTimes(FileTime modified, FileTime accessed, FileTime created) { throw new ReadOnlyFileSystemException(); }
        });
    }
    @Override public Map<String, Object> readAttributes(Path path, String specification, LinkOption... options) throws IOException {
        String[] view = specification.split(":", 2); String names = view.length == 1 ? view[0] : view[1];
        if (view.length == 2 && !view[0].equals("basic")) throw new UnsupportedOperationException();
        BasicFileAttributes value = readAttributes(path, BasicFileAttributes.class, options); Map<String,Object> all = new LinkedHashMap<>();
        all.put("size", value.size()); all.put("lastModifiedTime", value.lastModifiedTime()); all.put("lastAccessTime", value.lastAccessTime()); all.put("creationTime", value.creationTime());
        all.put("isRegularFile", value.isRegularFile()); all.put("isDirectory", value.isDirectory()); all.put("isSymbolicLink", false); all.put("isOther", false); all.put("fileKey", value.fileKey());
        if (names.equals("*")) return all;
        Map<String,Object> selected = new LinkedHashMap<>(); for (String name : names.split(",")) { if (!all.containsKey(name)) throw new IllegalArgumentException("Unknown archive attribute"); selected.put(name, all.get(name)); }
        return selected;
    }
    @Override public boolean isSameFile(Path left, Path right) throws IOException { checkAccess(left); checkAccess(right); return left.toAbsolutePath().normalize().equals(right.toAbsolutePath().normalize()); }
    @Override public boolean isHidden(Path path) throws IOException { checkAccess(path); return path.getFileName() != null && path.getFileName().toString().startsWith("."); }
    @Override public FileStore getFileStore(Path path) throws IOException { ArchivePath p = path(path); p.fs.entry(p); return p.fs.store; }
    @Override public void createDirectory(Path path, FileAttribute<?>... attrs) { throw new ReadOnlyFileSystemException(); }
    @Override public void delete(Path path) { throw new ReadOnlyFileSystemException(); }
    @Override public void copy(Path source, Path target, CopyOption... options) { throw new ReadOnlyFileSystemException(); }
    @Override public void move(Path source, Path target, CopyOption... options) { throw new ReadOnlyFileSystemException(); }
    @Override public void setAttribute(Path path, String attribute, Object value, LinkOption... options) { throw new ReadOnlyFileSystemException(); }
}
