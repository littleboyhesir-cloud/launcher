# TV Home (Apple TV-style launcher)

A custom home screen for Android TV: big rounded app cards, focus zoom, clock,
and a Manage apps screen where you hide the apps you don't want to see.

## Build the APK (no PC tools needed)
1. Create a free GitHub account and a new empty repository.
2. Upload ALL files from this folder, including the hidden `.github` folder.
3. Open the repo's **Actions** tab, pick **Build APK**, press **Run workflow**.
4. When it turns green (about 5 minutes), open the run and download **TVLauncher-apk**.
   Unzip it to get `app-debug.apk`.

## Install on the TV
- Put the APK on a USB stick, or use the **Downloader** app with a link to the file.
- Allow "Install unknown apps" for the app you use to open it.

## Make it your home screen
- Press the Home button. If Android asks which home app to use, pick **TV Home** and "Always".
- Or look in Settings > Apps > Default apps > Home app (the name varies by TV).
- If your TV won't allow changing the default home, open **TV Home** from the app list each time.

## Using it
- OK opens an app. Hold OK on an app to hide it.
- **Manage apps** shows everything and lets you bring hidden apps back.
- **Settings** opens the TV's normal settings.
- Hiding only removes an app from this screen. It does not uninstall it.
