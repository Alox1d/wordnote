<p align="center"><img src="assets/poster.png" alt="WordNote — a place for ideas" width="100%"></p>

# WordNote

An Android notebook by Alexander Baranov with rich text, reminders, widgets and Huawei integrations. My graduation project at ИГЭУ им. Ленина and the app with which I won the **APP student category at [Huawei Cup 2020](https://www.huawei.ru/news/obyavleny-luchshie-razrabotchiki-mobilnykh-prilozheniy-v-ramkakh-sorevnovaniya-huawei-cup-2020/)**. Now shared as source for exploring its architecture and features.

## Inside

- Rich text notes built on laser-native-editor.
- Local storage with SQLiteOpenHelper.
- Reminders with Russian/English natural-language date parsing.
- Voice recognition and OCR through Huawei ML Kit.
- Huawei account and Drive integration, push notifications, biometric access and home-screen widgets.

## Project structure

`app` contains the Kotlin/Java application. `app/libs/laser-native-editor` contains the adapted editor library, with its original attribution. `dateparser-release` contains the historical DateParser AAR; the maintained parser source is at [Alox1d/dateparser](https://github.com/Alox1d/dateparser). The bundled AAR predates that repository's current fixes.

## Build and test

Use **JDK 21**, Android SDK **36** and Build Tools **36.0.0**. The checked-in wrapper uses Gradle **9.4.1** with its distribution SHA-256 pinned; Android Gradle Plugin is **9.2.1**, with built-in Kotlin **2.2.10**. Android Studio's bundled JDK 21 works. Set `sdk.dir` in an ignored local `local.properties`, then run:

```sh
./gradlew :app:assembleDebug testDebugUnitTest lintDebug
./gradlew :app:assembleRelease
```

The minimum Android version remains **4.4 (API 19)**. AndroidX versions are selected for that minimum; upgrading every runtime dependency to its newest release would require raising it. The existing Views, SQLite schema, editor and Huawei integrations remain in place. The backup screen uses View Binding instead of removed Kotlin Android Extensions.

Debug builds use the standard Android debug key. Release builds are unsigned unless you configure your own signing locally. No personal signing key or password is provided.

### Huawei configuration

For cloud features, register your own Huawei developer project and supply `app/agconnect-services.json` locally. The AG Connect plugin is applied only when this ignored file exists. Account, Drive, voice, OCR and other HMS features need the corresponding Huawei configuration and supported environment. No shared developer credentials are included.

AGConnect 1.9 still needs the legacy AGP variant API, so `android.newDsl=false` remains enabled while built-in Kotlin stays enabled. The OCR model AARs share a vendor namespace and currently need `android.uniquePackageNames=false`. These compatibility options are documented by [AGP](https://developer.android.com/build/releases/agp-9-0-0-release-notes); the runtime Huawei SDKs have not all been upgraded.

### Verification status

On 2026-10-14, clean debug and unsigned release builds (including R8), all 8 Robolectric tests, and Android Lint passed on JDK 21. Core note creation, editing, completion and persistence after process restart were checked on an isolated Android 15 (API 35) emulator. Lint and legacy Huawei SDK warnings remain; a passing build does not establish cloud compatibility. Tests cover SQLite note round trips, opening the main/backup screens, system/keyboard insets, notification permission requests, and list interactions after deleting an earlier note. JVM tests simulate an unavailable remote HMS service; they do not validate Huawei cloud behavior.

This remains a project under review, rather than a store-ready release. API 19 device behavior, a real Huawei account/cloud configuration, OCR/voice and backups need separate end-to-end validation. Hidden-note access and backup/restore handling also need further hardening before relying on them for sensitive or irreplaceable notes.

## Public source changes

Removed token logging, private signing configuration and obsolete publishing scripts. Font mappings now use the bundled Apache-2.0 Roboto Condensed files. The public source starts from a prepared snapshot; the original development history is preserved privately.

## License

Original application code: Apache-2.0, see [LICENSE](LICENSE). The editor, fonts, MIT-licensed DateParser port and external SDKs retain their respective licenses and attribution; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
