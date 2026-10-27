package dev.ember.platform;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;

public class ReadOnlyJarProviderTest {
    private Path archive(String name) throws Exception {
        Path path = Files.createTempDirectory("ember-jarfs-").resolve("archive with spaces.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry(name)); zip.write("original fixture".getBytes("UTF-8")); zip.closeEntry();
        }
        return path;
    }
    @Test public void readsListsSeeksAndRoundTripsUriWithoutExtraction() throws Exception {
        Path input = archive("nested/hello #?.txt"); ReadOnlyJarProvider provider = new ReadOnlyJarProvider();
        try (FileSystem fs = provider.newFileSystem(input, Collections.emptyMap())) {
            Path path = fs.getPath("/nested/hello #?.txt");
            assertEquals("original fixture", new String(Files.readAllBytes(path), "UTF-8"));
            assertEquals(path, provider.getPath(path.toUri()));
            assertEquals(16, Files.readAttributes(path, BasicFileAttributes.class).size());
            assertTrue(Files.isDirectory(fs.getPath("/nested")));
            try (Stream<Path> tree = Files.walk(fs.getPath("/"))) { assertEquals(3, tree.count()); }
            try (SeekableByteChannel channel = Files.newByteChannel(path)) {
                channel.position(9); ByteBuffer bytes = ByteBuffer.allocate(7); assertEquals(7,channel.read(bytes)); assertEquals("fixture",new String(bytes.array(),"UTF-8"));
                channel.position(100); assertEquals(-1,channel.read(ByteBuffer.allocate(1)));
            }
            assertEquals("/nested/file",fs.getPath("/nested/../nested/./file").normalize().toString());
            assertEquals("hello #?.txt",fs.getPath("/nested").relativize(path).toString());
        }
        assertFalse(Files.exists(input.getParent().resolve("nested")));
    }
    @Test public void writeCreateAndClosedFilesystemOperationsFailExplicitly() throws Exception {
        Path input=archive("file.txt");ReadOnlyJarProvider provider=new ReadOnlyJarProvider();
        FileSystem fs=provider.newFileSystem(input,Collections.emptyMap());Path path=fs.getPath("/file.txt");
        assertTrue(fs.isReadOnly());
        assertThrows(ReadOnlyFileSystemException.class,()->Files.write(path,new byte[]{1}));
        assertThrows(ReadOnlyFileSystemException.class,()->Files.delete(path));
        assertThrows(FileSystemAlreadyExistsException.class,()->provider.newFileSystem(input,Collections.emptyMap()));
        assertThrows(NoSuchFileException.class,()->Files.readAllBytes(fs.getPath("/missing")));
        SeekableByteChannel channel = Files.newByteChannel(path);
        fs.close(); assertFalse(channel.isOpen());
        assertThrows(java.nio.channels.ClosedChannelException.class,()->channel.read(ByteBuffer.allocate(1)));
        assertThrows(ClosedFileSystemException.class,()->Files.readAllBytes(path));
        try(FileSystem reopened=provider.newFileSystem(input,Collections.emptyMap())){assertTrue(reopened.isOpen());}
        assertThrows(ReadOnlyFileSystemException.class,()->provider.newFileSystem(input,Collections.singletonMap("create","true")));
    }
    @Test public void rejectsTraversalAndKeepsNativeFileUrisOnTheirOwnProvider() throws Exception {
        ReadOnlyJarProvider provider=new ReadOnlyJarProvider();Path invalid=archive("../outside.txt");
        assertThrows(IOException.class,()->provider.newFileSystem(invalid,Collections.emptyMap()));
        Path normal=archive("okay.txt");assertEquals(normal,JarFileSystems.get(normal.toUri()));
        assertTrue(JarFileSystems.installedProviders().stream().anyMatch(p->p.getScheme().equals("jar")));
        assertThrows(IllegalArgumentException.class,()->provider.newFileSystem(URI.create("jar:https://attacker.example/archive.zip"),Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class,()->provider.newFileSystem(URI.create("jar:file://attacker.example/archive.zip"),Collections.emptyMap()));
    }
}
