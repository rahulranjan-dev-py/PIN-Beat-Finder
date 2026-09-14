# PIN Beat Finder

Offline-first Android app for India Post mail-branch operations. Staff can look up any PIN code or
post office across India when online, and — with or without a network — find which **Beat** and
**Branch Office (BO)** serve a village even when the name is misspelled.

## Features

| Area | What it does |
| --- | --- |
| **Online All-India** | Live lookup against `api.postalpincode.in` by PIN or post-office name, with a 10 MB OkHttp cache so recent answers work offline. |
| **Local Beats (offline)** | Room-backed directory of localities → Beat / BO / SO / PIN. Debounced (250 ms) search combines substring, exact beat/PIN and **phonetic** matching (Double Metaphone with an Indian-transliteration pre-normaliser). State and District filter chips. |
| **CRUD** | Add/edit via a modal bottom sheet, delete with confirmation. Validation is shared with the importer. |
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

### Excel template

Sheet `Beat Directory`, header row (columns marked `*` are mandatory):

`Locality/Village Name*` · `Branch Office (BO)*` · `Sub Post Office (SO)*` · `Beat Number*` ·
`District*` · `State*` · `Pincode*` · `Remarks`

Header matching on import is tolerant (case, punctuation, `*`, and `(BO)`-style hints are ignored).
PINs must match `^[1-9][0-9]{5}$`; numeric cells such as `110001` or `3.0` are normalised. Invalid
rows are listed in the import report and skipped; valid rows are inserted in one Room transaction.

## Building

```bash
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # JVM unit tests (phonetics, validation, Excel codec, DTOs)
```

Requires JDK 17 and the Android SDK (platform 35). CI (`.github/workflows/android.yml`) runs the
unit tests, lint and a debug build on every push.

## Verification status

- The pure-Kotlin layers (phonetic engine, validators, `ExcelCodec`, API DTO parsing) were
  compiled with Kotlin 2.2.21 against the real library versions and all 25 unit tests pass on the JVM.
- The Android build itself (AGP, Room/KSP, Compose) has **not** been run in the authoring
  environment because Google's Maven repository was unreachable there; run the Gradle commands
  above (or let CI do it) before shipping.
- FastExcel 0.18.4, Aalto 1.3.3 and stax2-api ship Java 8 bytecode with no Java 9+ API calls;
  commons-compress's multi-release Java 9 classes are excluded in `packaging`. `javax.xml.stream`
  is provided by the bundled `stax-api` jar because Android does not include StAX.
