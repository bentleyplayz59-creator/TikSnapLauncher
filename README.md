# TikSnapLauncher

A custom Android Home launcher featuring a TikSnap-themed home screen.

## Home screen reference images

Two reference images are stored as full Base64 data URLs (each file is one line starting with `data:image/png;base64,`):

| File | What it is |
| --- | --- |
| `normal_home_screen.txt` | The normal home screen: a Samsung Galaxy phone showing the standard app icons (Galaxy Store, Gallery, Play Store, Google) and the regular dock. |
| `tiksnap_home_screen.txt` | The TikSnap home screen: the phone home screen where every app icon and label has been replaced with the TikSnap icon and the name "TikSnap". |

To turn either file back into an image (for example in Termux), strip the prefix and decode it:

```sh
sed 's/^data:image\/png;base64,//' tiksnap_home_screen.txt | base64 -d > tiksnap_home_screen.png
sed 's/^data:image\/png;base64,//' normal_home_screen.txt | base64 -d > normal_home_screen.png
```

## Getting started

1. Clone the repository:

   ```sh
   git clone https://github.com/bentleyplayz59-creator/TikSnapLauncher.git
   ```

2. Open the project in Android Studio and build it, or build it from the command line.
3. Install the app on your device and set it as your default Home app in Android settings.

## Contributing

Issues and pull requests are welcome.
