# Changelog

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
