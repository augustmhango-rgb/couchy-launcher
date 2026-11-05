# Apple TV Home — iPhone and Fire TV setup

This is a separate app (`com.conreo.appletvhome`). Your existing Couchy app and its settings stay intact. Android-based Fire OS 5+ can meet the API 21 minimum, but actual device compatibility has not been tested. Home on Fire documents redirection support for Fire OS 7/8 only. **Vega OS devices cannot run this Android APK.** Check Settings → My Fire TV → About first.

## Download and install

The public APK is not published until the signed release is uploaded. Do not assume a GitHub link below is live yet.

After publication, on the Fire TV:

1. Install **Downloader** from the Amazon Appstore.
2. Open **Settings → My Fire TV → Developer Options → Install unknown apps**. Allow **Downloader**. If Developer Options is hidden, open About and press Select on the device name seven times, then go back.
3. Open Downloader and enter `https://github.com/augustmhango-rgb/couchy-launcher/releases/latest/download/Apple-TV-Home.apk`.
4. Choose **Install**, then **Open**. Keep Couchy installed.

GitHub Actions also retains a debug APK under the run's **Artifacts**, accessible while signed in to GitHub. Those downloads are ZIP files and are intended for testing, not the simple Downloader installation path. Install releases for stable future updates. Debug and release use different certificates and cannot update each other.

## Home button, boot and wake

Use the separate [Home on Fire companion](https://github.com/toolicious/home-on-fire). No companion code is bundled or modified in Apple TV Home. Its GPLv3-or-later licence remains with its author.

1. In Downloader enter `https://github.com/toolicious/home-on-fire/releases/latest/download/home-on-fire.apk`, then Install.
2. Open Fire TV **Settings → Accessibility**. If Home on Fire appears with an enable switch, enable it.
3. Open **Home on Fire**, choose **Choose target app**, then **Apple TV Home**.
4. Enable its **Replace Home button** and **Launch on boot** options. Its boot option also handles standby wake according to its documentation.
5. Press Home to check. Restart the Fire Stick and check again.

**If Accessibility is blank or blocked:** Home on Fire requires a one-time `WRITE_SECURE_SETTINGS` grant through ADB. Apple TV Home cannot grant that permission or enable another app's Accessibility service. The companion's documented alternatives require a computer or Android device; a guaranteed iPhone-only setup is not available on these builds. Do not disable Amazon system services or root the device.

The launcher's own **Settings → System → Best-effort Boot Launch** option is off by default. It uses Android's boot broadcast; modern Fire OS may block the activity start. It does not intercept Home or wake. Prefer the companion's supported mechanism and avoid enabling both launch-at-boot options unnecessarily.

Amazon's boot logo remains. The companion reports brief Amazon launcher flashes on Fire OS 8 and a Home-triggered app-switch delay of up to five seconds on Fire OS 7. Firmware updates can change this. Back from other apps follows the other app's back stack and cannot always return here.

## Upload an icon from your iPhone

1. Connect both devices to the same trusted home Wi-Fi (not isolated guest Wi-Fi).
2. On the TV hold **Select** on an app → **Change Icon · Upload from iPhone**. Alternatively open **Settings → Customise Icons** and choose the app.
3. Keep that screen open. In iPhone Safari enter the **complete `http://` address shown on your TV**, including the port after the colon. Do not use HTTPS.
4. Enter the six-digit pairing code from the TV.
5. Choose a PNG, JPG or WebP image from Photos or Files. Adjust Size and the two crop sliders. The preview shows the result.
6. Tap **Save icon to TV**. The image is saved privately on the Fire Stick. On the TV press **Done** or Back.

Images must be smaller than 4 MB. PNG transparency is preserved. The app rounds the image at display time; adjust **Settings → Wallpapers, Accent, Spacing, Corners & Categories → Display → Corner roundness**. The upload page gives an approximate corner preview. Restore an app's original icon from its long-press menu.

The address works only while an upload is active. The session accepts one successful upload, expires after ten minutes, and closes when you leave the launcher or press Back. Local HTTP does not encrypt traffic; use trusted home Wi-Fi. No cloud hosting, subscription or account is used. Guest Wi-Fi isolation, VPNs, or routers that block device-to-device traffic can prevent pairing.

## Customise the layout

Use **Settings** for theme, columns, icon size, Top Shelf, animation and ambient background motion. The additional settings panel preserves Couchy's wallpaper, spacing, corner, category and app-visibility controls. Category views act as folders: create one in Categories, then hold Select on an app and choose **Add to Folder / Category**. Use **Folders** at the top to open it. This is a filtered view, not a nested tvOS folder animation.

Hold Select → **Move App**, then select the app it should appear before. Add favourite apps through the same menu. The upper shelf displays the first six favourites; until you choose favourites, it displays the first six discovered apps. Top Shelf artwork and app names change with focus; third-party streaming apps do not expose tvOS's proprietary recommendations here.

Custom photos/videos and ZIP backups use Android's document picker. On Fire OS builds without a document provider, these actions may be unavailable. Safari icon uploads remain independent of the picker. The ambient background is an original moving gradient; no Apple screensaver footage is included. A dedicated idle screensaver has not been implemented.

## Return to Amazon Home

1. Open Home on Fire and turn off **Replace Home button** and **Launch on boot**, or disable its Accessibility service.
2. Turn off Apple TV Home's own best-effort boot option if you enabled it.
3. Press Home. Amazon's original home remains installed and enabled.

Home on Fire also documents **long-press Home** as an escape to Amazon Home (Back closes Quick Settings if it appears). Apple TV Home's **System → Restore Amazon Home** opens Amazon's launcher and disables its own boot preference; it cannot turn off the companion for you. Uninstall Apple TV Home normally to remove its private data without affecting Couchy.
