# Microsoft / Xbox / Minecraft account connection

Implemented: system-browser Microsoft authorization code + S256 PKCE → Xbox Live user
token → XSTS → Minecraft Services token → entitlement evidence → Java profile.
The service chain is original code, compared with Amethyst and Microsoft's documentation.
See [the reference review](AMETHYST_AUTH_REVIEW.md). No live account-services calls have run.

Ember's own application is registered and its Mojang API request has been submitted;
approval remains pending. See [registration status](REGISTRATION_STATUS.md).
The locally configured 0.2.2 APK enables generic Microsoft sign-in for testing.
Unconfigured public/CI builds keep live sign-in disabled.
Generic Microsoft sign-in needs an own public-client registration. Xbox/Minecraft calls
also need approval for that ID and confirmed mobile scopes. A local approval reference is
an owner audit record, not permission that code grants itself; servers enforce access.

## Exactly what the owner needs to provide

1. Register **Ember**, a Microsoft Entra native/public-client app supporting personal Microsoft accounts.
   Record **Application (client) ID** from Overview: a GUID, not an RSA public key,
   certificate, tenant ID, password or token. This Android app uses no client secret.
2. For this AppAuth-based app, use the **Mobile and desktop applications** platform
   (the Microsoft-documented option for mobile apps not using MSAL or a broker).
   Configure and confirm the exact custom native redirect:
   `dev.andes.androidjavaport://oauth2redirect`.
   Reassess scheme ownership or verified HTTPS App Links before production.
3. Obtain Minecraft Services authorization for that exact ID. The official
   [Minecraft application information](https://aka.ms/AppRegInfo) article links to the
   [AppID Review form](https://aka.ms/mce-reviewappid). Both were inspected in the browser
   on 2026-10-24. The form requests the matching owner contact email, application name,
   client ID, tenant ID, project website, justification and optional additional details.
   It also requires the owner's EULA/Usage Guidelines attestation. See the prepared
   [application draft](API_APPLICATION_DRAFT.md). Submission is not approval. No ID@Xbox
   approval route is presumed.
4. Confirm native/mobile OAuth scopes, Xbox relying parties, Minecraft endpoints,
   entitlement/profile schema, subscription expiry/rechecks and offline-use rules with
   the approving team. Another launcher's app ID is not this project's registration.
5. Copy `auth.local.properties.example` to ignored `auth.local.properties`, then set:
   - `microsoft.clientId`: your own public-client GUID.
   - `microsoft.registrationReference`: reference to the actual registration record.
   - `minecraft.approvalReference`: reference to approval for the same ID.
   - `minecraft.oauthScopes`: the confirmed space-separated mobile scopes.
6. Provide only the public GUID, redirect confirmation, approval status/reference and
   confirmed scopes. Complete login/consent in Microsoft's browser on the phone. Never
   send passwords, client secrets, authorization codes or access/refresh tokens.

Without Services approval, generic sign-in requests `openid profile offline_access`.
With the complete approval record, it uses the explicit approved Xbox scope list instead.
The implementation accepts `XboxLive.signin` with optional `offline_access` or
`XboxLive.offline_access`; the permitted combination must be confirmed for this public
client. These alternatives are not assumed to be interchangeable.

References: [app registration](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app),
[native redirect configuration](https://learn.microsoft.com/en-us/entra/identity-platform/how-to-add-redirect-uri),
[native PKCE](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow),
[Xbox exchanges](https://learn.microsoft.com/en-us/gaming/gdk/docs/services/fundamentals/s2s-auth-calls/service-authentication/live-website-authentication).
The Xbox example is for websites/confidential clients; its client-secret step is not copied.

## Ownership remains a separate gate

Entitlement evidence and the Java profile are both requested. A profile never skips the
entitlement request. Empty items, signed payloads and subscription entitlements are not
guessed into installation rights. Signed entitlement payloads are not decoded and trusted
without an approved contract/signature policy. Unknown schemas mean unverified, not missing
ownership. There is no demo identity, offline/cracked account, grant issuer or game downloader.

## Credentials, cancellation and errors

AppAuth handles browser PKCE/state/nonce, exchange and refresh. Encrypted pending state is
consumed once, expires after ten minutes and binds exact client/redirect/state. Microsoft
and Minecraft session payloads use AES-256-GCM with Android Keystore keys and private
no-backup files. Hardware backing depends on the device. Serialization is encrypted by
TokenVault before persistence; no plaintext fallback exists.

Account switching clears previous Minecraft credentials. Logout invalidates a session epoch
and removes all three credential/pending files and keys. Late network results cannot restore
a logged-out session. Local logout does not revoke provider grants or sign out the browser.
Cleanup/storage failures require reauthentication or app storage cleanup.

The Services transport uses five fixed HTTPS endpoints, system TLS trust, no redirects,
15-second connect/20-second read timeouts and a 1 MiB response limit. Errors distinguish
network, expired credentials, account restrictions, missing profile, denial, throttling,
service outage and changed contracts. Bodies/tokens are never logged or exposed in errors.
AppAuth logging is suppressed with a pinned 0.11.1 internal adapter; re-audit on upgrades.

## Actual tests

Nine mocked service-chain tests pass. The Keystore encryption/tamper/delete test passed
on the S25+ / Android 15. Real browser login, consent/cancellation, refresh/revocation,
live Xbox/Minecraft responses, process death during login and account switching remain
untested. Generic Microsoft phone login can now be tested with the configured APK;
live Xbox/Minecraft tests still require the pending API approval and confirmed scopes.
