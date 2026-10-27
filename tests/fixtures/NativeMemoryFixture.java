package fixture;

import java.nio.*;
import org.lwjgl.system.MemoryUtil;

/** Original fixture compiled against upstream 3.3.2; no game code or native reference library. */
public final class NativeMemoryFixture {
    public static int exercise() {
        ByteBuffer bytes = MemoryUtil.memAlloc(32);
        IntBuffer ints = MemoryUtil.memAllocInt(4);
        FloatBuffer floats = MemoryUtil.memAllocFloat(2);
        long zeroed = MemoryUtil.nmemCalloc(4, 4);
        try {
            if (bytes.order() != ByteOrder.nativeOrder()) throw new AssertionError("Byte order");
            long address = MemoryUtil.memAddress0(bytes);
            MemoryUtil.memPutInt(address + 1, 0x12345678); // deliberately unaligned
            if (MemoryUtil.memGetInt(address + 1) != 0x12345678) throw new AssertionError("Native integer");
            ints.put(0, 73);
            if (MemoryUtil.memGetInt(MemoryUtil.memAddress0(ints)) != 73) throw new AssertionError("Typed int address");
            floats.put(0, 1.5f);
            if (MemoryUtil.memGetInt(MemoryUtil.memAddress0(floats)) != Float.floatToIntBits(1.5f)) throw new AssertionError("Typed float address");
            if (MemoryUtil.memGetInt(zeroed) != 0) throw new AssertionError("Calloc");
            MemoryUtil.memCopy(address + 1, zeroed, 4);
            if (MemoryUtil.memGetInt(zeroed) != 0x12345678) throw new AssertionError("Copy");
            MemoryUtil.memSet(address, 65, 8); MemoryUtil.memPutByte(address + 8, (byte)0);
            if (!MemoryUtil.memUTF8(address).equals("AAAAAAAA")) throw new AssertionError("UTF8");
            byte[] unicode = "A\uD83D\uDE42".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (int i=0; i<unicode.length; i++) MemoryUtil.memPutByte(address + i, unicode[i]);
            MemoryUtil.memPutByte(address + unicode.length, (byte)0);
            if (!MemoryUtil.memUTF8(address).equals("A\uD83D\uDE42")) throw new AssertionError("Standard UTF8");
            bytes.position(3);
            if (MemoryUtil.memAddress(bytes) != address + 3) throw new AssertionError("Positioned address");
            ByteBuffer slice = MemoryUtil.memSlice(bytes, 2, 4);
            if (MemoryUtil.memAddress0(slice) != address + 5 || slice.capacity() != 4) throw new AssertionError("Slice address");
            bytes = MemoryUtil.memRealloc(bytes, 64);
            if (bytes.position() != 3 || bytes.get(0) != 65) throw new AssertionError("Reallocation preservation");
            return 73;
        } finally {
            MemoryUtil.memFree(bytes); MemoryUtil.memFree(ints); MemoryUtil.memFree(floats); MemoryUtil.nmemFree(zeroed);
        }
    }
}
