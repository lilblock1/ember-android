package dev.ember.platform.management;

import java.util.List;

/** Deliberate runtime-information subset, not a JMX server implementation. */
public interface RuntimeMXBean {
    List<String> getInputArguments();
    long getUptime();
    long getStartTime();
}
