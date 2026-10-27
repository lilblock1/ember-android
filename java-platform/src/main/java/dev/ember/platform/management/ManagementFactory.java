package dev.ember.platform.management;

import java.util.*;

/** Original Android process/ART information bridge. No invented desktop launcher flags,
 * fabricated measurements, bundled JVM, JMX server or thread-control implementation.
 */
public final class ManagementFactory {
    private ManagementFactory() {}
    private static final boolean ANDROID = android();
    private static final RuntimeMXBean RUNTIME = ANDROID ? new AndroidRuntime() : new NativeRuntime();
    private static boolean android() {
        try { Class.forName("android.os.Build", false, ManagementFactory.class.getClassLoader()); return true; }
        catch (ClassNotFoundException absent) { return false; }
    }
    private static Object call(String type, String method, Object receiver) {
        try { return Class.forName(type).getMethod(method).invoke(receiver); }
        catch (ReflectiveOperationException failure) { throw new UnsupportedOperationException("Runtime information is unavailable"); }
    }
    public static RuntimeMXBean getRuntimeMXBean() { return RUNTIME; }
    public static List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() {
        if (ANDROID) return Collections.singletonList(new GarbageCollectorMXBean() {
            private long statistic(String name) {
                try {
                    Object value = Class.forName("android.os.Debug").getMethod("getRuntimeStat", String.class).invoke(null, name);
                    return value == null ? -1 : Long.parseLong((String)value);
                } catch (ReflectiveOperationException | NumberFormatException failure) { return -1; }
            }
            @Override public long getCollectionCount() { return statistic("art.gc.gc-count"); }
            @Override public long getCollectionTime() { return statistic("art.gc.gc-time"); }
        });
        List<?> nativeBeans = (List<?>)call("java.lang.management.ManagementFactory", "getGarbageCollectorMXBeans", null);
        List<GarbageCollectorMXBean> result = new ArrayList<>();
        for (Object bean : nativeBeans) result.add(new GarbageCollectorMXBean() {
            @Override public long getCollectionCount() { return ((Number)call("java.lang.management.GarbageCollectorMXBean", "getCollectionCount", bean)).longValue(); }
            @Override public long getCollectionTime() { return ((Number)call("java.lang.management.GarbageCollectorMXBean", "getCollectionTime", bean)).longValue(); }
        });
        return Collections.unmodifiableList(result);
    }
    private static final class AndroidRuntime implements RuntimeMXBean {
        private final long processStart = ((Number)call("android.os.Process", "getStartElapsedRealtime", null)).longValue();
        private final long wallStart = System.currentTimeMillis() - getUptime();
        @Override public List<String> getInputArguments() { return Collections.emptyList(); } // Android app sessions have no Java launcher argument list.
        @Override public long getUptime() { return Math.max(0, ((Number)call("android.os.SystemClock", "elapsedRealtime", null)).longValue() - processStart); }
        @Override public long getStartTime() { return wallStart; } // Wall-time estimate anchored once to actual elapsed process lifetime.
    }
    private static final class NativeRuntime implements RuntimeMXBean {
        private final Object bean = call("java.lang.management.ManagementFactory", "getRuntimeMXBean", null);
        @Override @SuppressWarnings("unchecked") public List<String> getInputArguments() { return Collections.unmodifiableList(new ArrayList<>((List<String>)call("java.lang.management.RuntimeMXBean", "getInputArguments", bean))); }
        @Override public long getUptime() { return ((Number)call("java.lang.management.RuntimeMXBean", "getUptime", bean)).longValue(); }
        @Override public long getStartTime() { return ((Number)call("java.lang.management.RuntimeMXBean", "getStartTime", bean)).longValue(); }
    }
}
