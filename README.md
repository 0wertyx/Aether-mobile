# AETHER Mobile

Android interface port, version 0.1.0-mobile.

**Import in progress:** native Android shell, Gradle configuration, responsive CSS, navigation, font cache, UI helpers and host test imported. The remaining original HTML, JS, CSS, icons, fonts and font catalog are not imported yet. APK is not ready; do not treat this revision as a release.

## In-app updates

The native updater checks the public GitHub Releases endpoint on foreground launch, at most every six hours (manual checks can call `AetherAndroid.checkForUpdates()` from the local interface). It accepts stable release assets named `AETHER-Mobile-<versionCode>.apk`, checks the GitHub SHA-256 digest, package name, actual versionCode, and current signing certificate, and delegates installation to the standard Android installer with user confirmation. No root, silent install, or external updater app.

Release builds must use the same private signing key for every version; ordinary debug APKs are not interchangeable with release APKs. Increment `versionCode` and `versionName` in app/build.gradle. Release signing requires the four AETHER_KEYSTORE environment variables (see build.gradle); never commit the private keystore. Upload the signed, universal APK as `AETHER-Mobile-<versionCode>.apk` to a published stable GitHub Release. The updater rejects assets without a SHA-256 digest or with a different certificate. Android user confirmation is required.

Updater code is not yet compiled or device-tested. The original web assets are still pending, and no signed release has been published.
