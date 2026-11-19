# fb-client

A tiny, privacy-minded Android client for the Facebook mobile website. It is a single
WebView around `m.facebook.com` with filtering on top, built for people who do not want
the official app on their phone.

> Unofficial. Not affiliated with, endorsed by or connected to Meta Platforms, Inc.
> "Facebook" is a trademark of Meta Platforms, Inc.

## Why

The official app asks for many permissions and keeps running in the background. The
mobile website in a normal browser is slow and keeps pushing the app. fb-client sits in
between:

- **One permission: Internet.** No contacts, location, camera, microphone, notifications
  or background work. When you leave the app, its timers are paused.
- **Third-party requests are dropped** before they load. Page resources may come only from
  Facebook's own domains (plus reCAPTCHA on the login checkpoint), and some of Facebook's
  logging endpoints get an empty response.
- **External links open in your browser**, without Facebook's click-tracking redirect
  (`l.facebook.com/l.php`) and without `fbclid` and similar tracking parameters.
- **No "open in app" redirects.** `intent://`, `fb://` and similar links are refused.
- No Google Safe Browsing lookups, no WebView metrics, no app backup of cookies.

## Features

- Menu (bottom right): Feed, Reels, Groups, Marketplace, Refresh, Settings
- Text size: 50–200%
- Filters, each can be switched on or off:
  - Sponsored posts (on by default)
  - "Open in app" banners (on by default)
  - Suggested posts
  - Posts from groups and pages you don't follow
  - Reels in the feed (the Reels menu item stays)
  - "People you may know"
  - Suggested groups
- Keyword filter. It also matches Facebook's automatic image descriptions ("May be an image
  of dog"), so a keyword like `dog` hides dog pictures too. Use the language your Facebook is
  set to.
- Autoplay videos: off by default
- Follows the phone's dark/light mode

Filters work by matching labels on the Facebook page (English and Hungarian). When Facebook
changes its page, some filters may stop working until they are updated in
`app/assets/cleaner.js`.

## Install

Download the APK from [Releases](../../releases) and open it on your phone. You have to allow
installing apps from unknown sources for your browser or file manager. Requires Android 13
or newer.

Each release lists the SHA-256 of the APK and of the signing certificate. Updates are signed
with the same key, so they install over the previous version and you stay logged in.

## Build

No Gradle and no AndroidX: the APK is built with `aapt2`, `javac`, `d8`, `zipalign` and
`apksigner` (see `build.sh`). Everything runs in a Docker container, so nothing needs to be
installed apart from Docker.

```powershell
.\build.ps1            # build -> out\fb-client.apk
.\build.ps1 -Install   # build and adb install
.\build.ps1 -Debug     # build with WebView debugging (inspectable over adb)
.\build.ps1 -Clean     # remove the build image
```

On Linux or macOS:

```sh
docker build -t fb-client-build - < Dockerfile
docker run --rm -v "$PWD:/src" fb-client-build
```

The first build creates a signing key in `keystore/`. Keep it: without it, a new build will
not install over an existing installation. It is ignored by git.

`build.ps1` assumes a Docker engine running in WSL (it mounts `E:\x` as `/mnt/e/x`). With
Docker Desktop, use the plain `docker run` command above with your project path.

## Layout

| Path | What it is |
|---|---|
| `app/java/home/fbclient/MainActivity.java` | WebView, request filtering, menu |
| `app/java/home/fbclient/SettingsActivity.java` | Text size and filter switches |
| `app/java/home/fbclient/AppSettings.java` | Stored settings, handed to the page as JSON |
| `app/assets/cleaner.js` | Hides filtered posts and banners on the page |
| `build.sh`, `Dockerfile`, `build.ps1` | Build |

## License

[MIT](LICENSE)
