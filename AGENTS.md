# Recite Android project constraints

- This repository is a native Android application. Do not replace screens with a WebView shell.
- The packaged 78-piece corpus and local progress database must remain usable without a network connection.
- Room is the UI source of truth. Network writes go through the idempotent outbox and WorkManager.
- Never persist a Seiue or BDFZ password. Persist only the User Center session encrypted with Android Keystore.
- Keep `direct` and `play` product flavors separate. Only `direct` may request package-install permission or use R2 self-update.
- A release is not complete until APK/AAB build, hash, public R2 readback, GitHub release, phone/tablet layout, upgrade persistence, and authenticated sync have been verified.
- Never commit signing files, credentials, invite codes, cookies, tokens, or learner content.
