# VrVideo Browser

Browser Android khusus untuk memutar dan mengunduh video VR 360°/180° secara langsung dari web menggunakan ExoPlayer + yt-dlp.

## Fitur Utama

- **In-Page VR Video Sniffer**: Mendeteksi video standard / 360° / 180° langsung di WebView via JS Injection.
- **ExoPlayer Spherical Playback**: Pemutar video bawaan berbasis `androidx.media3` dengan dukungan spherical projection (360° monoscopic/stereoscopic) & sensor giroskop.
- **Fast Segmented Downloader**: Menggunakan `yt-dlp-android` + `ffmpeg-kit` untuk unduhan paralel dan konversi resolusi tinggi.
- **Multi-Search Engine**: Google, Bing, DuckDuckGo, Yahoo, Ecosia, Qwant dengan URL query template yang bisa diatur lewat Settings.
- **Encrypted Vault Storage**: Penyimpanan lokal terenkripsi untuk media privat.

## Persyaratan Build

- **JDK**: 21 (direkomendasikan via Android Studio JBR)
- **Android SDK**: `compileSdk 36`, `targetSdk 36`, `minSdk 24`
- **Gradle**: 8.10.2 (sudah termasuk wrapper `./gradlew`)

## Cara Build

1. Pastikan `local.properties` mengarah ke Android SDK Anda:
   ```properties
   sdk.dir=/Users/<username>/Library/Android/sdk
   ```

2. Jalankan build debug:
   ```bash
   ./gradlew assembleDebug
   ```

3. Output APK akan berada di:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

## Struktur Modul

```
app/src/main/
├── AndroidManifest.xml
├── java/com/elbruz/vrvideo/
│   ├── VrBrowserApp.kt
│   ├── model/SearchEngine.kt
│   ├── data/local/UserPrefs.kt
│   ├── util/
│   │   ├── VaultPrefs.kt
│   │   └── downloaders/FastDownloadService.kt
│   └── ui/main/
│       ├── MainActivity.kt
│       ├── MainViewModel.kt
│       ├── MainViewModelFactory.kt
│       ├── VrJsInterface.kt
│       ├── download/DownloadPromptDialog.kt
│       ├── player/VideoPlayerActivity.kt
│       └── settings/
│           ├── SettingsActivity.kt
│           └── SearchEngineAdapter.kt
└── res/
    ├── layout/
    ├── drawable/
    └── values/
```

## Lisensi

Private repository.
