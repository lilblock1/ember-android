package fixture;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;

/** Original API-routing fixture, without Minecraft code or resources. */
public final class ArchiveApiFixture {
    public static int providerCount() { return FileSystemProvider.installedProviders().size(); }
    public static Path uriPath(URI uri) { return Paths.get(uri); }
    public static Path nativePath(String path) { return Paths.get(path); }
    public static FileSystem open(URI uri) throws IOException { return FileSystems.newFileSystem(uri, Collections.emptyMap()); }
}
