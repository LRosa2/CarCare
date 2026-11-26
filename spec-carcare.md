# CarCare — Specification

Personal Android app that reminds the owner each month to enter the odometer and shows, for every
maintenance item of a 2015 Toyota Yaris Hybrid (NHP130, 1NZ-FXE), whether it is due and how many
km and/or how much time remain.

- Status: v0.1 being implemented (interview 2026-11-23, red-team pass resolved with defaults)
- Owner: one user, one phone, one car; sideloaded APK; no server
- Location: `E:\AndroidProj\CarCare`, package `io.github.lrosa2.carcare`

## At a glance

| | |
|---|---|
| Core loop | Monthly notification → enter km → list of items sorted by urgency |
| Due rule | Interval (km and/or months) from last full service, per-dimension pin override, whichever first |
| Due soon | Due date or projected km date within 1 month, or ≤ 500 km left |
| Reminders | One daily alarm at the reminder time (default 19:00) runs every check |
| Data | Room DB on the phone; personal data comes from a JSON import file kept out of git |
| Backup | JSON export/import (same format as the import file) + Android Auto Backup + backup-age nag |
| Stack | Kotlin 2.3.21, AGP 9.4.1, Compose (BOM 2026.09.00), Room 2.8.5, DataStore, kotlinx-serialization |

## Goals and non-goals

Goals
- Never miss a maintenance job that depends on km or time.
- Make entering the km once a month a 10-second job, triggered by a notification.
- Keep the useful reference info from the owner's Keep note (specs, part numbers, procedures, contacts) on the phone, editable.
- Show Toyota's recommendation next to the owner's own interval, with the owner's reasoning.

Non-goals (v1)
- Several cars (the model carries a vehicle id, nothing more).
- Cost tracking, fuel log, statistics, widgets.
- Swiss MFK and the Toyota hybrid battery check (explicitly not tracked).
- Cloud sync or a server. Play Store release.

## User stories

1. On the 1st of the month at 19:00 a notification asks for the km; tapping it opens km entry. If no km is entered within 3 days, it asks once more.
2. The monthly notification also lists items that are overdue or due soon, and says how long ago the last backup was.
3. The home screen shows every item sorted by urgency with status colour, "≈ 770 km left", "due 05.2028", and the expected month for km targets.
4. After a garage visit or DIY session, the owner logs one visit (date, km, several ticked items, a note). A ticked item can be "partial" (e.g. a coolant top-up), which keeps it in history without resetting the due point.
5. The owner can pin a next target (km and/or date) on an item, e.g. "brake fluid at 95,000 km", to line jobs up on purpose.
6. Each item shows a "Toyota reference" line and a free "My note" ("Toyota 15k; I do 5k because of cheaper oil").
7. A "Car info" tab holds editable cards (Vehicle, Fluids, Torque, Parts, Procedures, Tyres & TPMS, Links & garages, To buy) with tappable links and phone numbers.
8. First launch offers "Import" for the personal JSON file; Settings offers Export/Import at any time.

## Due logic

Pure Kotlin in `domain/` (no Android types), covered by JVM unit tests.

**Rule per item**: `intervalKm?`, `intervalMonths?`, `seasonal: MonthDay?`, `pinnedKm?`, `pinnedDate?`, `pinSetOn`.

- **Last full service**: the latest visit (by date, then km) that ticks the item and is not marked partial. It is always derived from history, never stored.
- **Km target**: `pinnedKm` if the pin is active, otherwise last full service km + `intervalKm`.
- **Date target**:
  - Seasonal items: the next `seasonal` day on or after (last swap + 6 months), or from today if never logged.
  - Otherwise: `pinnedDate` if the pin is active, else last full service date + `intervalMonths`.
- **Pins work per dimension** (D3, D19). A km pin replaces only the km interval, and the month interval still counts. Example: brake fluid due at 95,000 km or 10.05.2028, whichever first.
- **When a pin stops counting**: a pin is active until a full service of that item is logged on or after `pinSetOn`. Deleting that visit makes the pin active again. A partial job never clears a pin.
- **Current km** is an estimate: the latest reading plus pace × days since, shown with "≈" (D24).
  - Readings come from monthly entries and from visit km.
  - Pace is km/day from the last reading on or before (latest − 6 months) to the latest reading. When history is shorter, it starts from the earliest reading.
  - Pace is unknown when the span is under 14 days or the km goes down.
- **Projected km date** = today + km left ÷ pace.
- **Status**, most urgent first:
  - `OVERDUE`: the km target is reached, or today ≥ the date target.
  - `NEVER_DONE`: the item has an interval, no history and no pin. It shows as "due now".
  - `DUE_SOON`: ≤ 500 km left, or the date target or projected km date ≤ today + 1 month.
  - `OK`.
  - `NO_SCHEDULE`: the item has no interval and no pin.
- **Reading checks**:
  - A reading lower than one on an earlier (or the same) day is rejected, with a pointer to the old reading so it can be edited.
  - A backdated reading higher than a later one is rejected.
  - More than 1,500 km/day since the previous reading asks for confirmation. A long holiday trip of about 500 km/day passes.

## Item catalogue (seed of the import file)

Intervals come from the owner's notes and practice. Every value is editable in the app. The owner's
pins and service history live only in the import file (D10), not in this repo.
"Toyota reference" is filled from the research in [Toyota reference values](#toyota-reference-values).

| Key | Item | Interval |
|---|---|---|
| engine_oil | Engine oil | 5,000 km / 12 m |
| oil_filter | Oil filter | 10,000 km / 12 m |
| engine_coolant | Engine coolant | 60,000 km |
| inverter_coolant | Inverter coolant | 80,000 km |
| transaxle_fluid | Hybrid transmission fluid (ATF WS) | 80,000 km |
| spark_plugs | Spark plugs | 90,000 km |
| brake_fluid | Brake fluid | 24 m |
| hv_fan_filter | Hybrid battery fan filter | 15,000 km / 12 m |
| cabin_filter | Pollen filter | 15,000 km / 12 m |
| air_filter | Air filter | 60,000 km / 48 m |
| winter_tyres | Winter tyres on | every 15 Oct |
| summer_tyres | Summer tyres on | every 15 Apr |
| brakes_check | Brakes check (pads/discs) | 15,000 km / 12 m |
| battery_12v | 12V battery check | 12 m |
| ac_service | A/C service | 24 m |
| wipers | Wipers | 12 m |

The import was checked against the state the owner expected. One log entry had the wrong year and
was corrected in the import file (D11).

## Toyota reference values

The official NHP130 schedule (toyota-tech.eu) needs a dealer login, so it could not be read.

The main source is the Toyota Netherlands importer's maintenance brochure (Louwman & Parqui, Jan
2018), "based on official factory maintenance". Its extra-items table is headed "Yaris Hybrid 2011":
https://www.louwman.nl/uploads/onderhoud/onderhoudsprijzen/Onderhoudsprijzen-Toyota/onderhoud%20Yaris%20Hybrid%202017.pdf

| Item | Toyota NL schedule | Owner's choice |
|---|---|---|
| Engine oil + filter | Every service: 15,000 km / 1 year | Oil 5,000 km, filter 10,000 km (cheaper oil) |
| Engine coolant | First at 150,000 km, then every 90,000 km | 60,000 km |
| Inverter coolant | First at 240,000 km, then every 90,000 km | 80,000 km |
| Transmission fluid | Inspect every 60,000 km / 4 years (no replacement interval) | Replace every 80,000 km |
| Spark plugs | Every 90,000 km | 90,000 km |
| Brake fluid | 30,000 km / 2 years | 24 months |
| Air filter | 60,000 km / 4 years | Same |
| Pollen filter | 30,000 km / 2 years | 15,000 km / 12 months |
| HV battery fan filter | No interval found; mesh under the rear seat, clean when dusty (forum) | 15,000 km / 12 months |
| Brakes, 12V battery, wipers | Inspected at every service | 12 months (brakes also 15,000 km) |

Secondary sources:
- Toyota UK servicing article: transmission fluid is inspect-only, and the 12V battery is checked at each service.
- UK owners' club guide: 0W-20 oil for the Yaris HSD.

Each reference line names its source, and the owner's interval always wins (D13).

## Data model (Room)

| Table | Columns |
|---|---|
| `vehicle` | id, name, notes |
| `item` | id (key string, e.g. `engine_oil`), vehicleId, name, sortOrder, intervalKm, intervalMonths, seasonalMonth, seasonalDay, pinnedKm, pinnedDate, pinSetOn, toyotaRef, myNote, lastNotifiedStatus |
| `reading` | id, vehicleId, date, km |
| `visit` | id, vehicleId, date, km, note |
| `visit_item` | visitId, itemId, partial |
| `note_card` | id, vehicleId, title, body, sortOrder |

- Dates are stored as ISO `LocalDate` strings.
- Readings used for pace = `reading` ∪ the (date, km) of each `visit`.
- Settings live in DataStore:
  - reminder day of month (1–28), time, interval in months
  - pace window in months, due-soon km and months
  - last export date
  - last monthly prompt date, and whether its re-nag was sent
- Room exports its schema. No destructive migration.

## Reminders

- **Scheduling.** One inexact alarm (`AlarmManager.setAndAllowWhileIdle`, no exact-alarm permission) fires daily at the reminder time and schedules the next day.
  - It is rescheduled on app start, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET` and `TIMEZONE_CHANGED`. Rescheduling is safe to repeat.
  - The next time is computed in the system time zone, so DST keeps 19:00.
- **Daily check**, in a `BroadcastReceiver` with `goAsync`:
  1. **Monthly km prompt.** Fires when today is the reminder day (default 1st) in a reminder month and no reading or visit was entered in the last 7 days. The notification:
     - lists overdue and due-soon items and the backup age
     - has a tap target that opens km entry.
  2. **Re-nag.** 3 days after a prompt, if still no reading since the prompt, notify once more.
  3. **Status changes.** For each item whose status became DUE_SOON or OVERDUE (compared with `lastNotifiedStatus`), post one grouped "Maintenance due" notification. Store the new status, so each change notifies once per service cycle.
- Two notification channels: "Km reminder" and "Maintenance due". PendingIntents are explicit and `FLAG_IMMUTABLE`.
- **Reliability (D21):**
  - Home shows a warning banner when notifications are blocked (Android 13+ permission or a disabled channel).
  - Settings has a "Reminder health" section: notification permission, battery optimisation (button to the app's battery settings), and "pause app if unused" (button to the app's settings).

## Import, export, backup

- **JSON format** (`formatVersion: 1`) holds: vehicle, items with rules, pins, Toyota reference and note, readings, visits with item ticks, note cards, settings.
  - Import replaces everything after a confirmation; there is no merge.
  - A file with a newer `formatVersion` is refused with a message.
- **Personal data stays out of git** (D10, D20):
  - The owner's file lives at `private/carcare-import.json`, which is git-ignored.
  - The APK ships only a generic default item list (names and intervals, no VIN, pins or history), used when the owner starts without importing.
- **Export** uses the system "Create document" picker (`carcare-backup-YYYY-MM-DD.json`) and records the export date.
- **Auto Backup** is on (database and DataStore), so a new phone restores data when Google backup is enabled.
- **Backup age (D22):** the monthly notification states "last backup N days ago". Home shows a banner when the last export is older than 90 days.

## Screens (Compose, Material 3, English UI, Swiss number/date format)

1. **Home**:
   - Header: estimated km ("≈ 83'400 km, last entered 83'000 on 10.11.2026") and an "Enter km" button.
   - Banners: notifications blocked, backup old.
   - Item list sorted by status, then by nearest due. Each row shows a status chip, the item name, km left and/or date, and the expected month.
2. **Enter km**: number field (digits only), date (default today). Errors come from the reading checks.
3. **Item detail**:
   - Rule editor: interval km and months, seasonal day, pin km and date, with a clear-pin action.
   - Toyota reference, My note.
   - History of visits that ticked this item, marked "partial" where it applies.
4. **Log visit**: date, km, note, checklist of all items.
   - Each row shows its status, and a "partial" toggle once ticked.
   - Items due within 2,000 km or 2 months are marked "coming up", to help bundle jobs (D25).
   - Ticking "Engine oil" does not auto-tick "Oil filter".
5. **History**: visits and readings, newest first. Tap to edit or delete.
6. **Car info**: note cards; tap to edit (title and body); links and phone numbers are clickable.
7. **Settings**:
   - Reminder day, time and interval months; pace window; due-soon thresholds.
   - Export, Import.
   - Reminder health.

## Security and privacy

- **No network permission**, no analytics.
- **Personal data:**
  - VIN, TPMS IDs and the Keep-note cards exist only on the phone, in the git-ignored import file, and in exports the owner saves.
  - The service history (km and dates) is not in the repo either: this spec and the tests use made-up values.
- **Auto Backup** sends the database to the owner's Google account. That is accepted: it is the owner's own data, and it is the only off-phone copy besides exports.
- **Exported surface**: only the launcher activity is exported. The boot/time receivers are exported only for their system broadcasts.

## Testing

- **JVM unit tests (JUnit 6)**:
  - due calculator: km-only, time-only, whichever-first, never-done, pins, projection, seasonal, no schedule
  - odometer: pace window, estimate, low, high and suspicious readings
  - import/export round trip
  - a smoke test that the owner's import file decodes (runs only where the private file exists)
- **Manual check on the emulator** (`Pixel_7_API_34`, driven through adb):
  - import the file
  - the home list shows the expected statuses
  - enter km, log a visit
  - a reminder notification can be triggered from the debug menu

## Decisions Log

| ID | Topic | Decision | Rationale | Source | Date |
|---|---|---|---|---|---|
| D1 | Name | CarCare, folder `E:\AndroidProj\CarCare`, package `io.github.lrosa2.carcare` | Generic name still fits if the car changes | Interview | 2026-11-23 |
| D2 | Scope | One car; tables carry a vehicle id | Simple now, a second car needs no migration | Interview | 2026-11-23 |
| D3 | Due rule | Interval from last full service + optional pin override; whichever first | Owner lines up jobs on purpose (e.g. brake fluid and coolant at the same km) | Interview | 2026-11-23 |
| D4 | Oil | Two items: oil 5,000 km / 12 m, filter 10,000 km / 12 m | Matches the owner's alternating log | Interview | 2026-11-23 |
| D5 | Due soon | Due or projected date within 1 month, or ≤ 500 km left; pace over the last 6 months | First chosen as 1,000 km / 1 month; changed to the projected date because it adapts to pace | Interview | 2026-11-23 |
| D6 | Reminder | Fixed day/time (default 1st, 19:00), interval in months configurable, one re-nag after 3 days, tap opens km entry; daily check for due changes | Owner wants a monthly prompt and not to miss it | Interview | 2026-11-23 |
| D7 | Items | Base items + cabin/air filters + seasonal tyre swap (with TPMS sets) + yearly inspections; no MFK, no Toyota battery check | Owner's selection | Interview | 2026-11-23 |
| D8 | Logging | Visit with several items + note; per-item partial flag | Log shows multi-job visits and a partial coolant change | Interview | 2026-11-23 |
| D9 | Reference info | Editable free-text cards, pre-filled from the Keep note | New kinds of info need no app update | Interview | 2026-11-23 |
| D10 | Personal data | JSON import file outside git; export/import in the same format; Auto Backup | Repo may go public later | Interview | 2026-11-23 |
| D11 | History fix | One log entry's year corrected in the import file; the other suspicious entry kept | Owner confirmed | Interview | 2026-11-23 |
| D12 | Seed intervals | Item catalogue table above | Owner approved the table | Interview | 2026-11-23 |
| D13 | Toyota reference | Per item: Toyota reference line + free note; owner's interval wins | Owner knows when to deviate (cheaper oil, more often) | Interview | 2026-11-23 |
| D14 | Stack | Sibling toolchain (Kotlin 2.3.21, AGP 9.4.1, Compose, Room, DataStore, serialization); minSdk 26, targetSdk 36 | Proven on this machine | Interview | 2026-11-23 |
| D15 | Distribution | Sideloaded APK with the shared release key; debug build has `.debug` suffix; private repo LRosa2/CarCare if published | Same as sibling projects | Interview | 2026-11-23 |
| D16 | Tests | Due calculator and odometer logic pure Kotlin with JVM tests | Core logic must be right | Interview | 2026-11-23 |
| D17 | Language | English UI, Swiss date/number format (dd.MM.yyyy, 123'456) | Owner's notes are in English; lives in Switzerland | Interview | 2026-11-23 |
| D18 | Current km | No approximate reading is seeded; the app asks for the real km after import | The log already gives an estimate; a guessed reading would distort the pace | Red Team | 2026-11-23 |
| D19 | Pin semantics | Pin replaces only its own dimension; active until a full service on/after `pinSetOn`; deleting that visit restores it; partial never clears | Brake fluid must keep its 24-month clock beside its km pin | Red Team | 2026-11-23 |
| D20 | APK vs import file | Import file is the full source (items, pins, cards, history, settings) and replaces everything; APK only has a generic default item list | No duplicate items, no personal data in git/APK | Red Team | 2026-11-23 |
| D21 | Reminder reliability | Notifications-blocked banner + "Reminder health" settings section (permission, battery, unused-app pause) | A silently broken reminder defeats the app | Red Team | 2026-11-23 |
| D22 | Backup | Manual export + Auto Backup + backup age in the monthly notification + banner after 90 days | Cheap protection against phone loss; no folder-permission plumbing | Red Team | 2026-11-23 |
| D23 | Bad readings | Lower → rejected; backdated higher than a later one → rejected; > 1,500 km/day → confirm | Catches typos while allowing long trips of about 500 km/day | Red Team | 2026-11-23 |
| D24 | Current km estimate | Status and km left use the estimated km (last reading + pace), shown with ≈ | Monthly readings are up to ~1,000 km old | Red Team | 2026-11-23 |
| D25 | Repeat / bundling | Overdue and due-soon items are listed in every monthly prompt; the Log-visit checklist marks items coming up within 2,000 km / 2 months | No silent overdue items, no extra notification noise; helps bundle jobs | Red Team | 2026-11-23 |
| D26 | Scheduling | One daily inexact alarm (setAndAllowWhileIdle) at the reminder time, rescheduled on boot/update/time change/app start; WorkManager dropped | A 24h periodic worker can run at night; an alarm keeps 19:00 without the exact-alarm permission | Red Team | 2026-11-23 |
| D27 | Seasonal never logged | Next occurrence from today (not overdue) | It is November: winter tyres are surely on; no fabricated swap date | Red Team | 2026-11-23 |
| D28 | Alarm window | Android gives the inexact alarm a window of up to 1 h (seen on API 34: 19:00 +1h), so the reminder arrives between 19:00 and ~20:00 | Exact alarms need a special permission; a reminder an hour late is harmless | Interview | 2026-11-23 |
| D29 | First prompt after import | Import marks the current month's reminder day as handled and asks for the km right away; the first monthly prompt is the next reminder day | Avoids a second km prompt on the evening of the import | Interview | 2026-11-23 |

## Dependency Graph & Implementation Order

```
domain (DueCalculator, Odometer) ──┐
                                   ├─> data (Room, repository, settings) ─┬─> backup (JSON import/export)
                                   │                                      ├─> reminders (alarm, receiver, notifications)
                                   │                                      └─> ui (Home, Enter km, Item, Log visit, History, Car info, Settings)
private/carcare-import.json ───────┴── (built from the Keep note; consumed by backup import)
```

1. Domain logic + unit tests. Done.
2. Room schema, repository, DataStore settings. Done.
3. JSON format, import/export, the owner's import file, round-trip test, import-file smoke test. Done.
4. UI screens. Done.
5. Reminders: alarm scheduling, receivers, notifications, reminder health. Done.
6. Build, install on the emulator, import, check the expected state, trigger a reminder. Done 2026-11-23 on `Pixel_7_API_34`.
   - 54 JVM tests green.
   - Checked on the emulator, release build:
     - onboarding import via the system picker; the expected statuses
     - the km checks (a low reading is rejected)
     - logging a visit (oil back to OK); a partial tick keeps the item overdue
     - editing an item's interval
     - Car info and History
     - **real alarm chain**: with the clock moved to the reminder day at 18:58, the app unopened, both notifications arrived at 19:00 and the next alarm was set for the next day
     - re-nag 3 days later
     - alarm restored after reboot
     - notification tap opens Enter km
     - export → identical to the import file (items, pins, partial ticks, seasonal days, all visits, cards) → import back gives the same list
7. Not yet done:
   - install on the real phone; phone-maker battery savers may still delay the alarm there
   - editing and deleting a single km reading, and editing a card (code exists, not clicked through)
