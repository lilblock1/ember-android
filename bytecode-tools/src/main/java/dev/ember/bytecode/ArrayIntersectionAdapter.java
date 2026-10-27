package dev.ember.bytecode;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.analysis.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Original JVM-to-ART compatibility pass for Enum/interface intersection arrays.
 * HotSpot accepts a statically Enum[] argument to an interface-array parameter. ART
 * requires an explicit array checkcast. Insert only that check, on a private derivative.
 * No ownership/authentication code, game-specific classes or gameplay logic are patched.
 */
public final class ArrayIntersectionAdapter {
    public record Result(byte[] bytes, int insertedCasts, int redirectedCalls, int rewrittenTypes) {}
    private static final int MAX_CLASS = 16 * 1024 * 1024;

    private static final class TypedInterpreter extends BasicInterpreter {
        TypedInterpreter() { super(Opcodes.ASM9); }
        @Override public BasicValue newValue(Type type) {
            if (type != null && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)) return new BasicValue(type);
            return super.newValue(type);
        }
    }

    public static Result adaptClass(byte[] original, Set<String> interfaces) throws AnalyzerException {
        if (original.length > MAX_CLASS) throw new IllegalArgumentException("Class exceeds analysis limit");
        // Avoid full tree/frame analysis on classes without this erased intersection type.
        String symbols = new String(original, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!symbols.contains("[Ljava/lang/Enum;") && !symbols.contains("java/nio/file/spi/FileSystemProvider") && !symbols.contains("java/nio/file/Paths") && !symbols.contains("java/nio/file/FileSystems") && !symbols.contains("java/lang/management/")) return new Result(original, 0, 0, 0);
        ClassReader reader = new ClassReader(original);
        ClassNode node = new ClassNode(); final int[] mappedTypes = {0};
        reader.accept(new ClassRemapper(node, new Remapper() {
            @Override public String map(String name) {
                if (name.equals("java/lang/management/RuntimeMXBean") || name.equals("java/lang/management/GarbageCollectorMXBean")) {
                    mappedTypes[0]++; return "dev/ember/platform/management/" + name.substring(name.lastIndexOf('/') + 1);
                }
                return name;
            }
        }), 0);
        int changes = 0, redirected = 0;
        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                boolean providerLookup = call.owner.equals("java/nio/file/spi/FileSystemProvider") && call.name.equals("installedProviders") && call.desc.equals("()Ljava/util/List;");
                boolean uriPath = call.owner.equals("java/nio/file/Paths") && call.name.equals("get") && call.desc.equals("(Ljava/net/URI;)Ljava/nio/file/Path;");
                boolean uriFilesystem = call.owner.equals("java/nio/file/FileSystems") && call.name.equals("newFileSystem") && call.desc.equals("(Ljava/net/URI;Ljava/util/Map;)Ljava/nio/file/FileSystem;");
                if (providerLookup || uriPath || uriFilesystem) { call.owner = "dev/ember/platform/JarFileSystems"; redirected++; }
                if (call.owner.equals("java/lang/management/ManagementFactory") && (call.name.equals("getRuntimeMXBean") || call.name.equals("getGarbageCollectorMXBeans"))) {
                    call.owner = "dev/ember/platform/management/ManagementFactory"; redirected++;
                }
            }
            Frame<BasicValue>[] frames = new Analyzer<>(new TypedInterpreter()).analyze(node.name, method);
            AbstractInsnNode[] instructions = method.instructions.toArray();
            int scratchBase = method.maxLocals;
            int scratchSize = 0;
            for (int index = 0; index < instructions.length; index++) {
                if (!(instructions[index] instanceof MethodInsnNode call) || frames[index] == null) continue;
                Type[] arguments = Type.getArgumentTypes(call.desc);
                boolean[] cast = new boolean[arguments.length];
                boolean needed = false;
                Frame<BasicValue> frame = frames[index];
                for (int arg = 0; arg < arguments.length; arg++) {
                    Type target = arguments[arg];
                    if (target.getSort() != Type.ARRAY || target.getDimensions() != 1 || target.getElementType().getSort() != Type.OBJECT
                            || !interfaces.contains(target.getElementType().getInternalName())) continue;
                    BasicValue value = frame.getStack(frame.getStackSize() - arguments.length + arg);
                    if (value != null && Type.getType("[Ljava/lang/Enum;").equals(value.getType())) { cast[arg] = true; needed = true; }
                }
                if (!needed) continue;
                int[] locals = new int[arguments.length];
                int cursor = scratchBase;
                for (int arg = 0; arg < arguments.length; arg++) { locals[arg] = cursor; cursor += arguments[arg].getSize(); }
                scratchSize = Math.max(scratchSize, cursor - scratchBase);
                InsnList prelude = new InsnList();
                // The receiver remains on the stack. Save/reload all arguments in order,
                // including wide primitives, so argument evaluation order is preserved.
                for (int arg = arguments.length - 1; arg >= 0; arg--)
                    prelude.add(new VarInsnNode(arguments[arg].getOpcode(Opcodes.ISTORE), locals[arg]));
                for (int arg = 0; arg < arguments.length; arg++) {
                    prelude.add(new VarInsnNode(arguments[arg].getOpcode(Opcodes.ILOAD), locals[arg]));
                    if (cast[arg]) { prelude.add(new TypeInsnNode(Opcodes.CHECKCAST, arguments[arg].getDescriptor())); changes++; }
                }
                method.instructions.insertBefore(call, prelude);
            }
            method.maxLocals = scratchBase + scratchSize;
        }
        if (changes == 0 && redirected == 0 && mappedTypes[0] == 0) return new Result(original, 0, 0, 0);
        // No branches or labels are added. Original stack-map frames remain valid; newly
        // allocated scratch locals are dead at every existing frame boundary.
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return new Result(writer.toByteArray(), changes, redirected, mappedTypes[0]);
    }

    private static byte[] bounded(ZipFile jar, ZipEntry entry) throws IOException {
        if (entry.getSize() < 0 || entry.getSize() > MAX_CLASS) throw new IOException("Class exceeds input limit");
        try (InputStream input = jar.getInputStream(entry)) {
            byte[] value = input.readNBytes(MAX_CLASS + 1);
            if (value.length > MAX_CLASS) throw new IOException("Class exceeds input limit");
            return value;
        }
    }
    private static String sha256(Path path) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) { byte[] block = new byte[65536]; for (int n; (n=input.read(block)) >= 0;) hash.update(block,0,n); }
        return HexFormat.of().formatHex(hash.digest());
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: ArrayIntersectionAdapter <new-private-output-directory> <input.jar>...");
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(output)) throw new IllegalArgumentException("Use a new private output directory");
        List<Path> inputs = Arrays.stream(args).skip(1).map(Path::of).map(Path::toAbsolutePath).toList();
        Set<String> interfaces = new HashSet<>();
        for (Path path : inputs) try (ZipFile jar = new ZipFile(path.toFile())) {
            if (jar.size() > 200000) throw new IOException("Archive exceeds entry limit");
            long archiveBytes = 0;
            for (Enumeration<? extends ZipEntry> entries = jar.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                if (entry.getSize() < 0 || (archiveBytes += entry.getSize()) > 512L * 1024 * 1024) throw new IOException("Archive exceeds expanded size limit");
                if (entry.getName().endsWith(".class")) {
                    ClassReader reader = new ClassReader(bounded(jar, entry));
                    if ((reader.getAccess() & Opcodes.ACC_INTERFACE) != 0) interfaces.add(reader.getClassName());
                }
            }
        }
        Files.createDirectories(output);
        List<String> report = new ArrayList<>(); int total = 0, totalRedirects = 0;
        for (Path path : inputs) {
            Map<String, byte[]> changed = new HashMap<>(); int count = 0, redirects = 0;
            try (ZipFile jar = new ZipFile(path.toFile())) {
                for (Enumeration<? extends ZipEntry> entries = jar.entries(); entries.hasMoreElements();) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) continue;
                    Result result = adaptClass(bounded(jar, entry), interfaces);
                    if (result.insertedCasts() > 0 || result.redirectedCalls() > 0 || result.rewrittenTypes() > 0) { changed.put(entry.getName(), result.bytes()); count += result.insertedCasts(); redirects += result.redirectedCalls() + result.rewrittenTypes(); }
                }
                String inputHash = sha256(path);
                if (!changed.isEmpty()) try (ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output.resolve(inputHash + ".jar"), StandardOpenOption.CREATE_NEW))) {
                    Set<String> names = new HashSet<>();
                    for (Enumeration<? extends ZipEntry> entries = jar.entries(); entries.hasMoreElements();) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!names.add(name)) throw new IOException("Duplicate archive entry");
                        String upper = name.toUpperCase(Locale.ROOT);
                        if (upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC"))) continue;
                        ZipEntry fresh = new ZipEntry(name); fresh.setTime(0); target.putNextEntry(fresh);
                        byte[] replacement = changed.get(name);
                        if (replacement != null) target.write(replacement);
                        else try (InputStream source = jar.getInputStream(entry)) { source.transferTo(target); }
                        target.closeEntry();
                    }
                }
                report.add(inputHash + "\t" + count + "\t" + redirects + "\t" + changed.size()); total += count; totalRedirects += redirects;
            }
        }
        Files.write(output.resolve("adaptation.tsv"), report);
        System.out.println("Inserted " + total + " checked array conversions and " + totalRedirects + " platform API references; original inputs preserved.");
    }
}
