# Changelog

Notable changes to The Kids Channel are recorded here.

## [Unreleased]

## [1.9.8] - 2026-10-01

### Fixed

- Give Standard playback a fresh video view on channel changes and discard
  delayed callbacks and frame captures from the previous channel.
- Start the loading spinner's 200 ms delay when the vertical transition ends,
  and show a thinner blue spinner in the centre above the incoming screenshot.

## [1.9.7] - 2026-10-01

### Fixed

- Clear the previous channel's paused state when switching channels and keep
  the pause button hidden throughout the channel transition.

## [1.9.6] - 2026-10-01

### Added

- Show a loading spinner over the channel screenshot when waiting for video
  takes longer than 200 ms, in both player builds.

## [1.9.5] - 2026-10-01

### Fixed

- Keep VLC's channel screenshot visible until the video texture receives a
  frame, instead of hiding it on playback-time events or a fixed delay.

## [1.9.4] - 2026-10-01

### Fixed

- Activate normalization on VLC's actual player audio output instead of
  relying on startup options that LibVLC overrides.

## [1.9.3] - 2026-10-01

### Fixed

- Apply stronger boosting to very quiet videos while retaining compression
  for loud audio, and stop treating low-volume recordings as silence in the
  Standard build.

## [1.9.2] - 2026-10-01

### Fixed

- Save the current video and position before opening Settings and resume from
  that position when returning to playback.

## [1.9.1] - 2026-10-01

### Fixed

- Boost quiet audio as well as reduce loud passages in the VLC build, and
  widen the Standard build's automatic volume adjustment range.

## [1.9.0] - 2026-10-01

### Fixed

- Allow swiping to another channel while the current channel is still loading.

### Changed

- Require paused playback before holding the channel title to open Settings.
- Show the channel title only while paused or changing channels.
- Set the vertical channel transition to 150 ms and lower the swipe threshold
  to 10 percent of screen height.
- Fill the Settings hold indicator with red from both edges toward the finger's
  press position.
- Open Settings by holding the existing upper-left channel title for two
  seconds. Keep the pause icon in the center when playback is paused.

## [1.8.1] - 2026-10-01

### Fixed

- Give VLC a fresh video view on channel changes to prevent retained frames
  flashing over the next channel, and discard delayed events and preview
  captures from the previous channel.

## [1.8.0] - 2026-10-01

### Added

- Generate missing channel previews from the first frame of a video when
  channels are discovered.
- Track active watch time by root folder and channel on a Stats page in Settings.
- Temporarily disable root folders without removing their access or playback
  positions.

### Changed

- Replace the Paused label with a pause icon and double the control's size,
  using a rounded square shape. Hold it for five seconds to open Settings.

## [1.7.0] - 2026-09-30

### Added

- Replaced playback buttons with gestures: tap to pause or resume, swipe to
  change channels, and hold the Paused label for five seconds to open Settings.
- Added an interactive channel transition that follows the swipe and returns
  to the current channel when less than 20 percent of the screen is crossed.

### Changed

- Kept the current video playing during a swipe and used one saved preview per
  channel for the incoming channel.

### Fixed

- Corrected channel ordering and wraparound during repeated rapid swipes.
- Prevented taps during a swipe from pausing playback.
- Prevented stale previews and transition cleanup from covering or interrupting
  the active video.
- Kept playback visible when the next video starts within the same channel.

## [1.6.0] - 2026-09-30

### Added

- Animated channel changes with the current video frame sliding out and the
  next channel's saved preview sliding in when available.

### Fixed

- Kept the next channel's saved preview visible until video frames are ready,
  then faded it into playback over 100 ms to soften black flashes.

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
