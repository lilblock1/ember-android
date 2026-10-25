# Architecture and native/non-native boundaries

| Module | Responsibility | Execution |
|---|---|---|
| app | Compose interface, Android lifecycle, local diagnostics/settings | Android ART; may AOT/JIT compile managed bytecode |
| authentication | Browser PKCE, staged Xbox/XSTS/Minecraft, encrypted sessions, refresh/logout | Android ART plus platform Keystore |
| core | Access policy, callback binding, file validation | Kotlin bytecode under ART; JVM for host tests |
| native-engine | Input queue, JNI entry points, GLES surface probe | C++ compiled to ARM64 ELF shared library |
| tools | Public metadata inventory and local classfile inventory | Host Python/Java tools; not part of APK |
| compatibility-lab | Isolated original fixture / dynamic DEX / reflection / registered JNI experiment | ART plus a small ARM64 bridge, separate test APK; both tests passed on S25+ |

The production app has no route to game installation. The core `InstallGrant` constructor
is internal, no production code issues grants, and there is no game download or launch
implementation. Kotlin visibility is an architectural guard, not a defense against a
modified malicious APK. A future approved adapter must mint session-bound, expiring grants
only after both current entitlement and profile validation. Network failures, expired
subscriptions, restrictions and missing ownership must remain distinct outcomes.

The selected research release is 1.20.4 (Java 17). This limits initial moving parts; it is
not an advertised supported release. Other versions need their own dependency and API audits.

## Execution decision is still open

1. AOT Android ARM64 image: investigate a supported toolchain, Java runtime services,
   reflection/resources/JNI metadata, desktop replacement APIs and lawful local generation.
2. ART/DEX translation: test conversion of owned files only after authorization, preserving
   originals. Inventory unavailable Java SE APIs, unsupported bytecode features and JNI ABI.
   ART remains a managed runtime; DEX conversion alone does not supply desktop libraries.
3. An Android OpenJDK runtime can be a comparison experiment only if the owner accepts
   it. It is JVM execution and conflicts with the strict no-VM goal; it cannot be silently
   presented as a completed native port.

Do not implement optional mods, shader managers or cosmetic launcher screens before an
actual selected client reaches the main menu and a world on a physical ARM64 device.

## Probe lifecycle

Each surface owns a native state handle. GL calls are confined to the GLSurfaceView thread.
Touch events use a mutex-protected bounded queue. Pause drains input; context recreation
resets probe visuals. Rendering is on demand, so there is no continuous idle draw loop.
There is no game loop, audio path, Vulkan backend or desktop OpenGL translator. The separate
LWJGL subset now adapts selected context and GLES calls; see LWJGL_COMPATIBILITY.md.

## Future installation transaction

Approved entitlement/profile adapter → grant → trusted manifest → verified version metadata →
architecture-specific dependency plan → app-private staging → streamed bounded downloads →
hash/size verification → atomic installation metadata commit. Preserve previous installation
on failure. Keep saves outside staging and repair scopes. Reject redirect host changes,
archive traversal, symlink escapes, oversized entries and incomplete files before loading.
`VerifiedFiles` provides path/hash primitives, not this complete transaction.
