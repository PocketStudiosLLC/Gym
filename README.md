# Gym App

Android workout logger. See [spec.md](spec.md) for the full product spec.

## Build & run

Requires JDK 17 (pinned in `mise.toml`) and the Android SDK (`local.properties` → `sdk.dir`).

```sh
./gradlew assembleDebug                      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                       # install on a connected phone/emulator
```

## Code map

| Path | What |
|---|---|
| `data/` | Room entities + DAO (`GymDatabase.kt`), `GymRepository` (day view, sets, superset rules + undo), DataStore settings, photo storage |
| `timer/` | Workout stopwatch: `StopwatchController` (state, persisted across process death, auto-start on first set of today) and `StopwatchService` (foreground notification) |
| `backup/` | `.zip` export / import (Replace or Merge) |
| `ui/day/` | Home screen: day pager (swipe left = previous day), entry cards, set dialogs, drag-to-superset (`SetDragState.kt`) |
| `ui/exercise/` | Exercise picker and New/Edit Exercise |
| `ui/analytics/` | 1 Day / 1 Week charts |
| `ui/settings/` | Dark/Light, 8 theme colors, export/import |
