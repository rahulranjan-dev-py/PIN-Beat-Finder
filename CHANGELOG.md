# Changelog

## v0.12.0 — batch entry from the PIN picker, account office suggestions, per-beat export

### Added
- **Batch entry from the Fetch picker.** Tick several offices under a PIN and tap "Add N
  offices": the form opens for the first office, "Save & next" files it and moves to the next,
  "Skip" drops one without saving. The header shows "Office 2 of 5". Only the office fields
  carry over; locality, beat and remarks start blank for each office.
- **Account Office suggestions.** Typing in Account Office searches the built-in directory for
  SO/HO/GPO offices (same PIN first, then same district) and inserts the full office name.
- **Share one beat or one office.** Each card in the By-beat view has a share button with two
  choices: this beat, or every beat of the office. Both produce the same `.xlsx` format as the
  full backup, named after the office and beat.
- **Data sources in Settings.** Names the data.gov.in directory (GODL licence), the India Post
  facility master and the online providers, and shows the loaded snapshot version.

## v0.11.0 — office suggestions, local counts in the PIN picker, bulk type fix

### Added
- **Office Name suggestions.** Typing two or more letters in Office Name searches the built-in
  directory (same phonetic engine as All-India) and offers matching offices with their PIN,
  district and state; offices under the PIN already typed come first. Picking one fills type,
  name, account office, district, state and PIN — no need to know the PIN first.
- **Local counts in the Fetch picker.** Each office listed for a PIN now shows how many beats and
  villages are already filed under it locally, so staff can see at a glance which offices have
  data and which are still empty.
- **Fix office types in bulk.** The By-beat view has an "Office types" chip that opens a list of
  every office (villages, beats, PINs) with a type dropdown. Changing it re-types every record of
  that office at once. Offices whose records disagree on the type are flagged "Mixed types".

## v0.10.0 — office type dropdown, fetch offices by PIN

### Changed
- **Office fields.** The separate "Branch Office (BO)" and "Sub Post Office (SO)" fields are
  replaced by an **Office Type** dropdown (GPO, HO, IDC, SO, BO), an **Office Name** field and an
  optional **Account Office (SO/HO)** field. Cards, the "By beat" view and the delete prompt show
  the office as "Name TYPE" (e.g. "Barbendia BO").
- **Excel template** has the new columns (`Office Type*`, `Office Name*`,
  `Account Office (SO/HO)`); the Instructions sheet lists the allowed type codes. Spreadsheets
  made with the old template still import (BO → type BO with the SO as account office; SO-only
  rows → type SO).
- **Existing data is migrated in place.** Old records keep everything; a name typed with a
  suffix ("Rampur BO", "Sitapur S.O.") loses the suffix and it sets the type; a record that only
  had a sub post office becomes an SO record.

### Added
- **Fetch offices by PIN.** In the add/edit form, type the PIN and tap **Fetch**: the built-in
  directory lists every office under that PIN (type, delivery status, account office). Tapping
  one fills office type, name, account office, district and state, so office names are never
  typed from memory.
- **Account office in the built-in directory.** The bundled directory is now merged with the
  India Post facility master (from the CSI SPM Help tool's `SPM.db`): 160,067 offices carry their
  account office (e.g. Barbendia BO → Nirsa Chatti SO) and 6,984 offices missing from
  data.gov.in were added. Snapshot version `2026-09.2`; the directory reloads once on first
  launch of this version.
- "Add to local" from an All-India result also fills the office type and account office.

## v0.9.1 — built-in directory actually loads

### Fixed
- **"Built-in directory could not be loaded (india_post_directory.tsv.gz)"** on v0.8.0 and
  v0.9.0. The Android build tooling silently decompresses assets ending in `.gz` and drops the
  extension, so the file the app looked for was never in the APK. The asset is now shipped under
  a neutral name and the loader detects gzip by content, so it works either way. The one-time
  load runs on first launch of this version.

## v0.9.0 — updates, crash reports, database tests

### Added
- **Update check.** The app asks this repository's GitHub Releases page (once every 6 hours when
  online, or on demand from Settings → Updates) and shows a banner with a Download button when a
  newer build exists. Dismissing hides the banner for that version.
- **Crash reports.** If the app crashes, a plain-text report (version, device, stack trace) is
  saved on the device only. Settings → Crash reports offers Share and Delete; a one-time message
  appears on the next launch.
- **Database tests on CI.** Robolectric tests now exercise the beat directory DAO (phonetic and
  substring search, filters, import transaction) and the bundled directory seeder/DAO against a
  real SQLite, so regressions in the queries are caught before release.

### Changed
- The local tab's "Look up online" action is now "Find in All-India".

## v0.8.0 — the whole of India, built in

### Added
- **Bundled All-India directory.** The complete Department of Posts pincode directory
  (165,627 offices, 19,586 PINs, snapshot 2026-09) ships inside the app. PIN and name lookups
  are answered from the device — instantly and offline — with the same misspelling-tolerant
  phonetic search as the beat tab. Online sources are only consulted when the built-in directory
  has no match (for example an office newer than the snapshot).
- On first launch after installing, the directory is loaded once (a progress bar shows on the
  All-India tab; about 3 MB, a few seconds). Searching works meanwhile via the online sources.
- Settings → Online data sources now lists the built-in directory with its snapshot version.

### Changed
- The "Online All-India" tab is now simply **All-India**.

## v0.7.0 — faster lookups, grouped results, stable updates

### Fixed
- **Updates now install over the previous version.** Each CI build used to sign with a freshly
  generated debug key, so Android reported "package conflicts with an existing package". Builds
  now share one committed debug keystore. **One last uninstall of v0.6.0 or older is required**;
  from v0.7.0 onwards updates install in place.

### Changed
- **Faster online search.** All data sources are queried in parallel and the fastest answer
  wins (previously they were tried one after another, each allowed up to 20 s). Timeouts are
  tighter and the static directory mirror, usually the quickest, is tried first.
- **Grouped results.** When a name exists in several states, the online list groups by state
  with sticky headers and counts; groups start collapsed when there are more than two states.

### Added
- **Data sources status** in Settings: which sources are working, how fast they answered last
  time, and a "Test now" button.
- **High contrast** theme option for bright sunlight.
- Vibration tick on filter chips.
- Redesigned launcher icon (with a themed/monochrome variant on Android 13+).

## v0.6.0 — settings, Hindi, offline awareness

### Added
- **Settings** (gear icon): which tab the app opens on, theme (system/light/dark), text size
  (normal/large), language, vibration feedback, your own data.gov.in API key, and a button to
  clear saved online results.
- **Hindi (हिन्दी).** Every screen is translated; switch in Settings → Language or follow the
  phone's language. English remains the default.
- **Large text mode** for outdoor use, independent of the phone's font setting.
- **Offline banner** on the online tab, and a "cached" marker on results served from the
  on-device cache so a stale answer is never mistaken for a live one.
- **Haptics and motion.** Short vibration on long-press select, save and copy (can be turned
  off), and animated list changes when filters or deletes reorder results.

### Changed
- Validation and import messages are now localised.

## v0.5.0 — a friendlier offline directory

### Added
- **By beat view.** A Search / By beat toggle on the local tab. "By beat" lists every beat per
  branch office with its village count, PIN(s) and SO; expand a beat to see its villages and tap
  one to edit. Beats sort naturally (2, 2A, 10).
- **Import preview.** Importing a spreadsheet now shows what will happen first — rows to add,
  duplicates and blank rows skipped, rows with errors (listed) and, for replace-all, how many
  existing records will be deleted — with Confirm/Cancel before anything is written.
- **Swipe to delete with Undo.** Swipe a village card left to delete; a snackbar offers Undo.
  The Delete button now also offers Undo instead of a confirmation.
- **Multi-select.** Long-press a card to start selecting; tap others, then Delete.
- **Duplicate.** While editing a record, "Duplicate" starts a new record on the same beat/BO/SO/PIN
  with a blank locality — for entering neighbouring villages quickly.
- **First-run screen.** An empty directory shows three clear actions: import a spreadsheet, get
  the blank template, or add the first village by hand.

### Changed
- Search results bold the part of the name that matched, and phonetic hits say
  "Sounds like ‹what you typed›" so it is clear why a differently spelled village appeared.

## v0.4.0 — bridged tabs, recents, detail sheet, India Post palette

### Added
- **Online ⇄ Local bridge.** Online result cards show "N local beat record(s) for this PIN" when
  the offline directory already has rows for that PIN; tapping jumps to the local tab filtered to
  it. Local cards gain a "Look up online" action that runs the PIN through the online lookup.
- **Recent & pinned searches.** Successful online searches are remembered (last 20) and listed
  under the search box; tap to re-run, pin to keep at the top, remove or clear. Stored on-device
  only.
- **Detail sheet.** Tap an online result for the full record with Copy PIN, Share (WhatsApp,
  Gmail…), Show local beats, and **Add to local directory** — which opens the local editor
  pre-filled with locality, BO/SO, district, state and PIN so only the beat number is left to type.

### Changed
- **India Post palette.** Fixed post-box red primary with the logo yellow as accent, a red app bar
  in both light and dark themes, and Material You dynamic colour switched off by default so the
  app looks the same on every device.

## v0.3.1 — online filters, Online tab first

### Changed
- The app now opens on the **Online All-India** tab.
- Online results get **State** and **District** filter chips (same style as the local tab), so a
  common office name that exists in several states can be narrowed without a new search. The
  header shows "N of M post office(s)" while a filter is active.

## v0.3.0 — reliable online lookups

### Fixed
- **Online All-India lookups were failing.** The app relied on a single third-party API
  (`api.postalpincode.in`, rate-limited to 1000 requests/hour per IP and prone to timeouts) and
  also demanded Android's "validated internet" flag, which many Indian mobile networks never
  set — so it forced cache-only requests and reported "You are offline" while connected.

### Changed
- Online lookup now fails over across three sources, in order:
  1. **data.gov.in** — the official *All India Pincode Directory* published by the Department of
     Posts (Open Government Data platform).
  2. **India Post directory mirror** — a static, key-free copy of the same dataset on GitHub Pages.
  3. **postalpincode.in** — community API; the only source that supports search by office name.
  The first source that returns results wins; a source that is down, throttled, or returns an
  unexpected payload is skipped. The result list shows which source answered.
- Connectivity check no longer requires the validated-internet flag.
- Requests carry a proper User-Agent and Accept header; non-JSON responses degrade gracefully.
- Optional: use your own free data.gov.in API key (see README) to avoid the shared sample key's
  throttling.

## v0.2.0 — release tooling (pre-release, debug-signed)

### Changed
- Build and Release workflow now support keystore signing (v1 + v2 + v3) when signing secrets are
  configured. **This build was produced without a keystore and is debug-signed**, exactly like
  v0.1.0, so it installs as an in-place update over v0.1.0 and remains for pilot/sideload use only.
- CI builds the release variant on every pull request so R8 / resource-shrinking issues surface early.

### Unchanged
- App features are identical to v0.1.0; see below.
- Still not verified on physical devices by the maintainers — please report issues.

## v0.1.0 — first pilot build (pre-release)

Offline-first Android app for India Post mail-branch staff.

### Features
- **Online All-India lookup** by PIN code or post-office name via `api.postalpincode.in`, with a 10 MB HTTP cache so recent results work offline.
- **Local Beats directory (offline)** — villages/localities mapped to Beat number, Branch Office, Sub Post Office, District, State and PIN, stored in Room.
- **Phonetic fuzzy search** tuned for Indian place-name spellings (Rampur / Rampoor / Raampur, Bhilwara / Bilwara …), with 250 ms debounce and State / District filter chips.
- **Add / edit / delete** records from a bottom-sheet form with field-level validation.
- **Excel (.xlsx)** blank template download, bulk import with a per-row error report, and backup export shared through WhatsApp / Gmail / Drive.

### Install
1. Download the `.apk` from this release on the Android device (Android 8.0 / API 26 or newer).
2. Open it and allow installation from this source when prompted.
3. On first launch open the ⋮ menu → *Save blank template to device…*, fill it in, then *Import from Excel*.

### Known limitations
- Debug-signed build for pilot use only; not published to Google Play.
- Not yet verified on physical devices by the maintainers — please report issues.
- Online lookups depend on the public `api.postalpincode.in` service.
