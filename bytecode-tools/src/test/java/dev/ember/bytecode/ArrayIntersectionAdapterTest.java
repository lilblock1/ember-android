package dev.ember.bytecode;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.io.*;

public class ArrayIntersectionAdapterTest {
    public interface Marker {}
    public enum Values implements Marker { ONE, TWO }
    public static class WideArguments {
        private static <T extends Marker> int count(long marker, T[] values, double ratio) {
            if (marker != 42L || ratio != 0.5) throw new AssertionError("Argument order/width changed");
            return values.length;
        }
        public static <E extends Enum<E> & Marker> int run(java.util.function.Supplier<E[]> values) {
            E[] array = values.get();
            return count(42L, array, 0.5);
        }
    }
    private byte[] fixture(String suffix) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixture/EnumIntersectionFixture" + suffix + ".class")) {
            return Objects.requireNonNull(input).readAllBytes();
        }
    }
    @Test public void originalAndAdaptedFixtureHaveSameJvmResult() throws Exception {
        assertEquals(2, fixture.EnumIntersectionFixture.run());
        byte[] original = fixture("");
        var result = ArrayIntersectionAdapter.adaptClass(original, Set.of("fixture/EnumIntersectionFixture$Named"));
        assertEquals(1, result.insertedCasts());
        Map<String, byte[]> classes = new HashMap<>();
        classes.put("fixture.EnumIntersectionFixture", result.bytes());
        for (String suffix : List.of("$Named", "$Choice")) classes.put("fixture.EnumIntersectionFixture" + suffix, fixture(suffix));
        ClassLoader loader = new ClassLoader(ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name); if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        assertEquals(2, loader.loadClass("fixture.EnumIntersectionFixture").getMethod("run").invoke(null));
        assertFalse(Arrays.equals(original, result.bytes()));
    }
    @Test public void transformIsIdempotentAndRequiresKnownInterface() throws Exception {
        byte[] original = fixture("");
        assertEquals(0, ArrayIntersectionAdapter.adaptClass(original, Set.of()).insertedCasts());
        Set<String> interfaces = Set.of("fixture/EnumIntersectionFixture$Named");
        byte[] changed = ArrayIntersectionAdapter.adaptClass(original, interfaces).bytes();
        assertEquals(0, ArrayIntersectionAdapter.adaptClass(changed, interfaces).insertedCasts());
    }
    @Test public void preservesWideArgumentsAndEvaluatesSupplierOnce() throws Exception {
        String name = WideArguments.class.getName();
        byte[] original;
        try (InputStream stream = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            original = Objects.requireNonNull(stream).readAllBytes();
        }
        var adapted = ArrayIntersectionAdapter.adaptClass(original, Set.of(Marker.class.getName().replace('.', '/')));
        assertEquals(1, adapted.insertedCasts());
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String value, boolean resolve) throws ClassNotFoundException {
                if (!value.equals(name)) return super.loadClass(value, resolve);
                Class<?> type = findLoadedClass(value);
                if (type == null) type = defineClass(value, adapted.bytes(), 0, adapted.bytes().length);
                if (resolve) resolveClass(type);
                return type;
            }
        };
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Supplier<Values[]> source = () -> { calls.incrementAndGet(); return Values.values(); };
        assertEquals(2, loader.loadClass(name).getMethod("run", java.util.function.Supplier.class).invoke(null, source));
        assertEquals(1, calls.get());
    }
    @Test public void archiveApiBridgeIsNarrowIdempotentAndPreservesNativePaths() throws Exception {
        byte[] original;
        try (InputStream input = getClass().getResourceAsStream("/fixture/ArchiveApiFixture.class")) { original = Objects.requireNonNull(input).readAllBytes(); }
        var adapted = ArrayIntersectionAdapter.adaptClass(original, Set.of());
        assertEquals(0, adapted.insertedCasts()); assertEquals(3, adapted.redirectedCalls());
        assertEquals(0, ArrayIntersectionAdapter.adaptClass(adapted.bytes(), Set.of()).redirectedCalls());
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals("fixture.ArchiveApiFixture")) return super.loadClass(name, resolve);
                Class<?> result=findLoadedClass(name);if(result==null)result=defineClass(name,adapted.bytes(),0,adapted.bytes().length);return result;
            }
        };
        Class<?> type=loader.loadClass("fixture.ArchiveApiFixture");
        assertEquals(java.nio.file.Paths.get("fixture"),type.getMethod("nativePath",String.class).invoke(null,"fixture"));
        var path=java.nio.file.Files.createTempFile("bridge-native-", ".txt");
        assertEquals(path,type.getMethod("uriPath",java.net.URI.class).invoke(null,path.toUri()));
        assertTrue((Integer)type.getMethod("providerCount").invoke(null)>0);
    }
    @Test public void runtimeInformationTypesAndCallsRetargetWithoutInventingValues() throws Exception {
        byte[] original;
        try (InputStream input=getClass().getResourceAsStream("/fixture/ManagementApiFixture.class")){original=Objects.requireNonNull(input).readAllBytes();}
        var adapted=ArrayIntersectionAdapter.adaptClass(original,Set.of());
        assertTrue(adapted.redirectedCalls()>0);assertTrue(adapted.rewrittenTypes()>0);
        var twice=ArrayIntersectionAdapter.adaptClass(adapted.bytes(),Set.of());
        assertEquals(0,twice.redirectedCalls());assertEquals(0,twice.rewrittenTypes());
        ClassLoader loader=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!name.equals("fixture.ManagementApiFixture"))return super.loadClass(name,resolve);
                Class<?> value=findLoadedClass(name);if(value==null)value=defineClass(name,adapted.bytes(),0,adapted.bytes().length);return value;
            }
        };
        Class<?> type=loader.loadClass("fixture.ManagementApiFixture");
        var runtime=java.lang.management.ManagementFactory.getRuntimeMXBean();
        assertEquals(runtime.getInputArguments().size(),type.getMethod("argumentCount").invoke(null));
        assertEquals(runtime.getStartTime(),type.getMethod("start").invoke(null));
        assertTrue((Long)type.getMethod("uptime").invoke(null)>=0);
        assertTrue((Long)type.getMethod("collections").invoke(null)>=-1);
    }
}
