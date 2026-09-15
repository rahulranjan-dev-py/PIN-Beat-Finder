# Changelog

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
