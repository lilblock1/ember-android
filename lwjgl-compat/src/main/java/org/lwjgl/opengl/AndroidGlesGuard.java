package org.lwjgl.opengl;

import dev.andes.lwjgl.NativeBridge;

/** These adapters operate only on the explicit GLES context. No desktop GL translation. */
final class AndroidGlesGuard {
    static void current() {
        if (NativeBridge.currentContext() == 0) throw new IllegalStateException("An owned GLES context must be current on this thread");
    }
    static int offset(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) throw new IllegalArgumentException("GPU buffer offset exceeds Android limits");
        return (int)value;
    }
}
