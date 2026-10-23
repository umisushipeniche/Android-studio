# UMI Browser v1.3.5 — RELEASE SOURCE

Standalone fresh-install edition based on v1.3.4. Three UI languages: Chinese, European Portuguese and English. Use the language selector at the top of the home screen. Existing scanning, table directory, browsing, lock and PIN logic retained.

## Installation and compatibility
- Android 8.0 (API 26) and newer, subject to device and security policy. Camera QR scanning needs a functional camera and permission. Not guaranteed to run on every Android model.
- **Separate package:** `pt.umisushi.browser.release` (different from v1.3.4's `pt.umisushi.browser`). Installs alongside older versions, not an upgrade; local table directory and PIN are **not migrated**. Configure them again.
- Release version: `1.3.5`, versionCode 1. No beta marker.
- Build using the GitHub Actions workflow. The resulting APK may be a **debug-signed APK**; this does NOT constitute a signed production release. To distribute publicly, configure a secure, persistent release signing key and test install across devices.
- A consistent signing key is required for future updates to this new package. Never publish a private signing key in GitHub.
- Source package is not a prebuilt APK; compilation and real-device tests were not available in this environment.
