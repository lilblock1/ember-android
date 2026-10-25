# LWJGL 3.3.2 / ARM64 execution boundary

`lwjgl-compat` supplies an original small GLFW/GL11/GL11C API subset and native ARM64
EGL/GLES bridge. Upstream 3.3.2 jars are compile/test references only, not APK contents.
The original Java 17 fixture compiles against real LWJGL, becomes DEX and loads against
the Android subset. It creates a context, clears a surface, reads a pixel and checks RGB.

Implemented: explicit GLES 3.0 hints; one Android window or pbuffer; context binding/release;
window swapping/interval; framebuffer size; close flag; timer; clear/color/viewport/query;
bounds-checked direct RGBA8 readback; owner-thread/stale-handle checks and resource teardown.
Handles are validated IDs, not unchecked Java-supplied pointers.

Desktop OpenGL fails explicitly. Unknown hints, monitors/shared contexts and event pumping
are unsupported. Callbacks, most GLFW/LWJGL classes, native memory APIs, shaders/textures/
buffers, audio, Vulkan translation and AOT game compilation remain unimplemented. This
subset cannot launch Minecraft 1.20.4. ART/DEX is managed execution, not AOT compilation.

## Physical evidence

S25+ / SM-S936U / Android 15 API 35 / ARM64: both EGL instrumentation tests passed after
fixing offscreen behavior. Pbuffers have no display refresh/swap; interval control is
skipped and work is flushed instead. The initial failing test is recorded in validation.
The visible app surface also returned the expected result in its UI:

```
Pixel verified: 64,128,191
OpenGL ES 3.2 V@0800.17.11
Adreno (TM) 830
```

Four host tests compare selected method descriptors with actual upstream 3.3.2, reject
desktop/unsupported contexts and check Java-side readback bounds. These are subset results,
not general LWJGL/Minecraft compatibility evidence.

## Next real-client step

Install Java 1.20.4 through the official launcher on an entitled account, then provide the
local installation directory. Keep game files out of public repositories. The new audit
reads an existing local client, checks size/SHA-1 against trusted public Mojang metadata,
and inventories exact LWJGL member descriptors against the compiled subset:

```
python tools/client_linkage_audit.py --client /owned/1.20.4.jar --version 1.20.4 --shim-classes lwjgl-compat/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes --output build/client-linkage.json
```

Only public JSON is downloaded. The JAR is never executed or altered. An integrity match
is not ownership proof. Actual client execution still needs approved ownership integration
and the missing Java/platform/desktop graphics components. No real client has run yet.
