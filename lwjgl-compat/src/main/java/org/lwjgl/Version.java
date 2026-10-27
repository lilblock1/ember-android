package org.lwjgl;

/** Diagnostic identity for the original adapter, not an upstream LWJGL distribution. */
public final class Version {
    private Version() {}
    public static String getVersion() { return "Ember Android adapter (LWJGL 3.3.2 API subset)"; }
}
