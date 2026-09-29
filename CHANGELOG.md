# Changelog

Notable changes to The Kids Channel are recorded here.

## [Unreleased]

## [1.5.1] - 2026-09-30

### Fixed

- Preserved channel playback positions when switching channels quickly,
  including while VLC is still starting or waiting to seek.

## [1.5.0] - 2026-09-29

### Added

- Optional automatic audio normalization, enabled by default, for more
  consistent volume between videos and channels.
- Changelog-backed GitHub release notes with validation for missing or empty
  version sections.

## [1.4.0] - 2026-09-29

### Added

- Saved video-frame previews that appear while a channel resumes or loads.

### Fixed

- Prevented previews from being saved against the wrong channel during rapid
  channel changes.
- Removed preview movement and fading when live video becomes ready.

## [1.3.3] - 2026-09-29

### Fixed

- Restored VLC video output after leaving the app and returning to it.

## [1.3.2] - 2026-09-29

### Changed

- Redesigned the launcher icon as a rounded old CRT television.

## [1.3.1] - 2026-09-29

### Changed

- Refined the launcher icon's rounded styling.

## [1.3.0] - 2026-09-29

### Added

- Added a VLC-based build for devices and video formats that do not render
  correctly with the Standard player.
- Published separate signed Standard and VLC APKs in each release.

## [1.2.1] - 2026-09-29

### Fixed

- Improved Standard-player video rendering compatibility on Huawei Android 9
  devices.

## [1.2.0] - 2026-09-29

### Added

- Added pause and resume playback controls.
- Added the party-hat launcher icon.

### Changed

- Combined pause and protected Settings access into one control.
- Reworked the documentation around local video files and app installation.

## [1.1.0] - 2026-09-28

### Added

- Added folder-based channels with nested-folder playback and saved progress.
- Added looping, immersive fullscreen playback, and auto-hiding controls.
- Added protected Settings access by holding the Settings control for five
  seconds.
- Added signed Android releases through GitHub Actions.
