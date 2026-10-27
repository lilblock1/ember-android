package org.lwjgl.system;

import dev.andes.lwjgl.NativeMemory;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Original allocator/access subset. Only registered allocations are accepted. This does
 * not implement unsafe access to arbitrary VM objects, borrowed buffers or C callbacks.
 */
public final class MemoryUtil {
    public static final long NULL = 0L;
    private MemoryUtil() {}
    public interface MemoryAllocator {
        long malloc(long bytes);
        long calloc(long count, long size);
        long realloc(long address, long bytes);
        void free(long address);
    }
    private static final MemoryAllocator ALLOCATOR = new MemoryAllocator() {
        @Override public long malloc(long bytes) { return nmemAlloc(bytes); }
        @Override public long calloc(long count,long size) { return nmemCalloc(count,size); }
        @Override public long realloc(long address,long bytes) { checked(bytes); return NativeMemory.reallocate(address,bytes); }
        @Override public void free(long address) { nmemFree(address); }
    };
    public static MemoryAllocator getAllocator(boolean tracked) { return ALLOCATOR; }
    private static void checked(long bytes) { if (bytes < 0) throw new IllegalArgumentException("Negative native size"); }
    public static long nmemAlloc(long bytes) { checked(bytes); return NativeMemory.allocate(bytes,false); }
    public static long nmemCalloc(long count,long size) { checked(count);checked(size);return NativeMemory.allocate(Math.multiplyExact(count,size),true); }
    public static void nmemFree(long address) { if (address!=0) NativeMemory.free(address); }
    public static ByteBuffer memAlloc(int bytes) {
        checked(bytes);long pointer=nmemAlloc(bytes);if(pointer==0)throw new OutOfMemoryError("Native allocation failed");
        try { return memByteBuffer(pointer,bytes); } catch(Throwable failure) { nmemFree(pointer);throw failure; }
    }
    public static IntBuffer memAllocInt(int count) { return memAlloc(Math.multiplyExact(count,4)).asIntBuffer(); }
    public static FloatBuffer memAllocFloat(int count) { return memAlloc(Math.multiplyExact(count,4)).asFloatBuffer(); }
    private static int elementSize(Buffer buffer) {
        if(buffer instanceof ByteBuffer)return 1;
        if(buffer instanceof ShortBuffer||buffer instanceof CharBuffer)return 2;
        if(buffer instanceof IntBuffer||buffer instanceof FloatBuffer)return 4;
        if(buffer instanceof LongBuffer||buffer instanceof DoubleBuffer)return 8;
        throw new IllegalArgumentException("Unsupported buffer type");
    }
    public static long memAddress0(Buffer buffer) {
        if(buffer==null||!buffer.isDirect())throw new IllegalArgumentException("Direct owned buffer required");
        return NativeMemory.address(buffer,Math.multiplyExact((long)buffer.capacity(),elementSize(buffer)));
    }
    public static long memAddress(ByteBuffer buffer) { return Math.addExact(memAddress0(buffer),buffer.position()); }
    public static void memFree(Buffer buffer) { if(buffer!=null)nmemFree(memAddress0(buffer)); }
    public static ByteBuffer memByteBuffer(long address,int bytes) {
        checked(bytes);if(address==0&&bytes==0)return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
        return NativeMemory.view(address,bytes).order(ByteOrder.nativeOrder());
    }
    public static IntBuffer memIntBuffer(long address,int count) { return memByteBuffer(address,Math.multiplyExact(count,4)).asIntBuffer(); }
    public static ByteBuffer memRealloc(ByteBuffer original,int bytes) {
        checked(bytes);int position=original==null?0:original.position();
        long pointer=NativeMemory.reallocate(original==null?0:memAddress0(original),bytes);
        if(pointer==0)throw new OutOfMemoryError("Native reallocation failed");
        ByteBuffer result=memByteBuffer(pointer,bytes);result.position(Math.min(position,bytes));return result;
    }
    public static void memCopy(long source,long target,long bytes) { checked(bytes);NativeMemory.copy(source,target,bytes); }
    public static void memSet(long address,int value,long bytes) { checked(bytes);NativeMemory.set(address,value,bytes); }
    public static byte memGetByte(long address) { return NativeMemory.getByte(address); }
    public static int memGetInt(long address) { return NativeMemory.getInt(address); }
    public static void memPutByte(long address,byte value) { NativeMemory.putByte(address,value); }
    public static void memPutInt(long address,int value) { NativeMemory.putInt(address,value); }
    public static String memUTF8(long address) { return new String(NativeMemory.utf8(address),StandardCharsets.UTF_8); }
    public static ByteBuffer memSlice(ByteBuffer buffer,int offset,int length) {
        int start=Math.addExact(buffer.position(),offset);
        if(offset<0||start>buffer.limit()||length<0||length>buffer.capacity()-start)throw new IllegalArgumentException("Invalid slice range");
        ByteBuffer view=buffer.duplicate();view.clear();view.position(start);view.limit(start+length);return view.slice().order(buffer.order());
    }
}
