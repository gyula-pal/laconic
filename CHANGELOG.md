# Changelog

Each version is a sideloaded APK; the README links the latest one.

## 0.9 — 2026-11-12

### Added
- Search also finds contacts and calendar events, from two letters on. Results come in sections (apps,
  contacts, events), each with a few rows and a *+N more* row; the row Enter opens is marked.
- Tap a contact to see its numbers, emails and the actions other apps added to it. The handset opens the
  dialler with the number typed in (Laconic never places a call itself), the bubble starts a text.
- Tap an event to open it in your calendar. Events are the next 365 days, one line per event.
- Start with `a `, `c `, `e ` or `w ` to search only apps, contacts, events or the web.
- Search forgives small typos and finds initials (*pe* finds Photo Editor); accents don't matter.
- Settings has a Search section, with a switch each for contacts and events. A source that is switched on
  but not allowed shows one line to allow it or hide it.

### Changed
- Apps you open often rise among close matches, but never above a better match.
- Weather refreshes every 15 minutes instead of 5 (the source changes no faster). A failed price or
  weather refresh is tried again after a minute, not five.
- Coming back from a letter list or search no longer fetches the weather, price and next event again;
  the clock shows the last ones at once.
- Less work in the background: a new notification no longer makes Laconic re-read all of them, and a
  widget being resized is told its new size once, not on every frame.

### Fixed
- An app removed or updated while its row is on screen can no longer take the home screen down with it.
- A very long row of brackets typed into search can no longer crash it.
- The player shows the cover when an app sends it a moment after the title.
- The clock's event line skips events without a title.
- The weather no longer uses a location older than two hours.
- After a dark-mode, font-size or language change, the old screen is no longer kept in memory by its widgets.
- Letters typed in order only match inside one word: a name is no longer found because its letters are
  spread over a first and a last name.
- All-day events show on the right day in every time zone.
- Prices and weather keep refreshing after the phone's clock is set back.

## 0.8 — 2026-11-05

### Added
- Ink colour (settings → Home → Ink): white, or any colour from a picker (hue, saturation, brightness, hex).
  Clock, letters, lines, names and the black-and-white icons all take it.
- Background (settings → Home → Background): the wallpaper, or a solid colour of your own.
  A "Hard to read" note appears when ink and background are too close.
- + on each box in edit mode (Add apps in the ⋯ menu of narrow ones): your apps with search and checkboxes.
  Tick to add (pinning it, or moving it from another box, whose name is shown), untick to take it out and unpin.
- Box names take less room: a 16 dp line above the icons. In a one-row box the row sits centred in the height
  under the name, instead of at the top with an empty band below.
- In edit mode a short one-row box shows its actions as one ⋯ button at the start of the row, not over its icons.
- A box's name keeps 6 dp clear of the line above it (it touched it).
- Letters → Off: no letter strip on either side; the boxes and the list get the margins back (6 icon columns
  instead of 5 on a 411 dp screen). Apps outside home are reached by swiping up for search.
- Dragging a line stops where any box behind it would get narrower than 64 dp, not only the two next to it;
  layouts saved with a sliver of a box open it up again.
- With one named box, every box keeps the name line, so rows in boxes side by side line up.
- The ⋯ menu shows the same icons as the box's buttons (+, name, the two splits, ×).
- A box only tall enough for one row of icons is a lane: its apps sit in one row that scrolls sideways.
- Lines between boxes: faint, clear or bold (settings → Apps → Lines, shown with the boxes layout).
- Each corner button can be the flashlight, the camera, any app or nothing: long-press it, or settings →
  Home → Left corner / Right corner. Tap still does the action at once.

### Fixed
- With a light background, the status and navigation bar icons turn dark so they stay visible.
- Typing a colour code key by key no longer scrambles it (the cursor was left behind the added #).
- A carried app floats a little above and to the left of the finger (to the right with the strip on the left), on a soft disc, and a thin line shows where it will land; under the
  fingertip it was hidden and seemed to jump on release.
- DONE sits centred in its pill.
- Holding beside the boxes (the margin out to the screen edge, where a thumb reaching for the letters lands)
  no longer starts edit mode; only a hold on a box does.
- A dropped app lands where it was let go: apps scrolled out of a box pushed it one slot later each.
- The swipe-up band at the bottom no longer swallows taps on the apps or boxes under it (with the corner
  buttons off).
- A line between boxes dragged and then interrupted goes back instead of staying drawn where it was left; the
  64 dp minimum box size is worked out from the boxes' current size.

## 0.7 — 2026-11-05

### Changed
- Wave is now called **Laconic**, with a new icon: a thin L in a thin circle. The package name stays
  `cc.gyula.wave`, so it updates in place and keeps every setting.

### Added
- Boxes: drag an app (hold, then drag) to another box or to a new place in its own box; holding an app on
  home starts edit mode and the drag at once. Narrow boxes show one ⋯ button for their controls.
- Boxes can have names (FINANCE, WORK…), shown small at the top of the box; a blank name removes it.
- DONE is a white button.

### Fixed
- In edit mode the box buttons no longer cover the first row of icons.
- Tapping empty space inside a box no longer ends edit mode.

## 0.6 — 2026-11-05

### Added
- Boxes layout (settings → Apps → Layout): the pinned apps as icons in boxes that tile the space under the
  clock, like a tiling window manager — only the lines between boxes, none on the outer edge. Long-press to
  edit: split a box side by side or one above the other, remove one (its apps go to its neighbour), drag a
  line to resize, tap an app then a box to move it. A box with more apps than fit scrolls. The boxes hold the
  pins, so switching back to the list loses nothing.
- Swipe up from the empty band at the bottom, between the corner buttons, opens search with the keyboard.

## 0.5.1 — 2026-11-05

### Added
- Flashlight and camera buttons in the bottom corners, where the lock screen has them: one tap. The letter
  strip now ends just above them. Off in settings → Home → Flashlight and camera.

### Changed
- Home always comes back at the top of the list, instead of where it was left scrolled.
- The event line only looks as far as the end of tomorrow; a Thursday event shows from Wednesday on.
- Lint no longer fails the build because a library has a newer version out.
- Release builds are signed from a dedicated keystore outside the repo, holding the key every version so far
  was signed with, so updates still install over earlier versions. Without it, builds fall back to the debug key.
- The architecture diagram in the README draws each layer as a row of its packages.

### Added
- Screen reader support: the music buttons and the ☆ have names; each letter on the strip opens its list when
  activated; the swipes are also actions (Shortcuts, Unpin, Stop and hide); the band says what tapping and
  long-pressing do and reads ₿ and the alarm mark as words.

## 0.5 — 2026-10-27

### Added
- Prices work in the US: Binance turns US addresses away, so when it doesn't answer, coins come from Coinbase.
  Stocks, ETFs and metals still come only from Binance.
- Tests for the data layer (weather and price answers, widget placement, media sessions, usage stats) and for
  the screens (swiped-away player, letter strip, Enter in search): 134 in all.
- `./gradlew check` fails on any lint or compiler warning; `.githooks/pre-push` runs it before a push.

### Changed
- `core` is its own pure-Kotlin Gradle module, so the build refuses Android code there.
- Current toolchain and libraries (Android Gradle plugin 9.4, Kotlin 2.4, Compose 2026.09), versions in
  `gradle/libs.versions.toml`; targets Android 16.
- The baseline profile is recorded with the thumb on the letter strip (it used to land in the Back zone).

## 0.4.2 — 2026-10-27

### Changed
- Home opens faster in a freshly started process, 97 ms instead of 118 ms (median of 10 runs on a Pixel 10):
  a baseline profile has Laconic's own code compiled ahead of time.
- Code reorganised into three layers: `core` (plain Kotlin rules, no Android), `data` (Android services and
  storage) and `ui` (one package per feature). See *Architecture* in the README.
- The clock is split into `Clock`, which gathers what the band shows, and `ClockFace`, which only draws it.
  The settings sheet is now a component of its own.

### Fixed
- Older widgets that ask for exactly two cells (Android's "70 × cells − 30 dp" rule) sometimes started three
  columns wide: their width comes back rounded to whole pixels and tipped over into the next cell.

### Added
- A `baselineprofile` module that records the profile and times home opening with and without it.
- Unit tests for the rules behind home lists, search, the player, widget sizing and the clock text;
  Robolectric tests for settings storage and migrations and for Back on home; screenshot tests for the
  clock's nine band styles.

## 0.4.1 — 2026-10-27

### Fixed
- A music player swiped away came back after Spotify restarted in the background: Spotify re-sends its
  long-paused song stamped as just paused. A pause at the same position now keeps its original time.
- A swiped-away player also came back after a letter list, search or Back; Back on home no longer closes
  and recreates the home screen.
- The ☆ in the letter bubble sat low; it is drawn, centred.

### Added
- The next-event line opens out from the middle when coming home, after the band.

## 0.4 — 2026-10-27

### Added
- Home can list the 8 most used or most recent apps instead of pins, in that order or A–Z (needs usage access).
- Rename an app, hide it from the lists; *Hidden apps* in settings lists every app with a switch.
- Notification dots between an app's icon and name.
- *App icons* off: names only.
- Next alarm on the band; tap the event line to open the event.
- Search opens web addresses, shows sums (tap to copy), and searches the web when nothing matches.

### Changed
- Band items centred on their capitals; settings on/off rows are switches.

## 0.3.3 — 2026-10-27

### Added
- Widget pages on both sides of home, up to three on each.

### Fixed
- Widgets start at their own size, so two 2×2 widgets share a row.

## 0.3.2 — 2026-10-26

### Fixed
- Letters sat about 2 dp low on their stripes, the ☆ about 4 dp; both centred to within 0.2 dp.

## 0.3.1 — 2026-10-26

### Added
- Widgets resize sideways, in quarters of the page; narrower ones share a row.
- Album art in greyscale by default, colour as a setting.

## 0.3 — 2026-10-26

### Added
- Widget pages with Laconic's own picker, and long-press resize and remove.
- The clock band above, through or below the weekday; plain, inverted or between lines.
- Coming-home animation; stripes behind the letters; either-edge hidden strip.
- Settings as a half-height sheet with folding sections.

### Changed
- Pulling down opens notifications by default; search is a setting. Portrait only.

## 0.2 — 2026-10-26

### Added
- Swipe an app right for its shortcuts; pull down to search; a music player under the clock.
- Any Binance coin, stock, ETF or metal on the band; letters on the left with mirrored lists.

## 0.1 — 2026-10-26

- Pinned apps, an A–Z strip that bends into a wave under the thumb, and a weekday clock with weather,
  date, Bitcoin price and the next calendar event.
