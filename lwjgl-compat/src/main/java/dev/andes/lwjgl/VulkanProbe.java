package dev.andes.lwjgl;

/** Read-only driver capabilities for planning desktop-GL translation; no rendering claim. */
public final class VulkanProbe {
    static { System.loadLibrary("andes_glfw"); }
    private VulkanProbe() {}
    // Seven values per physical device: API, core feature bits, sampler/UBO limits,
    // selected extension bits, vendor ID, physical device type. No UUID or device identity.
    public static native long[] inspect();
}
