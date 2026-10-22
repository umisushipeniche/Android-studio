# UMI Browser v1.3.3 — repair build

This is **source code**, not an APK. Upload its contents to the root of the GitHub repository and run Actions → Build UMI Browser APK. Download the workflow artifact and extract `app-debug.apk`.

Fixes: drawn lock icon (no platform emoji), larger gold lock control, fully custom staff keypad, custom next-table action sheet, rotation handling, recover-mode dialog transition. The source is based on the prior v1.3.2 project and retains QR scanning and browser navigation.

The workflow verifies the APK with Android `apksigner` after compiling. It does not guarantee on-device installation; test on TCL before use.

**Important:** GitHub Actions debug APKs may have different signing keys between builds. An APK with a different signing key cannot update an installed copy. Do not uninstall a working version until the new APK is verified. The hard-coded administrator recovery code is not a secure secret.
