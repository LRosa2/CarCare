# CarCare

Personal Android app for the maintenance of a 2015 Toyota Yaris Hybrid. Once a month it asks
for the odometer reading. For every maintenance item it then shows whether the item is due, and
how many km or how much time remain. The full specification is in [spec-carcare.md](spec-carcare.md).

## Install on the phone

1. Copy `app/build/outputs/apk/release/app-release.apk` to the phone and open it.
   - Allow installing from that source when Android asks.
   - The APK is signed with the shared key of the sibling projects.
2. Copy `private/carcare-import.json` to the phone, e.g. into Downloads.
3. Open CarCare, tap **Import data file**, pick the JSON, then allow notifications.
4. Enter today's odometer when asked.
5. Optional: in **Settings → Reminder health**, set the battery to *Unrestricted* and turn off
   *Pause app activity if unused*, so Android never holds the reminder back.

## Build

Same toolchain as the sibling projects: JDK 21, AGP 9.4.1, Gradle 9.6.0, Kotlin 2.3.21.

```bash
./gradlew :app:testDebugUnitTest      # JVM tests (due logic, reminders, backup format)
./gradlew :app:assembleDebug          # debug build, installs next to release (.debug suffix)
./gradlew :app:assembleRelease        # signed when keystore.properties exists
```

## Personal data

The following hold the VIN, service history and TPMS IDs, and are git-ignored. Keep them out of
any public copy of the repo:

- `private/carcare-import.json`
- exported `carcare-backup-*.json` files

The APK itself contains no personal data, only a generic default item list.

The service history is not in the repo: the tests use made-up km and dates. The test
`BackupCodecTest."the private import file decodes and every item gets a status"` reads the
private file when it exists, and is skipped otherwise.

## Layout

| Package | What |
|---|---|
| `domain` | Pure Kotlin due logic: `DueCalculator`, `Odometer`, `StatusEngine`, `ReminderPolicy` |
| `data` | Room database, DataStore settings, `CarRepository`, bundled `DefaultCatalogue` |
| `backup` | JSON format (`BackupFile`, `BackupCodec`) and file I/O |
| `reminders` | Daily alarm, receivers, notifications |
| `ui` | Compose screens |
