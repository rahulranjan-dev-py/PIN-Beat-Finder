# PIN Beat Finder

Offline-first Android app for India Post mail-branch operations. Staff can look up any PIN code or
post office across India when online, and — with or without a network — find which **Beat** and
**post office (BO/SO/HO/GPO/IDC)** serve a village even when the name is misspelled.

## Features

| Area | What it does |
| --- | --- |
| **Online All-India** | Live lookup by PIN or post-office name with automatic failover across the official Department of Posts directory on data.gov.in, a key-free mirror of it, and `api.postalpincode.in`; 10 MB OkHttp cache so recent answers work offline. |
| **Local Beats (offline)** | Room-backed directory of localities → Beat / office (type + name) / account office / PIN. Debounced (250 ms) search combines substring, exact beat/PIN and **phonetic** matching (Double Metaphone with an Indian-transliteration pre-normaliser). State and District filter chips. |
| **CRUD** | Add/edit via a modal bottom sheet: office type dropdown (GPO/HO/IDC/SO/BO), Office Name suggestions from the built-in directory as you type, and a **Fetch** button that lists every office under the typed PIN (with how many local beats/villages each already has) so one tap fills type, name, account office, district and state. Ticking several offices in the picker enters them one after another ("Save & next"). Account Office suggests SO/HO/GPO offices from the directory. The By-beat view's **Office types** chip re-types every record of an office in one go and flags offices with mixed types; each beat card can share that beat or the whole office as `.xlsx`. Delete with confirmation. Validation is shared with the importer. |
| **Excel** | Download/share a blank `.xlsx` template, bulk-import via the Storage Access Framework (append or replace-all, row-level validation report), and export/share a backup through the Android share sheet via `FileProvider`. |

## Tech stack

- Kotlin 2.2, Jetpack Compose + Material 3, target SDK 35 / min SDK 26
- MVI-style unidirectional state (`State` / `Intent` / `Effect`) with `StateFlow` + `ViewModel`
- Room 2.8 (KSP) with pre-computed phonetic key columns and compound indexes
- Retrofit 2 + OkHttp 4 + kotlinx.serialization
- Apache Commons Codec (Double Metaphone)
- FastExcel writer + reader (POI-free, streaming; StAX API jar bundled for Android)

## Project layout

```
app/src/main/kotlin/com/pinbeatfinder/
├── core/phonetic/   IndianPhoneticNormalizer, PhoneticSearchEngine
├── core/util/       PinCodeValidator, BeatDraftValidator, AppResult
├── domain/model/    BeatRecord, BeatDraft, PostOffice, search filters
├── data/local/      BeatDirectoryEntity, BeatDirectoryDao, BeatFinderDatabase (Room)
├── data/remote/     PostalApiService, DTOs, NetworkModule (OkHttp cache + offline policy)
├── data/excel/      ExcelCodec (pure JVM), ExcelSyncManager (SAF + FileProvider)
├── data/repository/ BeatDirectoryRepository, PostalLookupRepository
├── di/              AppContainer (manual, lazy DI)
└── ui/              MainScreen (tabs), online/, local/ (screen, editor sheet, ViewModel)
```

### How phonetic search works

1. `IndianPhoneticNormalizer` folds transliteration variants before encoding:
   `ee→i`, `oo→u`, `aa→a`, aspirates (`bh→b`, `dh→d`, `kh→k`, `th→t`, `ph→f`…), `sh→s`, `w→v`,
   `z→j`, doubled consonants, dotted abbreviations (`B.O.`), postal suffix noise (`PO`, `BO`) and
   common toponym suffixes (`-pura/-puram → -pur`).
2. `PhoneticSearchEngine` encodes each token with Double Metaphone (code length 6) and stores
   `phoneticPrimary` / `phoneticAlternate` on the row **at write time**.
3. `BeatDirectoryDao.searchCandidates` retrieves an indexed candidate set with bound-parameter
   `LIKE` patterns (substring on `localityName`, prefix on the NOCASE-collated phonetic columns,
   exact match on `beatNumber` / `pincode`), optionally narrowed by state/district.
4. The repository re-ranks candidates in memory (exact > prefix > contains > phonetic-equal >
   phonetic-prefix > Jaro–Winkler), so the whole query stays well under 300 ms.

### Bundled All-India directory

`app/src/main/assets/india_post_directory.bin` (gzip TSV, ≈3.3 MB; not named `.gz` because AGP would decompress and rename it at packaging) is the Department of Posts
*All India Pincode Directory* from data.gov.in, reduced to the columns the app shows plus
precomputed phonetic keys, merged with the India Post **facility master** (the `OfficeMaster`
table of the CSI *SPM Help* tool's `SPM.db`) which contributes each office's **account
office** (the SO/HO it reports to) and ~7,000 offices missing from data.gov.in. The join is on
(PIN, normalised office name); 95.6 % of data.gov.in rows get an account office.
`DirectorySeeder` loads it into a separate Room database
(`india_post_directory.db`) once per asset version; `IndiaPostDirectoryRepository` answers PIN
and name lookups from it, and `PostalLookupRepository` only races the online providers when the
bundle has no match. To refresh the snapshot, download the latest CSV from data.gov.in, export
`OfficeMaster` (fid, facilitydesc, pincode, ftype, postatus, repofficename) as TSV, and rerun
the generator (a small Kotlin tool that applies `PhoneticSearchEngine` to every office name and
does the merge), then bump `version` in `india_post_directory.json`.

Data attribution: the pincode directory is published by the Department of Posts on data.gov.in
under the Government Open Data License – India (GODL); the facility master is India Post's own
operational reference data. Neither source carries district/state for offices absent from
data.gov.in, so those rows take the majority district/state of their PIN.

### Online lookup providers

`PostalLookupRepository` queries every applicable provider **in parallel** and returns the
fastest non-empty answer (the others are cancelled); Settings → *Online data sources* shows each
provider's last status and latency. Order below is the tie-break only:

| Order | Source | Lookup by | Notes |
| --- | --- | --- | --- |
| 1 | `api.data.gov.in` — *All India Pincode Directory* (Department of Posts) | PIN | Needs an API key. The public sample key is baked in but is shared and throttled. |
| 2 | `aniket-thapa.github.io/india-pincode-api` — static mirror of the same dataset | PIN | No key, no rate limit. |
| 3 | `api.postalpincode.in` — community API | PIN or office name | 1000 req/hour/IP; only source for name search. |

Payloads are parsed leniently (case-insensitive keys, several aliases per field) so an upstream
schema tweak degrades to "no results from this source" rather than a crash. The results list shows
which source answered.

**Own data.gov.in key (recommended for regular use).** Register free at https://data.gov.in, then
build with either `DATA_GOV_IN_API_KEY=<key>` in the environment or `dataGovInApiKey=<key>` in
`gradle.properties` / `-PdataGovInApiKey=<key>`. The key is compiled into `BuildConfig`.

### Settings, language and text size

The gear icon opens Settings: default tab, theme, text size, language (English / Hindi / system),
vibration feedback, an optional personal data.gov.in API key (applies immediately, no rebuild),
and "Clear saved online results". Language uses AppCompat's per-app locale API, so it works on
every supported Android version and is remembered across launches. All user-facing text lives in
`res/values/strings.xml` with the Hindi translation in `res/values-hi/`.

### Excel template

Sheet `Beat Directory`, header row (columns marked `*` are mandatory):

`Locality/Village Name*` · `Office Type*` · `Office Name*` · `Account Office (SO/HO)` ·
`Beat Number*` · `District*` · `State*` · `Pincode*` · `Remarks`

`Office Type` is one of `GPO`, `HO`, `IDC`, `SO`, `BO` (spellings such as "Branch Office" or
"B.O" are accepted). `Office Name` is the serving office without its type suffix. Files made
with the pre-0.10 template (`Branch Office (BO)` / `Sub Post Office (SO)` columns) still import:
the BO becomes a `BO` record with the SO as account office, and an SO-only row becomes an `SO`
record.

Header matching on import is tolerant (case, punctuation, `*`, and `(BO)`-style hints are ignored).
PINs must match `^[1-9][0-9]{5}$`; numeric cells such as `110001` or `3.0` are normalised. Invalid
rows are listed in the import report and skipped; valid rows are inserted in one Room transaction.

### Debug signing

`app/debug.keystore` is committed on purpose (standard `android`/`androiddebugkey` credentials,
not a secret). Without it every CI runner would generate its own debug key and Android would
refuse to install one build over another. Release signing is unaffected.

### Updates and crash reports

Sideloaded builds cannot rely on a store, so `UpdateChecker` reads the public GitHub Releases
feed (`BuildConfig.GITHUB_REPO`) and surfaces newer tags with a download link; checks are
throttled to 6 hours and a dismissed tag stays hidden. `CrashReporter` installs an
uncaught-exception handler that writes reports to `filesDir/crashes/` (last 5 kept) and shares
them through the `FileProvider`; nothing leaves the device unless the user shares it.

### Tests

`./gradlew testDebugUnitTest` runs both the pure JVM tests and the Robolectric database tests
(`app/src/test/.../robolectric/`), which open in-memory Room databases against Robolectric's SQLite.
`BeatFinderDatabaseMigrationTest` builds a v1 database file by hand and opens it through the
current schema, so a broken migration fails on CI rather than on a phone.

## Building

```bash
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # JVM unit tests (phonetics, validation, Excel codec, DTOs)
```

Requires JDK 17 and the Android SDK (platform 35). CI (`.github/workflows/android.yml`) runs the
unit tests, lint and a debug build on every push.

## Releases

Installable builds are published on the repository's
[Releases page](https://github.com/rahulranjan-dev-py/PIN-Beat-Finder/releases). Each release
carries a debug-signed `pin-beat-finder-<version>-debug.apk` (Android 8.0+), a `SHA256SUMS.txt`
and the notes from [`CHANGELOG.md`](CHANGELOG.md).

To cut a release:

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts` and add a section to `CHANGELOG.md`.
2. Either push a tag (`git tag v0.2.0 && git push origin v0.2.0`) or run the **Release** workflow
   from the Actions tab with the version. The workflow runs the unit tests, builds the APK and
   publishes the GitHub Release.

### Release signing

`assembleRelease` / `bundleRelease` produce store-ready artifacts when a keystore is configured;
otherwise they fall back to the debug key (installable for testing, printed as a warning, never
suitable for Google Play). Credentials are resolved from environment variables first, then from a
git-ignored `keystore.properties`.

**One-time setup**

1. Generate a keystore and keep it somewhere safe (losing it means you can never update the app on
   Google Play):
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias pinbeatfinder \
       -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **CI (GitHub Actions)** – add repository secrets under *Settings → Secrets and variables → Actions*:

   | Secret | Value |
   | --- | --- |
   | `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
   | `KEYSTORE_PASSWORD` | keystore password |
   | `KEY_ALIAS` | `pinbeatfinder` (or the alias you chose) |
   | `KEY_PASSWORD` | key password (optional if same as the keystore password) |

   With these present the Release workflow publishes `pin-beat-finder-<version>-release.apk`
   and a `.aab` for Play Console, and honours the *pre-release* input. Without them it publishes a
   debug-signed APK and forces pre-release.
3. **Local builds** – copy `keystore.properties.example` to `keystore.properties`, fill it in, then
   run `./gradlew assembleRelease`. The file and `*.jks` are git-ignored.

The env-var names the build script reads are `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`
and `KEY_PASSWORD`.

## Verification status

- The pure-Kotlin layers (phonetic engine, validators, `ExcelCodec`, API DTO parsing) were
  compiled with Kotlin 2.2.21 against the real library versions and the unit tests pass on the JVM.
- The Android build itself (AGP, Room/KSP, Compose) has **not** been run in the authoring
  environment because Google's Maven repository was unreachable there; run the Gradle commands
  above (or let CI do it) before shipping.
- FastExcel 0.18.4, Aalto 1.3.3 and stax2-api ship Java 8 bytecode with no Java 9+ API calls;
  commons-compress's multi-release Java 9 classes are excluded in `packaging`. `javax.xml.stream`
  is provided by the bundled `stax-api` jar because Android does not include StAX.
