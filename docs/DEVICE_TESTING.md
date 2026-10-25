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

Pending: live Microsoft browser/account flow with this project's own registration and API
approval; lifecycle/context loss; LWJGL input callbacks; actual client/main menu; playable
world and save/reload. Back up worlds before future imports/upgrades; credential tests and
failed launches must never delete worlds. Device access is now available, while registration,
approval, owned-client inventory and remaining runtime/graphics engineering still block play.
