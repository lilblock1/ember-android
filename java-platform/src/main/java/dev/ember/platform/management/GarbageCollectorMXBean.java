package dev.ember.platform.management;

/** Android exposes approximate GC statistics; -1 means a statistic is unavailable. */
public interface GarbageCollectorMXBean {
    long getCollectionCount();
    long getCollectionTime();
}
