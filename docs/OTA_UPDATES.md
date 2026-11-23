# STREAMX — Over-The-Air (OTA) Update System

This document explains the architecture, security guarantees, data models, and network endpoints powering the in-app Over-The-Air update system in STREAMX.

---

## 1. High-Level Flow

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Trigger                                                  │
│    - Manual: Settings → About StreamX → "Check for Updates" │
│    - Background: Periodic WorkManager (UpdateCheckWorker)   │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Fetch Latest Release Manifest                            │
│    Endpoint: api.github.com/repos/IconicAashu/StreamX/      │
│              releases/latest                                │
│    Fallback: raw.githubusercontent.com/.../version.json     │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Parse & Validate Version Code                            │
│    - Checks remote versionCode > BuildConfig.VERSION_CODE   │
│    - Extracts APK download URL from release assets          │
│    - Extracts expected SHA-256 checksum from body           │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Streamed Download & Integrity Verification               │
│    - OkHttp chunked streaming to internal cache: updates/   │
│    - Real-time download percentage & speed emission         │
│    - MessageDigest calculation of actual SHA-256 hash       │
│    - Compare calculated hash == expected hash               │
│    - Verify packageArchiveInfo.packageName == com.streamx.app│
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. Safe System Handoff                                      │
│    - Generate FileProvider content URI:                     │
│      content://com.streamx.app.fileprovider/cache_updates/  │
│    - Launch Intent.ACTION_VIEW with FLAG_GRANT_READ_URI     │
│    - Android system PackageInstaller executes update        │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Configuration & Endpoints

The update configuration is centralized in `com.streamx.app.domain.model.UpdateConfig`:

```kotlin
object UpdateConfig {
    const val GITHUB_OWNER = "IconicAashu"
    const val GITHUB_REPO = "StreamX"
    const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val FALLBACK_MANIFEST_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/version.json"
    const val EXPECTED_PACKAGE_NAME = "com.streamx.app"
    const val FILE_PROVIDER_AUTHORITY = "com.streamx.app.fileprovider"
}
```

---

## 3. Cryptographic Security & Safety Guarantees

1. **SHA-256 Checksum Validation**:
   - The parser inspects the release body for regex patterns such as `SHA-256:\s*([0-9a-fA-F]{64})` or standalone 64-character hex strings.
   - During download, bytes are written directly while computing the SHA-256 hash in a single pass.
   - If the hashes do not match, the APK is immediately deleted and the user is alerted to prevent corrupted or tampered installations.

2. **Package Identity Check**:
   - Before launching the installer, the APK is verified with Android's `PackageManager.getPackageArchiveInfo()`.
   - The APK's package name MUST match `com.streamx.app`.

3. **Android 7.0+ FileProvider Strict Mode Compliance**:
   - No `file://` URIs are exposed across process boundaries.
   - The target APK is saved in the app's cache directory under `updates/` and shared via `androidx.core.content.FileProvider` with `FLAG_GRANT_READ_URI_PERMISSION`.

4. **Android 8.0+ Unknown App Sources (`REQUEST_INSTALL_PACKAGES`)**:
   - If the app lacks permission to install unknown apps, it gracefully prompts the user with `ACTION_MANAGE_UNKNOWN_APP_SOURCES` before proceeding.

---

## 4. Fallback Manifest Format (`version.json`)

If the app cannot reach the GitHub Releases API (e.g. rate-limiting or network proxies), it falls back to parsing a raw manifest at `https://raw.githubusercontent.com/IconicAashu/StreamX/main/version.json`:

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "downloadUrl": "https://github.com/IconicAashu/StreamX/releases/download/v1.1.0/STREAMX-v1.1.0.apk",
  "sha256": "3a7b8c...64_hex_chars...",
  "releaseNotes": "Performance improvements and bug fixes.",
  "isMandatory": false
}
```

---

## 5. Background Check Worker

`UpdateCheckWorker` extends `CoroutineWorker` and runs periodically via WorkManager with battery-not-low and network-connected constraints. When an update is detected, it posts a high-priority system notification through the `updates` notification channel.
