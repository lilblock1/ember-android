# Ember registration and API review status

Registered/submitted 2026-10-24; status updated 2026-10-26.

- Microsoft display name: Ember.
- Application (client) ID: `6a1a3aa5-6dc6-48de-836d-af462ed06b2f`.
- Directory (tenant) ID: `d06c0e58-e193-4fb5-a823-a37cb6c133e9`.
- Audience: personal Microsoft accounts only.
- Platform: Mobile and desktop applications (native public client, AppAuth).
- Saved redirect: `dev.andes.androidjavaport://oauth2redirect`.
- No client secret was created.
- Official Mojang AppID Review request: submitted for this client ID. The form displayed
  “Thank you for contacting Mojang Studios.” No reference number was displayed.
- Minecraft Services approval: **reported approved by the project owner in this chat on
  2026-10-26** for Ember. No written approval reference has been supplied. The submission
  receipt itself is not evidence of approval.
- Minimal Xbox scope selected from Microsoft's published protocol: `XboxLive.signin`.
  No offline Xbox scope is requested for this connection test. The approval reply's
  scope details and current entitlement/profile contract have not been supplied.

The locally configured 0.2.3 APK enables the account service chain using the owner's
approval attestation as its local audit reference. Microsoft and Minecraft servers still
enforce authorization. The ignored `auth.local.properties` contains the actual registration
and attestation; it contains no user credentials. Public/CI builds remain
unconfigured unless their developer supplies the appropriate local registration record.
See AUTHENTICATION.md for configuration. Registration and a successful build do not
prove ownership verification or game execution. Generic Microsoft sign-in was observed
on the S25+ on 2026-10-26. Existing Microsoft-only sessions must reauthorize with the
Xbox scope before any Xbox token exchange is attempted.

The approving team's written reference and any native-scope restrictions remain requested
for the audit record. A 403/denial must be investigated, never bypassed. Do not submit duplicate
requests merely to accelerate review. Never store approval correspondence, personal
contact data, authentication codes or tokens in the public repository.

Sources: [Xbox scope and exchange protocol](https://learn.microsoft.com/en-us/gaming/gdk/docs/services/fundamentals/s2s-auth-calls/service-authentication/live-website-authentication),
[native public-client PKCE](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow).
The website sample's client-secret step is not used by this native app. No claim is made
that Mojang's reply independently confirmed these protocol details.
