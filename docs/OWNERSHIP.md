# Purchased Java ownership verification (experimental contract)

On 2026-10-26 CDT (2026-10-27 UTC), the S25+ passed the production verifier and downloaded the original 1.20.4 client after a fresh online check. See evidence/ownership-client-s25.json. This validates the observed purchased-license contract for this account; it is not a claim that subscription, family restriction, revoked-license or every account variant has been tested.

The owner reports approval for Ember client 6a1a3aa5-6dc6-48de-836d-af462ed06b2f. A written approval reference and provider schema specification have not been supplied. The implementation treats live signed responses as an observed versioned contract and rejects unknown variants; it does not call community documentation an official specification.

## Checks before any proprietary download

- Own approved registration and an unexpired encrypted Minecraft token.
- Fresh HTTPS GET `/entitlements/license?requestId=<random UUID>`, followed by `/minecraft/profile` using the same bearer token. System TLS, exact host, no redirects, 15/20 second timeouts, bounded 1 MiB responses. Service failures stay separate from ownership rejection.
- RS256 compact JWS with pinned key ID 1, expected thumbprint header, no alternate URL/key/critical-header instructions. Claims are interpreted only after signature verification.
- Both outer and signed request IDs match the fresh challenge; the signed signer matches the token's Xbox user. Decoding that token claim is binding data only: the service must accept the same token on both HTTPS calls.
- Signed issuance/not-before/expiry checks; evidence must be issued within 120 seconds. Returned and signed entitlement lists must match, including sources.
- Both `game_minecraft` and `product_minecraft` have signed `PURCHASE` or `MC_PURCHASE` sources. Bedrock-only, incomplete Java and unrecognized subscription evidence cannot grant installation.
- Fresh profile ID matches the connected session and has a valid Java profile name.
- Install grant is memory-only, account/session-bound and valid for at most ten minutes or the earlier service/token expiry. Every proprietary request/read/commit rechecks it. Downloads always obtain a fresh grant. App restart and a newly connected Minecraft session trigger a fresh online ownership check. Logout/account switch invalidates access and late results.

The 36 rule-free Java artifacts (including Guava and failureaccess) are selected with strict coordinate/path validation from the same hash-verified official version metadata. Standalone desktop-native classifiers and upstream LWJGL artifacts are excluded. A universal Java JAR can contain platform resources; the isolated resource loader refuses native-library resources rather than treating bundled desktop binaries as Android-compatible. The original client and each selected library are size/SHA-1 checked, staged privately and atomically committed. Existing valid files are rechecked; failures preserve committed files. SHA-1 is the publisher's legacy metadata digest, not a new signing algorithm. TLS and the trusted metadata chain are also required.

This is not a full installation: other libraries, assets, resource resolution and runtime integration remain missing. No offline grant is persisted. Subscription-only access is unsupported rather than silently called unowned. The UI links to the official purchase/account site, and unknown service responses fail closed.

## Verification-key provenance

The owner's earlier pasted RSA public key did not verify actual service signatures and was discarded. `/publickeys` supplies authentication/profile/certificate keys, not the matching entitlement key. No private key or secret is used.

The pinned public SPKI was read without executing or redistributing the launcher from Mojang's publicly downloadable Linux launcher chain:

1. [Official launcher bootstrap archive](https://launcher.mojang.com/download/Minecraft.tar.gz), SHA-256 `695269281547bbbcf47fe74633027a0e4ddc13a61060c686a7217e85d314e45e`.
2. Its embedded [publisher Linux metadata URL](https://launchermeta.mojang.com/v1/products/launcher/6f083b80d5e6fabbc4236f81d0d8f8a350c665a9/linux.json).
3. `launcher-core-ado` [manifest](https://piston-meta.mojang.com/v1/packages/0f5faa36de53cc2883b501a7a18e402966930f2b/manifest.json), verified SHA-1 `0f5faa36de53cc2883b501a7a18e402966930f2b`.
4. `liblauncher.so`, verified size 21,153,648 and SHA-1 `71347e8806b3dd45038bcca789bcece496e02cf1`, fetched from the manifest's official piston-data URL.
5. Public key DER SHA-256 `e32aa396f0c6e726d523f9cf145e4f6daa9ea93ae38685b781d25e214301822b`. Actual mcstore and challenge-bound license signatures verified against it on the phone.

Only the public verification key is in the source. Launcher executables and game content are excluded from source/APKs/releases. This published bootstrap chain is not represented as the latest launcher version. Key rotation must fail closed and requires independently verified publisher provenance and tests before updating the pin.

## Limits and remaining approval/device needs

No additional app registration is needed for the already successful purchased-license test. Retain the provider's written approval email/reference and any approved scope/contract guidance for release auditing; never share passwords, tokens or secrets. Subscription semantics and offline-use permission still require authoritative guidance and separate account tests. Physical first-playable testing still needs the real client to reach its menu, create/load/save a world, render correctly and accept touch input. None of those game milestones has passed.
