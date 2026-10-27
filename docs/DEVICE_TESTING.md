# Physical-device matrix — 2026-10-24

| Device | Platform / GPU | Actual completed checks | Real Minecraft |
|---|---|---|---|
| S25+ / SM-S936U | Android 15 API 35, arm64-v8a / Adreno 830 | App install/start; 2 DEX/reflection/JNI tests; 1 Keystore tamper/delete test; 2 LWJGL/EGL pixel/thread/handle tests; visible readback RGB 64,128,191 | Not run |
| MediaTek / Mali | TBD | Not tested | Not tested |
| Exynos / Mali | TBD | Not tested | Not tested |

Device serial identifiers are omitted from public evidence. GPU was reported by the
actual visible EGL context. No FPS, thermal, controller, world-save or game compatibility
claim follows from these component tests.

Initial offscreen swap-interval failure was fixed by separating pbuffer flush behavior
from display presentation; subsequent device tests and the visible readback probe passed.

Generic Microsoft connected state was observed on this phone on 2026-10-26. See
evidence/microsoft-login-s25.json. This does not verify an Xbox/Minecraft session or ownership.

The Xbox/XSTS/Minecraft profile connection also succeeded on 2026-10-26. See
evidence/minecraft-login-s25.json. Entitlement evidence was retrieved but is not yet
interpreted as a validated license/subscription grant.

Pending: consent cancellation, refresh/logout/revocation;
lifecycle/context loss; LWJGL input callbacks; actual client/main menu; playable
world and save/reload. Back up worlds before future imports/upgrades; credential tests and
failed launches must never delete worlds. Device access and the own Microsoft registration
are now available, and the owner reports Services approval. Validated ownership, owned-client
inventory and remaining runtime/graphics engineering still block play.

## Preserve the live account

For the already signed-in app, build `:app:assembleDebug` and
`:app:assembleDebugAndroidTest`, install with `adb install --user 0 -r` (test APK also `-t`),
and run `am instrument --user 0` manually. Do not run Gradle connected app tests: their
cleanup can uninstall the target and erase credentials. Do not uninstall, clear storage,
or replace signing identity during these tests. Samsung's extra profile requires user 0.

Opt-in test arguments: `ownershipAndDownload=true` checks the production ownership and
original client download; `actualClientProbe=true` runs only when privately prepared,
owned-client DEX is already present. Neither test bundles game bytes. The latter records
a failure boundary; it is not a passing gameplay test. Keep translated content private.
