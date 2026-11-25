# TikSnapLauncher

A custom Android Home launcher featuring a native-drawn, two-page home screen.

## Design references

The Base64 images in `normal_home_screen.txt` and `tiksnap_home_screen.txt` are **visual references only**. They are not rendered as launcher backgrounds or used as screen content.

The app draws its interface at runtime:
- A Samsung-inspired home page with clock/date, a weather/search card, app shortcuts, Google search bar, dock and page indicator.
- A second page with a 4-by-5 grid of real TikSnap app shortcuts, plus a TikSnap-only dock.
- An installed-app drawer with real app icons and launch actions.
- Swipe navigation between pages and swipe-up drawer access.

## Build

The GitHub Actions workflow builds a debug APK on pushes to `main` and on manual dispatch.

1. Download the `TikSnapLauncher-debug` artifact from the Actions run.
2. Extract `app-debug.apk`.
3. Install it on the Android device.
4. Open Android Settings → Apps → Default apps → Home app and select TikSnapLauncher.

## Package

`com.bytesnap.tiksnaplauncher`

TikSnap shortcut target: `com.bytesnap.tiksnap`.
