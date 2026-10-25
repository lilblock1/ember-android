# Dependencies and distribution review

| Component | Purpose | License / source |
|---|---|---|
| Kotlin | Managed Android/host code | Apache-2.0, https://github.com/JetBrains/kotlin |
| AndroidX / Compose | Android UI and lifecycle | Apache-2.0, https://android.googlesource.com/platform/frameworks/support/ |
| AppAuth Android 0.11.1 | OAuth/PKCE client | Apache-2.0, https://github.com/openid/AppAuth-Android |
| kotlinx.coroutines | Reactive account state | Apache-2.0, https://github.com/Kotlin/kotlinx.coroutines |
| JUnit 4 | Host/instrumentation assertions | EPL-1.0, https://github.com/junit-team/junit4 |
| Gradle wrapper | Build bootstrap | Apache-2.0, https://github.com/gradle/gradle |

This is an initial inventory, not a complete release notice bundle. Review resolved
transitive dependencies and include required license texts/notices before public release.
The project intentionally does not set a blanket outbound source license on behalf of its
owner; choose one before publishing. No Minecraft binaries/assets are redistributed.

No OpenJDK, GraalVM, Mesa, upstream LWJGL binaries, PojavLauncher or mod loader is bundled
in the APK. LWJGL 3.3.2 jars are BSD-3-Clause build/test references; the Android subset is
an original implementation. No Amethyst code was copied or linked. See AMETHYST_AUTH_REVIEW.md.
Any future inclusion requires version-specific license and source/notice obligations review.
Build toolchains under the task's work directory are not project deliverables.
