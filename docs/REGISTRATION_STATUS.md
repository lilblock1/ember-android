# Ember registration and API review status

Recorded 2026-10-24.

- Microsoft display name: Ember.
- Application (client) ID: `6a1a3aa5-6dc6-48de-836d-af462ed06b2f`.
- Directory (tenant) ID: `d06c0e58-e193-4fb5-a823-a37cb6c133e9`.
- Audience: personal Microsoft accounts only.
- Platform: Mobile and desktop applications (native public client, AppAuth).
- Saved redirect: `dev.andes.androidjavaport://oauth2redirect`.
- No client secret was created.
- Official Mojang AppID Review request: submitted for this client ID. The form displayed
  “Thank you for contacting Mojang Studios.” No reference number was displayed.
- Minecraft Services approval: **pending**, not granted by the submission receipt.
- Approved Xbox scopes and current entitlement/profile contract: not yet confirmed.

The locally configured 0.2.2 APK enables only generic Microsoft browser PKCE with
`openid profile offline_access`. The ignored `auth.local.properties` records the actual
registration but leaves Minecraft approval and scopes blank. Public/CI builds remain
unconfigured unless their developer supplies the appropriate local registration record.
See AUTHENTICATION.md for configuration. Registration and a successful build do not
prove real account login, ownership verification or game execution.

The owner must supply the approving team's actual approval reference and confirmed
native scopes before the Xbox/Minecraft adapter can be enabled. Do not submit duplicate
requests merely to accelerate review. Never store approval correspondence, personal
contact data, authentication codes or tokens in the public repository.
