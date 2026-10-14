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

## Building the historical app

The project retains Gradle 6.4.1, Android Gradle Plugin 4.0.0, Kotlin 1.6.10 and Android SDK 32. Use a compatible older JDK (for example JDK 11), configure `sdk.dir` in a local `local.properties`, and run:

```sh
./gradlew \
  :app:assembleDebug
```

This is legacy tooling and dependency availability can require migration. A fresh APK build has not been verified for this public release; do not treat this repository as a current Play Store release.

Debug builds use the standard Android debug key. Release builds are unsigned unless you add your own signing setup locally. No personal signing key or password is provided.

For cloud features, register your own Huawei developer project and supply `app/agconnect-services.json` locally. The AG Connect plugin is applied only when this file exists. Account, Drive, voice, OCR and other HMS features need the corresponding Huawei configuration and supported environment. No shared developer credentials are included.

## Public source changes

Removed token logging, private signing configuration and obsolete publishing scripts. Font mappings now use the bundled Apache-2.0 Roboto Condensed files. The public source starts from a prepared snapshot; the original development history is preserved privately.

## License

Original application code: Apache-2.0, see [LICENSE](LICENSE). The editor, fonts, MIT-licensed DateParser port and external SDKs retain their respective licenses and attribution; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
