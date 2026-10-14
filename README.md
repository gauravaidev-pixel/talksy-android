# Talksy Android

Native Android client (Kotlin + Jetpack Compose) for the Talksy backend.
Real-time chat, groups, polls, status and bots — with Google Sign-In.

## What's inside
- **Kotlin + Jetpack Compose**, Material 3, light/dark theme, edge-to-edge
- **Vector (SVG) icons** throughout — no bitmap icons, no emojis in the UI
- **Google Sign-In** via Credential Manager (no passwords, no backup login)
- **Retrofit + kotlinx.serialization** for REST, **socket.io-client** for realtime
- **DataStore** for the JWT session, OkHttp auth interceptor
- Clean layers: `data/` (remote, local, socket, repo) → `ui/` (theme, nav, screens)

## 1. Configure
Open `gradle.properties` and set:
```
TALKSY_BASE_URL=https://<your-render-service>.onrender.com
TALKSY_GOOGLE_WEB_CLIENT_ID=<your web oauth client id>.apps.googleusercontent.com
```
The Google **Web** client id must be the same value the backend uses as
`GOOGLE_CLIENT_ID`.

### Google Cloud Console
1. Create an OAuth **Web** client → copy its client id.
2. Create an OAuth **Android** client → package name `com.talksy.app`
   + your signing SHA-1 (`./gradlew signingReport`, or add the CI debug SHA-1).
3. Put the Web client id in `gradle.properties` **and** in the backend env.

## 2. Build
Locally (needs Android SDK + JDK 17):
```bash
gradle wrapper        # one-time: generates the wrapper
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

## 3. Build on GitHub (no local setup)
Push this folder to a repo. The workflow `.github/workflows/android-build.yml`
builds a debug APK on every push. Download it from the run's **Artifacts**
(`talksy-debug-apk`). Optionally set a repo **variable** `TALKSY_BASE_URL` and a
**secret** `TALKSY_GOOGLE_WEB_CLIENT_ID` so CI injects them automatically.

## Screens
Login · Chats list · New chat (people search) · Chat (realtime) · New group ·
Polls (create + vote) · Status · Bots · Profile · Settings

## Fixed signing key (important for Google Sign-In)

A debug keystore is committed at `app/debug.keystore.p12`. Debug builds are
signed with it, so the signing SHA-1 is **stable across every build**. Without
this, GitHub Actions would generate a new random debug key on each run and
Google Sign-In would break on the next build.

Register this SHA-1 in Google Cloud Console
(**Credentials -> Create OAuth client ID -> Android**, package name `com.talksy.app`):

```
01:7A:CA:6C:D8:74:F7:24:10:20:3B:7F:7C:BF:34:F1:2E:C5:7F:A0
```

- This is a **debug** key only (password `android`) — never use it for release.
- The build log also prints it (the `signingReport` step).
- The key lives at `app/debug.keystore.p12`; keep it in the repo so the SHA-1
  does not change.
