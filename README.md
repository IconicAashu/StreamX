# STREAMX 🎬

> **The next-generation cinematic streaming & discovery platform for Android.**  
> Crafted with an **iOS-inspired White Liquid Glass design system**, universal Media3 engine, extensible Stremio add-on ecosystem, and offline-first private user profiles.

[![Android CI](https://github.com/IconicAashu/StreamX/actions/workflows/android.yml/badge.svg)](https://github.com/IconicAashu/StreamX/actions/workflows/android.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.04.01-4285F4.svg?logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/AndroidX%20Media3-1.3.1-00C853.svg)](https://developer.android.com/media/media3)
[![MinSdk](https://img.shields.io/badge/minSdk-26-orange.svg)](https://android.com)
[![TargetSdk](https://img.shields.io/badge/targetSdk-34-brightgreen.svg)](https://android.com)

---

## ✨ Key Highlights

- 🪟 **Liquid Glass Design System**: Frosted glass top headers, floating glass navigation dock, translucent modal bottom sheets, fluid backdrop blurs, and vibrant violet/indigo accent gradients.
- 📺 **Universal Media Engine (Media3)**: Adaptive HLS bitrate switching (Auto, 1080p, 720p, 480p), multi-audio track selection (Hindi, English, etc.), embedded and external OpenSubtitles switching with custom offset/encoding, and landscape auto-rotation.
- 🔌 **Extensible Add-ons (Stremio Protocol)**: Full Stremio v3 manifest and catalog compatibility. Community manifests, custom HTTP headers, proxy support, and catalog filtering.
- 👤 **Offline Private Profile System**: Zero cloud lock-in. Create local profiles with custom display names, avatar presets, and personalized watchlists stored locally using Jetpack DataStore.
- 🚀 **Integrated Over-The-Air (OTA) Updates**: In-app GitHub release detection, download progress streaming, SHA-256 cryptographic checksum verification, and seamless `FileProvider` package installer handoff.
- ⚡ **Native C++ Streaming Core**: Integrated C++20 / JNI engine for high-performance peer-to-peer data buffering.

---

## 📱 Architecture & Tech Stack

STREAMX is built adhering to Google's official **Modern Android Architecture** recommendations, featuring Clean Architecture and unidirectional data flow (UDF):

```
┌────────────────────────────────────────────────────────┐
│               Presentation Layer (UI)                  │
│   Jetpack Compose · Material 3 · Navigation Component  │
│          Liquid Glass Design System Components         │
└───────────────────────────▲────────────────────────────┘
                            │ StateFlow / Events
┌───────────────────────────┴────────────────────────────┐
│                    ViewModel Layer                     │
│   Kotlin Coroutines · StateFlow · Lifecycle ViewModels │
└───────────────────────────▲────────────────────────────┘
                            │ Domain Models / Use Cases
┌───────────────────────────┴────────────────────────────┐
│                      Domain Layer                      │
│      Repository Interfaces · Pure Business Models      │
└───────────────────────────▲────────────────────────────┘
                            │ Implementation
┌───────────────────────────┴────────────────────────────┐
│                       Data Layer                       │
│  DataStore (Prefs) · OkHttp · Retrofit · Media3 Exo   │
│       Native C++ Core · WorkManager · FileProvider     │
└────────────────────────────────────────────────────────┘
```

### Core Technologies
- **Language**: Kotlin 2.0.0 & C++20 (CMake 3.22.1)
- **UI Framework**: Jetpack Compose (BOM 2024.04.01) with Material Design 3
- **Media Player**: AndroidX Media3 ExoPlayer 1.3.1 (HLS, DASH, Session)
- **Asynchrony**: Kotlin Coroutines & Flow
- **Network**: Retrofit 2.11.0, OkHttp 4.12.0
- **Image Loading**: Coil Compose 2.6.0
- **Background Tasks**: AndroidX WorkManager 2.9.1
- **Local Persistence**: AndroidX DataStore Preferences 1.1.1
- **Testing**: JUnit 4, Kotlinx Coroutines Test, AndroidX Test Runner

---

## 🛠️ Getting Started & Building

### Prerequisites
1. **Android Studio**: Jellyfish (2023.3.1) or Koala (2024.1.1)+
2. **JDK**: Java Development Kit 17 (recommended: Eclipse Temurin or Azul Zulu 17)
3. **Android SDK**:
   - Compile SDK: `34`
   - Target SDK: `34`
   - Minimum SDK: `26` (Android 8.0 Oreo)
   - NDK: 25+ (for native C++ streaming modules)

### Clone the Repository
```bash
git clone https://github.com/IconicAashu/StreamX.git
cd StreamX
```

### Build Commands

- **Build Debug APK**:
  ```bash
  # Windows PowerShell
  .\gradlew.bat assembleDebug

  # macOS / Linux
  ./gradlew assembleDebug
  ```
  The built APK will be located at:  
  `app/build/outputs/apk/debug/app-debug.apk`

- **Run Unit Tests**:
  ```bash
  # Windows PowerShell
  .\gradlew.bat testDebugUnitTest

  # macOS / Linux
  ./gradlew testDebugUnitTest
  ```

---

## 🔄 Over-The-Air (OTA) Updates

STREAMX includes a built-in update engine that checks for new releases directly from GitHub Releases:
- **Repository Endpoint**: `https://api.github.com/repos/IconicAashu/StreamX/releases/latest`
- **Integrity Verification**: Automatic SHA-256 calculation of downloaded APKs before opening the Android Package Installer.
- **Safety Checks**: Validates package name (`com.streamx.app`) and requires `versionCode` strictly greater than installed version.

For release publishing instructions and manifest details, see:
- [Release Guide](docs/RELEASING.md)
- [OTA Update Architecture](docs/OTA_UPDATES.md)

---

## 👨‍💻 Author & Credits

- **Author**: Aashu Kumar
- **GitHub**: [@IconicAashu](https://github.com/IconicAashu)
- **Feedback & Support Email**: [aashu@hilmail.co](mailto:aashu@hilmail.co)

---

## 📄 Contributing & Guidelines

Contributions and feedback are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for code style, branching model, and pull request guidelines.
