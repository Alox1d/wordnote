<p align="center"><img src="assets/poster.png" alt="WordNote - a place for ideas" width="100%"></p>

# WordNote

An Android notebook by Alexander Baranov with rich text, reminders, widgets and Huawei integrations. My graduation project at Ivanovo State Power Engineering University and the app with which I won the **APP student category at [Huawei Cup 2020](https://www.huawei.ru/news/obyavleny-luchshie-razrabotchiki-mobilnykh-prilozheniy-v-ramkakh-sorevnovaniya-huawei-cup-2020/)**. Now shared as source for exploring its architecture and features.

## Inside

- Rich text notes built on laser-native-editor.
- Local storage with SQLiteOpenHelper.
- Reminders with Russian/English natural-language date parsing.
- Voice recognition and OCR through Huawei ML Kit.
- Huawei account and Drive integration, push notifications, biometric access and home-screen widgets.

## Project structure

`app` contains the Kotlin/Java application. `app/libs/laser-native-editor` contains the adapted editor library, with its original attribution. `dateparser-release` contains the historical DateParser AAR; the maintained parser source is at [DateParser Kotlin](https://github.com/Alox1d/dateparser-kotlin). The bundled AAR predates that repository's current fixes.

## Build and test

Use **JDK 21**, Android SDK **36** and Build Tools **36.0.0**. The checked-in wrapper uses Gradle **9.4.1** with its distribution SHA-256 pinned; Android Gradle Plugin is **9.2.1**, with built-in Kotlin **2.2.10**. Android Studio's bundled JDK 21 works. Set `sdk.dir` in an ignored local `local.properties`, then run:

```sh
./gradlew \
  :app:assembleDebug testDebugUnitTest lintDebug
./gradlew :app:assembleRelease
```

The minimum Android version remains **4.4 (API 19)**. AndroidX versions are selected for that minimum; upgrading every runtime dependency to its newest release would require raising it. The existing Views, SQLite schema, editor and Huawei integrations remain in place. The backup screen uses View Binding instead of removed Kotlin Android Extensions.

Debug builds use the standard Android debug key. Release builds are unsigned unless you configure your own signing locally. No personal signing key or password is provided.

### Huawei configuration

For cloud features, register your own Huawei developer project and supply `app/agconnect-services.json` locally. The AG Connect plugin is applied only when this ignored file exists. Account, Drive, voice, OCR and other HMS features need the corresponding Huawei configuration and supported environment. No shared developer credentials are included.

AGConnect 1.9 still needs the legacy AGP variant API, so `android.newDsl=false` remains enabled while built-in Kotlin stays enabled. The OCR model AARs share a vendor namespace and currently need `android.uniquePackageNames=false`. These compatibility options are documented by [AGP](https://developer.android.com/build/releases/agp-9-0-0-release-notes); the runtime Huawei SDKs have not all been upgraded.

### Backup behavior

Manual and scheduled Huawei backups use a versioned ZIP snapshot containing validated SQLite notes and their private attachments. Restore prefers this archive and reads the historical database-only backup only when no ZIP exists. Failed queries/transfers are reported without replacing current notes. A corrupt ZIP never falls back to another backup. Huawei sign-in remains in memory; it is not copied into preferences.

Automatic backups support Off, Daily, Weekly and Monthly (30 days). Android schedules persisted network jobs on API 21+; API 19/20 use an inexact alarm and a foreground service. Execution time depends on network availability and system power restrictions. Background work uses silent sign-in and never opens a login screen; sign in manually again if the Huawei account becomes unavailable.

System Android auto-backup is disabled. Android 12+ exclusion rules also block every app-data domain in automatic cloud backup and device transfer; no include/export rules are configured. The explicit Huawei ZIP backup remains available. Real Huawei account, cloud transfer and API 19/20 behavior still require device validation.

### Hidden notes and attachments

Both tabs and search require successful device authentication before showing private notes. Leaving the activity locks the session; private editor windows are hidden until authentication succeeds again, while their unsaved draft survives activity recreation. Sensitive windows block screenshots. Reminder notifications use generic text for private notes.

New note images are stored under the app-private attachments directory and are included in the portable backup. Older releases created copies in the public gallery; those existing copies are left intact. A legacy database-only backup can restore images only if all referenced legacy images remain accessible locally. Missing images, unsupported editor data, corrupt checksums and oversized backups are rejected before replacing notes. Hidden notes use app access controls; the SQLite database and Huawei archive do not have a separate password-encryption layer.

### Verification status

On 2026-10-15, debug and unsigned release builds (including R8), **132 JVM tests**, and Android Lint completed successfully on JDK 21. Lint has no errors; existing warnings and vendor Huawei/R8 warnings remain. Tests cover both tabs/search/authentication failure and cancellation, private editor lifecycle and restored saves, recycled row privacy, portable image restoration into an empty installation, malformed data/paths, transaction rollback and cancelled downloads, cloud failure handling, and scheduling without postponing the next run.

An isolated **Android 16 (API 36), ARM64, 16 KB** emulator exercised rich-text creation, IME focus and Back, search, completion/restoration, process-restart persistence, private-draft masking after Home, cancelled backup sign-in, and tablet rotation with an unsaved draft. The OCR libraries are pinned to 3.18.1.302; all packaged 64-bit libraries passed LOAD/RELRO alignment checks. Camera capture uses a narrowly scoped private FileProvider, and picked images are read through content URIs with EXIF preserved. The external camera also returned to its original private output after the WordNote process was reclaimed. Tests cover pending camera/picker restoration and repeated camera release.

The follow-up regressions cover private link/date/time windows during lock and background transitions, invalid list children rejected before database or ZIP restoration, combined toolbar formatting, literal multiline clipboard text and spans, and OCR recovery after failures and stop. OCR document/result activities block screen capture by default. Six instrumented tests passed on the API 36 / 16 KB emulator; the same tests fail on the preceding build.

To run the device regressions on a separate API 36 emulator, build `:app:assembleDebugAndroidTest`, install the debug app and its test APK, then run `ru.alox1d.wordnote.huawei.RegressionDeviceTest` with `androidx.test.runner.AndroidJUnitRunner`. The tests use unsaved synthetic drafts and temporary backup candidates. They do not replace the installed notes database.

JVM tests replace external HMS authentication/Drive services. Real Huawei account/cloud transfer, biometric/PIN prompts, and API 19/20 device behavior still require validation. Existing Huawei/R8 warnings remain. This source update is not a published store release.

## Public source changes

Removed token logging, private signing configuration, personal contact/ad/VK identifiers and obsolete publishing scripts. The public build leaves the ad unit unset and does not request banners. Font mappings now use the bundled Apache-2.0 Roboto Condensed files. The public source starts from a prepared snapshot; the original development history is preserved privately.

## License

Original application code: Apache-2.0, see [LICENSE](LICENSE). The editor, fonts, MIT-licensed DateParser port and external SDKs retain their respective licenses and attribution; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
