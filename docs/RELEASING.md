# STREAMX — Release & Signing Guide

This guide outlines the complete process for building, signing, and publishing official releases of **STREAMX** to GitHub Releases for distribution and automatic Over-The-Air (OTA) updates.

---

## 1. Versioning Scheme

STREAMX uses **Semantic Versioning** (`MAJOR.MINOR.PATCH`):
- `versionName`: Visible to users (e.g., `"1.0.0"`, `"1.1.0"`).
- `versionCode`: Positive integer incremented on every release (e.g., `1`, `2`, `3`).

Both values are maintained in `app/build.gradle.kts`:
```kotlin
defaultConfig {
    applicationId = "com.streamx.app"
    minSdk = 26
    targetSdk = 34
    versionCode = 1      // Increment for each new release
    versionName = "1.0.0" // User-facing version
}
```

> **IMPORTANT**: The in-app OTA update system compares `versionCode`. An update will only be presented to users if the release's `versionCode` is strictly greater than the currently installed `versionCode`.

---

## 2. Keystore & Signing Configuration

### A. Generating a Release Keystore
To sign production APKs, generate a private keystore (if you do not already have one):

```powershell
# Run in terminal (adjust paths as needed)
keytool -genkey -v -keystore streamx-release.keystore -alias streamx -keyalg RSA -keysize 2048 -validity 10000
```

> **SECURITY WARNING**:
> - Never commit `*.keystore` or `*.jks` files to git. They are ignored by `.gitignore`.
> - Store the keystore and passwords securely.

### B. Configuring Signing in `app/build.gradle.kts`
You can configure release signing either via local environment variables or a private `keystore.properties` file:

```kotlin
// Example app/build.gradle.kts signing config:
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("KEYSTORE_PATH") ?: "streamx-release.keystore")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "streamx"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}

buildTypes {
    release {
        isMinifyEnabled = false // or true when ProGuard/R8 rules are finalized
        signingConfig = signingConfigs.getByName("release")
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

---

## 3. Building the Release APK

```powershell
# Windows
.\gradlew.bat assembleRelease

# macOS / Linux
./gradlew assembleRelease
```

The output APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## 4. Calculating the SHA-256 Checksum

Before publishing, compute the SHA-256 hash of the generated APK:

```powershell
# Windows PowerShell
(Get-FileHash -Algorithm SHA256 .\app\build\outputs\apk\release\app-release.apk).Hash.ToLower()

# macOS / Linux
sha256sum app/build/outputs/apk/release/app-release.apk
```

Example output:
`efe2de552bed8a67125670a7e79366116e398ad33432a2e12d7e0144a7e95d56`

---

## 5. Publishing to GitHub Releases

1. Go to your GitHub repository:
   `https://github.com/IconicAashu/StreamX/releases/new`
2. **Tag version**: e.g., `v1.0.0`
3. **Release title**: `STREAMX v1.0.0`
4. **Release Description**: Include the release notes, `versionCode`, and the computed SHA-256 checksum so the OTA updater can verify package integrity:
   ```markdown
   ## What's Changed
   - Official Initial Release with iOS Liquid Glass UI
   - Universal Media3 streaming engine
   - Stremio add-on integration
   - Local privacy profile system

   ### Package Information
   - Version: 1.0.0
   - Version Code: 1
   - Package Name: com.streamx.app
   - SHA-256: efe2de552bed8a67125670a7e79366116e398ad33432a2e12d7e0144a7e95d56
   ```
5. **Attach Assets**:
   - Drag and drop `app-release.apk` (or rename to `STREAMX-v1.0.0.apk`).
6. Click **Publish release**.

---

## 6. Verifying OTA Detection

1. Open STREAMX on your device.
2. Navigate to **Settings** → **About StreamX**.
3. Tap **Check for Updates**.
4. STREAMX queries `https://api.github.com/repos/IconicAashu/StreamX/releases/latest`, extracts the APK download URL, checks the `versionCode`, downloads the update, verifies the SHA-256 hash, and initiates Android's package installer.
