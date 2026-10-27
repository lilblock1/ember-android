package dev.ember.platform;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;

/** Supplies an original read-only archive provider when Android has no jar provider.
 * Native/default filesystem calls retain their normal provider and semantics.
 */
public final class JarFileSystems {
    private static final ReadOnlyJarProvider ARCHIVES = new ReadOnlyJarProvider();
    private JarFileSystems() {}
    public static List<FileSystemProvider> installedProviders() {
        List<FileSystemProvider> result = new ArrayList<>(FileSystemProvider.installedProviders());
        for (FileSystemProvider provider : result) if (provider.getScheme().equalsIgnoreCase("jar")) return Collections.unmodifiableList(result);
        result.add(ARCHIVES);
        return Collections.unmodifiableList(result);
    }
    private static FileSystemProvider archiveProvider() {
        for (FileSystemProvider provider : installedProviders()) if (provider.getScheme().equalsIgnoreCase("jar")) return provider;
        throw new AssertionError("Archive provider initialization failed");
    }
    public static Path get(URI uri) {
        return "jar".equalsIgnoreCase(uri.getScheme()) ? archiveProvider().getPath(uri) : Paths.get(uri);
    }
    public static FileSystem newFileSystem(URI uri, Map<String, ?> environment) throws IOException {
        return "jar".equalsIgnoreCase(uri.getScheme()) ? archiveProvider().newFileSystem(uri, environment) : FileSystems.newFileSystem(uri, environment);
    }
}
