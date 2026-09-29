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
./gradlew lint test assembleStandardDebug assembleVlcDebug
```

The debug APKs are written to:

- `app/build/outputs/apk/standard/debug/app-standard-debug.apk`
- `app/build/outputs/apk/vlc/debug/app-vlc-debug.apk`

## Deploy to a connected device

Enable USB debugging on the device, connect it, and confirm that ADB can see
it:

```sh
adb devices
```

Install the debug build and launch the app:

```sh
adb install -r app/build/outputs/apk/standard/debug/app-standard-debug.apk
adb shell monkey -p com.thekidschannel -c android.intent.category.LAUNCHER 1
```

## Signed releases

The `Signed Android release` GitHub Actions workflow builds signed Standard and
VLC APKs. A manual run stores both APKs as a workflow artifact. Pushing a tag
beginning with `v`, such as `v1.1.0`, also creates a GitHub Release and attaches
both APKs and their SHA-256 checksums. Its release notes come from the matching
version heading in [CHANGELOG.md](CHANGELOG.md).

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

Move the entries under `Unreleased` into a dated version section, for example
`## [1.5.0] - 2026-10-01`, then create a published release:

```sh
git add CHANGELOG.md
git commit -m "Prepare 1.5.0 release"
git push origin main
git tag v1.5.0
git push origin v1.5.0
```

The tag version must have a non-empty `## [version]` section in
`CHANGELOG.md`. The release workflow stops before building if that section is
missing. Its extraction can be checked locally with:

```sh
bash .github/scripts/extract-changelog.sh 1.4.0 CHANGELOG.md /tmp/release-notes.md
cat /tmp/release-notes.md
```

For an unpublished build, open **Actions → Signed Android release → Run
workflow**, enter a version name, and download the resulting artifact when the
run completes.

The VLC flavor's dependency notice is in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and is also packaged inside
the VLC APK.
