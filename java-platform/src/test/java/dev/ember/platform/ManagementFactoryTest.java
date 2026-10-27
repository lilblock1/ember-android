package dev.ember.platform;

import org.junit.Test;
import static org.junit.Assert.*;
import dev.ember.platform.management.ManagementFactory;

public class ManagementFactoryTest {
    @Test public void hostBridgeUsesActualRuntimeInformationWithoutPrintingArguments() {
        var nativeBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
        var bridge = ManagementFactory.getRuntimeMXBean();
        assertTrue(nativeBean.getInputArguments().equals(bridge.getInputArguments()));
        assertEquals(nativeBean.getStartTime(),bridge.getStartTime());
        long before=nativeBean.getUptime();long measured=bridge.getUptime();long after=nativeBean.getUptime();
        assertTrue(measured>=before&&measured<=after);
        assertThrows(UnsupportedOperationException.class,()->bridge.getInputArguments().add("not-a-real-launch-flag"));
        assertEquals(java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().size(),ManagementFactory.getGarbageCollectorMXBeans().size());
        for(var bean:ManagementFactory.getGarbageCollectorMXBeans()) { assertTrue(bean.getCollectionCount()>=-1);assertTrue(bean.getCollectionTime()>=-1); }
    }
}
