# The Kids Channel

An Android video player designed to work like a simple television for children.

Each immediate child folder inside an added root becomes a channel. Videos in a
channel folder and all of its nested folders play in natural filename order,
then loop. The app remembers the current video and playback position for every
channel.

Playback has only Channel Up and Channel Down controls. There is no timeline,
seeking, playlist editing, or next-video button.

## Development environment

Install [Nix](https://nixos.org/) and [devenv](https://devenv.sh/), then enter the
project environment:

```sh
devenv shell
```

This provides JDK 17, Gradle, Android SDK Platform 35, Android Build Tools 35,
and ADB. If direnv is installed, `direnv allow` activates the same environment
automatically.

Build and test the app with:

```sh
./gradlew test assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Using the app

1. Open the app and tap **Add root folder**.
2. Select a folder whose immediate child folders contain videos.
3. Add more root folders from the settings button.
4. Use the up and down buttons during playback to change channels.

Prefix names with numbers when an explicit order is needed, such as
`01 Welcome.mp4`, `02 Songs`, and `03 Stories.mp4`.
