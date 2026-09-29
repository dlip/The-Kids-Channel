# Development

## Environment

Install [Nix](https://nixos.org/) and [devenv](https://devenv.sh/), then enter
the project environment:

```sh
devenv shell
```

This provides JDK 17, Gradle, Android SDK Platform 35, Android Build Tools 35,
and ADB. If direnv is installed, `direnv allow` activates the same environment
automatically.

## Build and test

```sh
./gradlew lint test assembleDebug
```

The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

## Deploy to a connected device

Enable USB debugging on the device, connect it, and confirm that ADB can see
it:

```sh
adb devices
```

Install the debug build and launch the app:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.thekidschannel -c android.intent.category.LAUNCHER 1
```

## Signed releases

The `Signed Android release` GitHub Actions workflow builds a signed APK. A
manual run stores the APK as a workflow artifact. Pushing a tag beginning with
`v`, such as `v1.1.0`, also creates a GitHub Release and attaches the APK and
its SHA-256 checksum.

Create the signing key once:

```sh
keytool -genkeypair -v \
  -keystore the-kids-channel-release.jks \
  -alias the-kids-channel \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Keep the keystore and its passwords in a secure backup. Every future update
must use the same key. Keystore files are ignored by Git and must not be
committed.

Install the [GitHub CLI](https://cli.github.com/), authenticate it, then add
the four repository secrets:

```sh
base64 -w 0 the-kids-channel-release.jks | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD
```

Enter `the-kids-channel` for `ANDROID_KEY_ALIAS`. If no separate key password
was set when the keystore was created, use the keystore password for
`ANDROID_KEY_PASSWORD` too.

Create a published release:

```sh
git tag v1.1.0
git push origin v1.1.0
```

For an unpublished build, open **Actions → Signed Android release → Run
workflow**, enter a version name, and download the resulting artifact when the
run completes.
