package fixture;

import java.lang.management.ManagementFactory;

/** Original runtime-information API fixture. Argument contents are never printed. */
public final class ManagementApiFixture {
    public static int argumentCount() { return ManagementFactory.getRuntimeMXBean().getInputArguments().size(); }
    public static long start() { return ManagementFactory.getRuntimeMXBean().getStartTime(); }
    public static long uptime() { return ManagementFactory.getRuntimeMXBean().getUptime(); }
    public static long collections() { return ManagementFactory.getGarbageCollectorMXBeans().get(0).getCollectionCount(); }
}
