# Delivery status

| Phase | State | Evidence / remaining work |
|---|---|---|
| 1 Feasibility | Partial | Original DEX/JNI and LWJGL/EGL subset tests pass on S25+; real client and AOT remain unresolved |
| 2 Foundation | Partial physical validation | App installed/started and visible EGL readback passed on S25+; full lifecycle and inputs remain to test |
| 3 Authentication | Partial physical validation | Microsoft and Xbox/XSTS/Minecraft profile connection observed on S25+; owner reports API approval; cancellation, refresh/revocation and process-death cases remain |
| 4 Ownership | Purchased-account test passed | Signed license, fresh nonce, signer/profile and time checks passed on S25+; subscription and negative live account variants remain |
| 5 Installer | Partial real download | Original 1.20.4 client + six initialization libraries downloaded after fresh ownership and integrity checked; full library/asset/runtime resolver absent |
| 6 Execution | Isolated real-client entry probe | Real Main initialized and entry observed under ART/DEX, then missing Guava Ticker; 249/292 directly referenced LWJGL declarations absent; desktop GL translation and production runtime absent |
| 7 Playability | Unimplemented | No real client, worlds, gameplay, audio or saving |
| 8 Optimization | Unimplemented | Device information only; no game performance claims |
| 9 Advanced compatibility | Unimplemented | Multiplayer, world I/O, packs not implemented |
| 10 Mods/shaders | Deferred | Base playable milestone first |
| 11 Reliability | Partial | Credential handling and policy primitives; game recovery/backups pending |
| 12 Release | Research foundation only | Debug artifact if build succeeds; no playable release |

## First playable — partial

- [x] Generic Microsoft sign-in works on a physical Android phone (S25+, observed 2026-10-26; not Xbox/Minecraft ownership).
- [x] Purchased Java Edition ownership verified on S25+ for the observed contract; subscription variants remain unsupported.
- [ ] Complete required game installation downloads and passes integrity checks (client and six initialization libraries passed; remaining dependencies/assets absent).
- [ ] A selected client reaches its real main menu.
- [ ] A real single-player world loads, runs and saves correctly.
- [ ] Touchscreen input and rendering work with that client on physical ARM64 Android.

No FPS, thermal, multiplayer, mod, shader or device compatibility claims are made.
