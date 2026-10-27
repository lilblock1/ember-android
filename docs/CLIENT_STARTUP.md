# Real client startup: October 27, 2026

Minecraft 1.20.4 has not reached its main menu. This is an authenticated ART/DEX
compatibility experiment on the owner's S25+ / Android 15 / ARM64, not native AOT.
The original client and 36 Java libraries remain private, intact and integrity checked.
Each client experiment rechecks purchased ownership. Replacement APK installs preserve
the phone's encrypted session; no account values are exported.

| Boundary | Observed result |
|---|---|
| Guava/failureaccess and 36 selected Java artifacts | Resolve on S25+ after official verified downloads |
| Erased Enum[] passed to interface[] | Original explicit-cast adapter passes original fixture and real-client verifier |
| JAR filesystem provider | Original bounded read-only NIO provider passes device read/walk/readonly test; real startup passes provider failure |
| RuntimeMXBean / GC subset | Android process clock and actual ART statistics tested; narrow call/type retargeting passes real startup |
| LWJGL MemoryUtil subset | Original C++ ARM64 allocator/access and upstream-compiled fixture pass phone tests |
| LWJGL version diagnostic | Identifies Ember's API subset, without claiming upstream distribution |
| Authenticated real client | Reaches GLX._initGlfw / RenderSystem.initBackendSystem; missing org.lwjgl.opengl.GL |

Evidence: `enum-intersection-s25.json`, `jarfs-startup-s25.json`,
`runtime-startup-s25.json`, `memory-startup-s25.json` and
`memory-version-startup-s25.json` in `docs/evidence/`. Earlier reports intentionally
retain the historical errors they observed. An instrumentation diagnostic test passing
means a blocker was collected, not a successful game launch.

## Implemented platform subsets

`java-platform` provides read-only ZIP/JAR paths, directory walking, basic attributes,
bounded seeking and close semantics. It performs no extraction and rejects writes,
traversal and ambiguous entries. Android's normal file provider is preserved. No desktop
AWT, full JMX server, JVM flags or thread-management implementation is implied.

`bytecode-tools` retargets only installedProviders, URI Paths.get and URI newFileSystem
to the archive bridge. It remaps RuntimeMXBean/GarbageCollectorMXBean types and the two
supported factory calls. Original files remain unchanged; generated derivatives/DEX
are private and checked against a phone-side hash manifest.

Native memory uses explicit malloc/calloc/realloc/free, native-order direct and typed
views, bounded byte/int/copy/set and UTF-8 access. Registry access is synchronized;
ARM64 pointer tags are preserved. Limits: 256 MiB per allocation, 512 MiB total and
131072 live blocks. JNI accesses reject foreign/out-of-range/freed addresses. Buffer
lifetime is the caller's responsibility: direct Java buffer access after free is unsafe,
and address reuse is not protected by temporal handles. This is not a general arbitrary
pointer, borrowed native buffer, callback trampoline or complete LWJGL implementation.

The startup probe keeps credentials inside the phone, uses the real session/profile,
and redacts console output. When Minecraft writes a private crash report and terminates
the test process, a separate opt-in test exports only fresh-report class names and
stack-frame names. Raw authenticated crash text remains inside the phone.

## Work required before a menu

The current native backend implements explicit GLES probes. Vanilla 1.20.4 expects
desktop OpenGL and GLFW callbacks, capabilities, window/input and timing behavior.
Adding an empty GL class or relabeling GLES as desktop OpenGL would not implement this.
The static inventory now finds 224 missing declarations out of 292 direct LWJGL member
references; inherited, reflective and behavioral coverage remains outside that count.

The next graphics experiment should demonstrate actual desktop GLSL/context/draw
behavior through a translator, then bind the missing LWJGL APIs and callbacks. Mesa
[Zink](https://docs.mesa3d.org/drivers/zink.html) translates desktop OpenGL to Vulkan;
its supported GL level depends on Vulkan features, formats and limits. Mesa documents
[Android NDK/Meson builds](https://docs.mesa3d.org/android.html), but this project has
not built, integrated or validated Zink. App-local loading, Android presentation and
third-party licensing still require implementation. No phone driver replacement or
rooting is part of this project. The selected native Vulkan inspection is capability
evidence only, not a complete Zink profile pass or rendering test.

Full assets, remaining native/Java APIs, audio, actual rendering/input and game lifecycle
remain. Main menu, world loading/saving and playability stay unchecked until observed.
