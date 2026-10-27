package dev.ember.platform;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

final class ArchiveFileSystem extends FileSystem {
    final Path archive;
    final ZipFile zip;
    final Map<String,Entry> entries;
    final FileStore store;
    private final ReadOnlyJarProvider provider;
    private final AtomicBoolean open = new AtomicBoolean(true);
    ArchiveFileSystem(ReadOnlyJarProvider provider, Path archive) throws IOException {
        this.provider = provider; this.archive = archive;
        ZipFile reader = new ZipFile(archive.toFile());
        Map<String,Entry> indexed = new TreeMap<>(); Set<String> originalNames = new HashSet<>(); long size = 0;
        try {
            if (reader.size() > 200000) throw new IOException("Archive exceeds entry limit");
            indexed.put("", new Entry("", null, true));
            for (Enumeration<? extends ZipEntry> values = reader.entries(); values.hasMoreElements();) {
                ZipEntry entry = values.nextElement(); String name = entry.getName();
                if (!originalNames.add(name) || name.startsWith("/") || name.contains("\\") || name.indexOf('\0') >= 0
                        || name.matches("^[A-Za-z]:.*") || Arrays.asList(name.split("/")).contains("..")) throw new IOException("Unsafe archive entry");
                if (entry.getSize() < 0 || (size += entry.getSize()) > 512L * 1024 * 1024) throw new IOException("Archive exceeds expanded size limit");
                String key = name.endsWith("/") ? name.substring(0, name.length()-1) : name;
                if (key.isEmpty() || key.contains("//") || Arrays.asList(key.split("/")).contains(".")) throw new IOException("Ambiguous archive entry");
                Entry previous = indexed.get(key);
                if (previous != null && (!previous.directory || !entry.isDirectory())) throw new IOException("Archive path conflict");
                indexed.put(key, new Entry(key, entry, entry.isDirectory()));
                int parent = key.lastIndexOf('/');
                while (parent >= 0) {
                    String directory = key.substring(0, parent); Entry existing = indexed.get(directory);
                    if (existing != null && !existing.directory) throw new IOException("Archive directory conflict");
                    if (existing == null) indexed.put(directory, new Entry(directory, null, true));
                    parent = directory.lastIndexOf('/');
                }
            }
        } catch (Throwable failure) { reader.close(); throw failure; }
        zip = reader; entries = Collections.unmodifiableMap(indexed);
        final long total = size;
        store = new FileStore() {
            @Override public String name() { return archive.getFileName().toString(); }
            @Override public String type() { return "zipfs-readonly"; }
            @Override public boolean isReadOnly() { return true; }
            @Override public long getTotalSpace() { return total; }
            @Override public long getUsableSpace() { return 0; }
            @Override public long getUnallocatedSpace() { return 0; }
            @Override public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) { return type == BasicFileAttributeView.class; }
            @Override public boolean supportsFileAttributeView(String name) { return "basic".equals(name); }
            @Override public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) { return null; }
            @Override public Object getAttribute(String name) { throw new UnsupportedOperationException(); }
        };
    }
    void requireOpen() { if (!open.get()) throw new ClosedFileSystemException(); }
    Entry entry(ArchivePath path) throws IOException {
        requireOpen(); Entry value = entries.get(path.key()); if (value == null) throw new NoSuchFileException(path.toString()); return value;
    }
    static final class Entry implements BasicFileAttributes {
        final String name; final ZipEntry zip; final boolean directory;
        Entry(String name, ZipEntry zip, boolean directory) { this.name = name; this.zip = zip; this.directory = directory; }
        @Override public FileTime lastModifiedTime() { return FileTime.fromMillis(zip == null ? 0 : Math.max(0,zip.getTime())); }
        @Override public FileTime lastAccessTime() { return lastModifiedTime(); }
        @Override public FileTime creationTime() { return lastModifiedTime(); }
        @Override public boolean isRegularFile() { return !directory; }
        @Override public boolean isDirectory() { return directory; }
        @Override public boolean isSymbolicLink() { return false; }
        @Override public boolean isOther() { return false; }
        @Override public long size() { return directory ? 0 : zip.getSize(); }
        @Override public Object fileKey() { return name; }
    }
    @Override public FileSystemProvider provider() { return provider; }
    @Override public void close() throws IOException { if (open.compareAndSet(true,false)) { try { zip.close(); } finally { provider.closed(this); } } }
    @Override public boolean isOpen() { return open.get(); }
    @Override public boolean isReadOnly() { return true; }
    @Override public String getSeparator() { return "/"; }
    @Override public Iterable<Path> getRootDirectories() { requireOpen(); return Collections.singletonList(getPath("/")); }
    @Override public Iterable<FileStore> getFileStores() { requireOpen(); return Collections.singletonList(store); }
    @Override public Set<String> supportedFileAttributeViews() { return Collections.singleton("basic"); }
    @Override public Path getPath(String first, String... more) {
        requireOpen(); StringBuilder value = new StringBuilder(first);
        for (String part : more) { if (value.length() != 0 && value.charAt(value.length()-1) != '/') value.append('/'); value.append(part); }
        return new ArchivePath(this,value.toString());
    }
    @Override public PathMatcher getPathMatcher(String syntaxAndPattern) {
        PathMatcher delegate = FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
        return path -> delegate.matches(Paths.get(path.toString()));
    }
    @Override public UserPrincipalLookupService getUserPrincipalLookupService() { throw new UnsupportedOperationException("Archive principals are unsupported"); }
    @Override public WatchService newWatchService() { throw new UnsupportedOperationException("Read-only archive watches are unsupported"); }
}
