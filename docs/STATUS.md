# Delivery status

| Phase | State | Evidence / remaining work |
|---|---|---|
| 1 Feasibility | Partial | Original DEX/JNI and LWJGL/EGL subset tests pass on S25+; real client and AOT remain unresolved |
| 2 Foundation | Partial physical validation | App installed/started and visible EGL readback passed on S25+; full lifecycle and inputs remain to test |
| 3 Authentication | Partial | Browser PKCE and staged Xbox/XSTS/Minecraft chain; nine mock tests and physical Keystore test pass; own registration/approval and live login pending |
| 4 Ownership | Blocked | Current approved Services contract and project approval missing; no grant issuer |
| 5 Installer | Partial primitives only | Path/integrity policy tests; no proprietary downloads, resolver or installation transaction |
| 6 Execution | Unimplemented | No production Java execution strategy, LWJGL bridge or GL translator |
| 7 Playability | Unimplemented | No real client, worlds, gameplay, audio or saving |
| 8 Optimization | Unimplemented | Device information only; no game performance claims |
| 9 Advanced compatibility | Unimplemented | Multiplayer, world I/O, packs not implemented |
| 10 Mods/shaders | Deferred | Base playable milestone first |
| 11 Reliability | Partial | Credential handling and policy primitives; game recovery/backups pending |
| 12 Release | Research foundation only | Debug artifact if build succeeds; no playable release |

## First playable — none verified

- [ ] Microsoft sign-in works on a physical Android phone.
- [ ] Java Edition ownership is correctly verified.
- [ ] Official Minecraft game files download and pass integrity checks.
- [ ] A selected client reaches its real main menu.
- [ ] A real single-player world loads, runs and saves correctly.
- [ ] Touchscreen input and rendering work with that client on physical ARM64 Android.

No FPS, thermal, multiplayer, mod, shader or device compatibility claims are made.
