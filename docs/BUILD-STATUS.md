# Build and delivery status

Version 0.1.0, application ID `com.conreo.appletvhome`, min SDK 21, target/compile SDK 34, JDK 17.

## Verified in the cloud workspace

- `testDebugUnitTest`: 6 tests, 0 failures, 0 errors, 0 skipped. Tests exercise real JVM sockets for pairing/token checks, invalid image retry, cross-origin and DNS-rebinding rejection, size limits, attempt limits and cancellation. They do not exercise Android bitmap decoding, Safari or a television.
- `assembleDebug`: passed.
- `assembleRelease`: passed, including R8 and resource shrinking.
- `lintRelease`: passed with 0 errors and 28 warnings (mostly inherited unused resources/attributes; not warning-free).
- Final signed release: approximately 2.1 MB. `apksigner verify` passed v1/v2/v3. Application ID/minimum API confirmed with `aapt`.
- Signing certificate SHA-256: `3d4a3ca83fe9313dbc830dab5189a5ae982271e4cc5fc4bb40c4fc0e2ce372f3`.
- Signed APK in this task's workspace: `/workspace/shared/downloads/Apple-TV-Home-0.1.0.apk`.

## Not verified or completed

- No real Fire Stick, Android TV emulator or iPhone Safari session was available. Remote focus, long-press behaviour, boot/wake, memory usage, frame rate and 1080p/4K output need hardware validation. Compilation is not evidence that those behaviours work on a device.
- Home on Fire is a separately installed companion. Its permissions and target selection cannot be configured automatically by this unprivileged launcher. Some firmware requires a one-time ADB permission grant, which cannot be promised with an iPhone alone.
- Top Shelf uses installed app artwork and names, not private tvOS content recommendations.
- Categories are flat folder-like views, not nested tvOS folders. No dedicated idle cinematic screensaver has been implemented. The original ambient gradient and user-selected local video provide animated backgrounds.
- Android's blur modifier depends on Android 12/API 31 rendering support; older Fire OS may display a sharp image. A software blur fallback is not yet implemented.
- Photo/video selection and backup/restore require a document provider, which some Fire OS builds omit. iPhone icon upload does not require one.
- Settings retains Couchy's additional controls. Legacy layout/status options are not all used by the new home screen.
- Source changes are published to `feat/apple-tv-home` in the user fork. Draft PR: https://github.com/augustmhango-rgb/couchy-launcher/pull/1. The checked-in signed development APK is available in `distribution/`; CI verifies its checksum, signing certificate and source-file manifest before uploading it as an artifact. A separate job can publish that same pre-signed APK as a prerelease. Check the actual workflow/release status before claiming publication.

## Release key custody

A dedicated persistent release key and random password were generated in `/workspace/.private/apple-tv-home/`, outside the Git checkout, with restrictive permissions. Do not commit or publish them. The key currently exists only in the task environment and needs secure durable custody before promising long-term update availability. Future APK updates must use this same certificate and a higher versionCode.

The workflow expects `APPLETV_KEYSTORE_BASE64` and `APPLETV_KEYSTORE_PASSWORD` as repository Actions secrets, with alias `appletvhome`. These secrets have **not** been configured. Release publication deliberately fails rather than publishing an unsigned APK when secrets are absent. Ordinary CI uploads debug, unsigned release, and the checked-in signed installer artifacts. The initial development installer is pre-signed locally, so its prerelease publication needs no private key in CI. The source hash manifest prevents accidentally publishing that binary after changes to its source; regenerate and re-sign for a new source version. Debug APKs cannot update the separately signed release APK.
