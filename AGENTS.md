# Recite Android project constraints

- This repository is a native Android application. Do not replace screens with a WebView shell.
- The packaged 78-piece corpus and local progress database must remain usable without a network connection.
- Room is the UI source of truth. Network writes go through the idempotent outbox and WorkManager.
- Never persist a Seiue or BDFZ password. Persist only the User Center session encrypted with Android Keystore.
- Keep `direct` and `play` product flavors separate for permissions and update transport, but both must use the single canonical application id `net.bdfz.recite.direct` and the same app-signing identity. The same product must never be installed as separate Direct and Play apps.
- Only `direct` may request package-install permission or use R2 self-update. Before launching Android's installer, validate the versioned manifest, immutable URL, size, SHA-256, archive package, archive version and installed-app signer.
- A release is not complete until APK/AAB build, hash, public R2 readback, GitHub release, both registered OnePlus phones, physical-tablet layout/upgrade, same-package upgrade persistence, and authenticated sync have been verified. One phone or an emulator cannot substitute for either physical-device gate.
- Never commit signing files, credentials, invite codes, cookies, tokens, or learner content.
