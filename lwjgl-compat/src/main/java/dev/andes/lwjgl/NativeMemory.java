package dev.andes.lwjgl;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/** JNI access to process-owned, bounded ARM64 allocations. No arbitrary pointer access. */
public final class NativeMemory {
    static { System.loadLibrary("andes_glfw"); }
    private NativeMemory() {}
    public static native long allocate(long bytes, boolean zero);
    public static native long reallocate(long address, long bytes);
    public static native void free(long address);
    public static native ByteBuffer view(long address, int bytes);
    public static native long address(Buffer buffer, long bytes);
    public static native void copy(long source, long target, long bytes);
    public static native void set(long address, int value, long bytes);
    public static native byte getByte(long address);
    public static native int getInt(long address);
    public static native void putByte(long address, byte value);
    public static native void putInt(long address, int value);
    public static native byte[] utf8(long address);
    public static native long allocatedBytes();
}
