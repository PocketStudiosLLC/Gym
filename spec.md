# Gym App — Product & Technical Spec

> **Status:** Draft v0.3 · **Platform:** Android · **Working title:** "Gym App"
>
> Anything marked **[ASSUMPTION]** is a decision that hasn't been confirmed yet. See [§12 Open Questions](#12-open-questions).

---

## 1. Overview

Gym App is an offline-first Android app for logging strength workouts. For each exercise the user records sets, reps, weight (in pounds) and the target muscle group. The home screen is **today's workout**: a list of the exercises done that day, each of which expands to show its sets. To make a superset, the user drags a set from one exercise onto a set of another. All workout data can be exported to a file and imported again, so nothing is lost when the user changes phones or factory-resets.

### 1.1 Goals
- Log a set quickly in the middle of a workout, with as few taps as possible.
- Show the day's workout clearly, supersets included.
- Let users create their own exercises, with an optional photo used as the exercise's icon.
- Use dark mode by default, with 8 accent colors and a light mode option.
- Keep the user's data portable through export and import.

### 1.2 Non-Goals (v1)
- Accounts, cloud sync, social features.
- Pre-built workout programs or plans.
- Kilograms or any other unit besides pounds.
- Set fields other than reps and weight (no RPE, notes or warm-up flags).
- Wear OS or tablet-specific layouts.

---

## 2. Glossary

| Term | Meaning |
|---|---|
| **Exercise** | A reusable definition in the user's library: name, one or more muscle groups, optional photo. Example: "Barbell Bench Press". |
| **Exercise Entry** | One exercise done in one day's workout. Holds that day's sets. |
| **Set** | One set inside an entry: reps × weight (lb). |
| **Superset** | Two or more exercise entries done back-to-back, starting at a given set in each and continuing for every set after it. |
| **Day / Workout** | Every entry logged on one calendar date. |
| **Active Timer** | The workout stopwatch, shown as a card at the top of the day's list while it runs. |

---

## 3. Navigation & Screen Map

```
App launch
  └── Day Screen (opens on today)
        ├── swipe left → previous day / swipe right → next day
        ├── Active Timer card (stopwatch, when running): pause/resume, stop
        ├── Exercise Entry (tap → expand/collapse set list)
        │     └── Set row (tap → edit/delete; long-press + drag → superset)
        └── FAB (+)  →  Action Menu
              ├── New Exercise   → New Exercise dialog
              ├── Add Exercise   → Exercise Picker
              ├── Gym Timer      → starts the stopwatch (Active Timer card)
              ├── Analytics      → Analytics screen
              └── Settings       → Settings screen (theme, export/import)
```

---

## 4. Screens

### 4.1 Day Screen (Home)

The app always opens to the current date.

**Top app bar**
- Shows the date, e.g. "Mon, Nov 16". When the date isn't today, a "Today" button jumps back to the current date.
- **Swiping left** goes to the **previous** day. **Swiping right** goes to the **next** day. The days scroll as pages (a `HorizontalPager`), and the content follows the finger.
- **[ASSUMPTION]** Tapping the date opens a date picker for jumping to any day.
- **[ASSUMPTION]** The user can swipe to future days, but a future day only shows the empty state until something is logged.
- Past days can be viewed and edited the same way as today.

**Active Timer card**
- When a timer is running (see §4.7), it's pinned as the first item above the exercise list.
- It shows the elapsed time (`h:mm:ss`) in large digits, with **Pause/Resume** and **Stop** buttons.
- **Stop** ends the timer and removes the card.
- The card appears on whichever day is being shown, because the timer is global and not tied to a day.

**Exercise list**
- Rows are ordered by when each entry was added. The user can reorder rows with *Move up* / *Move down* in the row's long-press menu. A drag handle wasn't used, because long-press-and-drag is reserved for supersets.
- Each row shows:
  - **Left:** exercise name (primary text) and its muscle groups as small chips, e.g. `Chest` `Triceps`.
  - **Summary line:** set count and best set, e.g. "4 sets · top 225 lb × 5".
  - **Right:** the exercise photo as a rounded-square thumbnail. With no photo, show the exercise's **initials** (up to 2 letters, from the first letters of the first two words, e.g. "Barbell Bench Press" → "BB") on a tile filled with the theme color.
  - **Superset color coding:** each superset group gets its own color. Every entry in a group has a colored bar on its left edge and a small "Superset with *Exercise B*" label in that color. Sets that are in the superset are tinted with the same color (see §4.2).
- **Tap a row:** the row expands to show its sets (see §4.2). Tap again to collapse. **[ASSUMPTION]** More than one row can be expanded at a time.
- **Long-press a row:** context menu with *Delete entry* and *Remove from superset*.

**Empty state:** "No exercises logged for this day. Tap + to add one."

**FAB:** a "+" floating action button, bottom-right.

### 4.1a Gym / Food tabs

Two tabs sit centered at the top of the home screen: **Gym** (selected on launch; the Day screen described here) and **Food**. A **Settings** gear sits in the top-right corner of both tabs.

**Swiping** moves along one line of pages: **Food ← Gym (today) → yesterday → the day before → …**
- From Gym today, swipe right to open Food. From Food, swipe left (or press Back) to return to Gym.
- From Gym, each left swipe goes back one day; right swipes come forward again, as far as today.
- There are no future days. The date picker only offers today and earlier.
- Food currently shows today only.

### 4.2 Expanded Exercise — Set List

When a row is expanded, its sets appear inline below it:

```
┌─────────────────────────────────────────────┐
│▌Barbell Bench Press            [photo]      │
│▌Chest · 4 sets · Superset with Pull-ups     │
├─────────────────────────────────────────────┤
│  Set 1    135 lb × 10                       │
│  Set 2    185 lb × 8                        │
│▌ Set 3    205 lb × 6      (superset color)  │
│▌ Set 4    205 lb × 5      (superset color)  │
│  [ + Add Set ]                              │
└─────────────────────────────────────────────┘
```

- Each set row shows the set number, weight in lb and reps.
- **Add Set row:** below the sets there are a **weight (lb)** box, a **reps** box and an **Add Set** button. Each tap of Add Set logs one set using the values in the boxes. The values stay after adding, so the same set can be logged several times by tapping again. The boxes are prefilled from the exercise's last set. If the entry is in a superset, the new set is automatically part of it too.
- **Tap a set:** opens a small edit dialog with weight (lb) and reps on numeric keypads, plus a **Delete** button. Deleting shows an Undo snackbar.
- Sets aren't deleted by swiping, because a horizontal swipe changes the day.
- **Superset color:** sets in a superset get a color-coded left bar and tint, matching their group's color on the main list.

### 4.3 Superset via Drag & Drop

**Gesture**
1. Long-press a set row in Exercise A. The set lifts and follows the finger, with a haptic tick.
2. Drag it over a set row in Exercise B. The target set highlights. The list auto-scrolls near the top and bottom edges.
3. Release. A superset is created that starts at **set *i* of A** and **set *j* of B**.

Day swiping is turned off while a set is being dragged.

**Behavior**
- Sets of A from set *i* onward, and sets of B from set *j* onward, belong to the superset. So does every set added later to either entry. Earlier sets stay standalone (straight sets done before the superset began).
- The group is given the next unused color from the superset palette (§5). Both entries show it.
- **Adding a third exercise:** dragging a set from Exercise C onto any set already in a superset adds C to that group (a tri-set or giant set) from that set onward.
- **Undo:** after a drop, a snackbar reads "Superset created · Undo".
- **Ending a superset:** long-press the entry and choose *Remove from superset*. When a group drops to fewer than 2 members, it's removed.
- **Invalid drops:** dropping onto a set of the same exercise, or onto empty space, does nothing and the set animates back.
- **Collapsed rows as targets:** a drop target must be an expanded row. **[ASSUMPTION]** Hovering over a collapsed row for about 600 ms auto-expands it so its sets can be targeted.

### 4.4 FAB Action Menu

Tapping "+" opens a speed-dial menu with these items:

Settings is not in the menu; it's the gear icon at the top right.

**Gym tab** (top to bottom; Start Exercise sits closest to the + button):

| Item | Action |
|---|---|
| New Exercise | Opens the New Exercise dialog (§4.5) |
| Analytics | Opens Analytics (§4.8) |
| Start Routine | Opens Start Routine (§4.11) |
| Start Exercise | Opens the Exercise Picker (§4.6) |

**Food tab** (§4.10), top to bottom: Analytics, Add Water, Add Meal, Add Weight.

Tapping outside the menu, or pressing Back, dismisses it.

### 4.5 New Exercise (dialog / full-screen sheet)

Creates a new **Exercise** in the library.

| Field | Type | Required | Notes |
|---|---|---|---|
| Name | Text | Yes | Must be unique, case-insensitive. Shows an inline error if it's a duplicate. |
| Muscle Groups | Multi-select chips | Yes, at least one | Uses the list in §6.1. Any number can be selected. |
| Photo | Image | No | "Take Photo" (camera) or "Choose from Gallery". Shows a preview after capture, with options to retake or remove. |

- **Save** creates the exercise. **[ASSUMPTION]** A "Save & add to this day" checkbox, on by default, also adds it to the day being shown and expands it, ready for the first set.
- **Cancel** discards the input.
- Photos are copied into app-private storage, center-cropped to a square and compressed, with a thumbnail of about 512 px.

**Editing an exercise:** the same form is used to edit an existing exercise's name, muscle group and photo. The photo can be replaced, retaken or removed. Open it by long-pressing an exercise in the picker (§4.6) or from an entry's long-press menu (*Edit exercise*). Changes apply everywhere that exercise appears, past days included.

### 4.6 Start Exercise (Exercise Picker)

Adds an **existing** exercise from the library to the day being shown.

- Searchable list of library exercises, filterable by muscle group chips. An exercise appears under every group it targets.
- Each row shows the name, muscle groups and thumbnail (or initials).
- **[ASSUMPTION]** Recently used exercises appear first.
- Tapping an exercise adds an Exercise Entry to the day, returns to the Day screen and expands the new entry.
- A "Create new…" shortcut at the bottom opens §4.5 with the search text prefilled.
- Long-pressing a library exercise offers *Edit* and *Delete*. Deleting archives the exercise, so its history is kept.

### 4.7 Gym Timer

The Gym Timer is a **stopwatch** that times the workout. There's no countdown or rest timer.

**Starting**
- **Automatic:** logging the first set of the day on **today's** page starts the stopwatch. It auto-starts only once per day. If the user stops it, logging more sets that day won't restart it. Logging sets on past days never starts it.
- There is no manual start button: the stopwatch only starts automatically from the first set of the day.

**The Active Timer card** (top of the Day screen, §4.1)
- Shows the elapsed time in large digits, with **Pause/Resume** and **Stop**.
- **Stop** ends the stopwatch and removes the card. **[ASSUMPTION]** The final time is shown briefly in a snackbar, but it isn't saved in v1.
- Keeps running while the app is in the background, using a foreground service with an ongoing notification. The notification has the same Pause/Resume and Stop actions.
- Only one stopwatch runs at a time.

### 4.8 Analytics

A screen of charts, with a **1 Day / 1 Week** toggle at the top.

- **Range:**
  - *1 Day* shows a single date.
  - *1 Week* shows the last 7 days, ending on the selected date (not a Mon–Sun calendar week).
  - Left and right arrows move the range back and forward. The screen opens on the day currently shown on the Day screen.
- **Charts:**
  1. **Sets per muscle group:** a bar chart with one bar per muscle group, showing the number of sets done in the range. A set counts toward **every** muscle group its exercise targets, so the bars can add up to more than the total set count. The total sets tile (below) counts each set once.
  2. **Weight lifted:** total volume (Σ reps × weight, in lb). In *1 Day* mode it's split by muscle group, using the same counting rule as chart 1. In *1 Week* mode it's a bar per day, which can also be split by muscle group.
  3. **Summary tiles:** total sets, total reps, total volume (lb), and the number of exercises.
- **[ASSUMPTION]** Tapping a muscle group's bar shows the exercises behind it.
- An empty range shows "No workouts logged in this range."

### 4.9 Settings

Settings has three tabs: **Colors** (mode and theme color, below), **Routines** (build and manage routines, §4.11) and **Data**: from the top, the profile (below), then export/import (§7).

**Profile** (Data tab), saved as you type:
- **Daily calorie goal** (kcal), shown on the Food page.
- **Body:** height in ft and in, weight in lb (with a "Use latest weigh-in" shortcut when it differs from the last Add Weight entry), age, and Male/Female.
- **Basal metabolic rate**, shown below once height, weight, age and sex are set, using Mifflin-St Jeor: 10·kg + 6.25·cm − 5·age, +5 for male or −161 for female.
- The profile is included in backups and restored on Replace imports.

**Appearance**
- **Mode:** Dark (default) or Light.
- **Theme color:** 8 preset swatches: Blue, Teal, Green, Orange, Red, Pink, Purple and Amber. The selected swatch shows a checkmark. Changes apply immediately and work in both modes.

**Data** (see §7)
- **Export workout data:** saves a backup file.
- **Import workout data:** restores from a backup file.

### 4.10 Food tab

The Food page shows today: a summary card (calories against the daily goal with a progress bar and "left"/"over", when a goal is set; then calories, water, latest weight, protein, carbs, fat, and creatine if any) and a list of everything logged, in time order. Long-press an entry to delete it. All items are logged to today.

| Item | Records |
|---|---|
| Add Weight | Body weight in lb (dialog). |
| Add Meal | Full screen: optional photo (camera or gallery), optional name, calories (required), protein, carbs and fat in g (blank = 0). |
| Add Water | Amount in mL (required), calories (optional, for drinks that have them; counts toward daily calories) and creatine in g (optional). |

**Food Analytics** (+ → Analytics): back arrow, tabs **Weight / Food / Water**, and **1 Day / 1 Week / Total** on each.
- **Weight:** tiles Latest, Change (first → last in range), Lowest; a line chart (1 Day: each weigh-in; otherwise one point per day, averaged).
- **Food:** tiles for calories (1 Day: total; otherwise average per logged day) and meal count; calories chart (1 Day: a bar per meal; 1 Week: a bar per day; Total: a line per day); macro bars (totals or average per logged day).
- **Water:** tiles Total and drinks (1 Day) or average per logged day; chart (1 Day: running total; 1 Week: a bar per day; Total: a line per day).
- Averages use days that have entries, so unlogged days don't pull them down.

Data model v3 added `body_weight`, `meal`, `water` and `supplement` tables. v4 adds `water.creatineG` and drops `supplement` (supplements were removed). Food data is included in backups, meal photos too.

### 4.11 Routines

A **routine** is a named list of exercises in a set order, e.g. "Push Day".

- **Building routines** happens in **Settings → Routines**: a list of routines (tap one to edit, long-press to edit or delete) and a **New Routine** button.
- **New Routine:** enter a name, then **Add exercise** to pick exercises from the library (the picker stays open so several can be added; the same exercise may appear more than once), or **Add new exercise** to create one (the New Exercise form, without "Save & add to this day"); saving it returns to the routine with the new exercise added at the end. Each row has a drag handle and Remove. Save needs a name and at least one exercise.
- **Reordering and supersets (drag):** hold a row (long-press) to pick it up, then drag.
  - Drop it **between** rows to move it there; a line shows where it will land.
  - Hold it **over** another row for about half a second: the row highlights and shows "Release to superset". Releasing places it right after that row in the same superset (joining that row's superset if it has one).
  - Dropping between two members of a superset joins that superset; dropping anywhere else takes the exercise out of its superset. A superset left with one exercise is dissolved.
  - Releasing over a row before it highlights cancels the drag.
  - Superset rows show a colored bar (one color per superset) and a "Superset" label. The list scrolls when an exercise is held near the top or bottom edge.
- Routine cards (Start Routine, Settings → Routines) mark superset exercises with a colored bar and "· superset".
- **Start Routine:** lists all routines with their exercises. Tapping one adds every exercise to the day being shown, in the routine's order, and opens the first. The routine's supersets become real supersets on that day, covering every set (from set 1), each with its own color. Long-press a routine to **Edit** or **Delete** it. Deleting a routine never touches logged workouts.
- Routines, including their supersets, are included in export/import backups.
- Data model v5 adds `routine_exercise.supersetGroup` (nullable INTEGER): consecutive rows with the same value form one superset.

---

## 5. Theming

- **Material 3** color schemes. The selected preset color seeds a dark and a light scheme, both generated with Material color utilities.
- Dark mode is the default on first launch. The dark background is near-black (`#121212`) or Material 3's dark surface.
- **Superset palette:** a fixed set of about 6 colors that stay distinct from the theme color and readable in both modes. Groups take these colors in turn on each day.
- Theme preferences are stored in DataStore and read before the first frame, so the app never flashes the wrong theme.

---

## 6. Data

### 6.1 Muscle Groups (default list)

Chest, Back, Shoulders, Biceps, Triceps, Forearms, Core/Abs, Quads, Hamstrings, Glutes, Calves, Full Body, Cardio.

**[ASSUMPTION]** This list is fixed in v1. Each exercise targets one or more groups, all treated equally (no primary/secondary distinction).

### 6.2 Data Model (Room)

```kotlin
@Entity
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,                 // stable ID used in export/import
    val name: String,                 // unique, case-insensitive
    val photoPath: String?,           // app-private file path
    val createdAt: Instant,
    val archived: Boolean = false     // soft delete keeps history intact
)

@Entity(primaryKeys = ["exerciseId", "muscleGroup"])
data class ExerciseMuscleGroup(
    val exerciseId: Long,             // FK → Exercise (cascade delete)
    val muscleGroup: MuscleGroup      // enum; one row per targeted group
)

@Entity
data class ExerciseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,             // FK → Exercise
    val date: LocalDate,              // the workout day
    val position: Int                 // order on the day's list
)

@Entity
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,                // FK → ExerciseEntry (cascade delete)
    val setIndex: Int,                // 1-based order within entry
    val reps: Int,
    val weightLb: Double,
    val completedAt: Instant
)

@Entity
data class SupersetGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val colorIndex: Int               // index into superset palette
)

@Entity(primaryKeys = ["groupId", "entryId"])
data class SupersetMember(
    val groupId: Long,
    val entryId: Long,                // an entry belongs to at most one group
    val startSetIndex: Int            // the set at which this entry joined
)
```

**Rules**
- A set is in the superset when `setIndex >= startSetIndex` for its entry's membership. Sets added later are included automatically.
- When a superset drops to fewer than 2 members, the group is deleted automatically.
- Deleting an Exercise archives it, so past history is kept. Deleting an ExerciseEntry deletes its sets and its superset membership in a cascade.
- If an edit or delete leaves an entry with no sets at or after its `startSetIndex`, the entry leaves the group.

### 6.3 Preferences (DataStore)
- `themeMode: dark | light` (default `dark`)
- `themeColor: blue | teal | green | orange | red | pink | purple | amber`

### 6.4 Timer State
The stopwatch's state is: start timestamp, accumulated paused time, whether it's paused, and `autoStartedForDate` (the date the stopwatch was last auto-started for, so it only auto-starts once per day). It's held by the foreground service and mirrored to DataStore, so it survives the app being killed.

---

## 7. Export & Import

This lets the user move their data to a new phone or restore it after a factory reset.

### 7.1 Export
- In Settings, choose **Export workout data**. The system "Save as" dialog opens (Storage Access Framework, `CreateDocument`). The user can save to Downloads, Google Drive or any other location the system offers.
- The default file name is `gymapp-backup-YYYY-MM-DD.zip`.
- The archive contains:
  - `manifest.json`: app version, schema version and export timestamp.
  - `data.json`: every exercise, entry, set, superset group and member, plus preferences. Records are linked by UUID, not by database ID.
  - `photos/<exercise-uuid>.jpg`: every exercise photo.
- The export always covers **the user's full history**.

### 7.2 Import
- In Settings, choose **Import workout data**. The system file picker opens (`OpenDocument`, filtered to `.zip`).
- Before anything changes, the app checks the manifest and schema version, and shows a summary such as "312 workouts, 48 exercises, 2023-01-04 → 2026-11-16".
- The user chooses one of:
  - **Replace:** wipe the current data and restore the backup. This needs a confirmation.
  - **Merge:** add records whose UUID isn't already present, and skip duplicates.
- The import runs in a single database transaction. If anything fails, nothing changes and the app shows an error.

### 7.3 Extra safety net
- **[ASSUMPTION]** Turn on Android Auto Backup for the Room database and photos, as a fallback that needs no action from the user. Manual export stays the main, guaranteed path.

---

## 8. Technical Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM with a unidirectional data flow (ViewModel + StateFlow), single-activity |
| Navigation | Navigation Compose; `HorizontalPager` for day swiping |
| Persistence | Room (SQLite) + DataStore Preferences |
| Serialization | kotlinx.serialization (JSON) + `java.util.zip` for the backup archive |
| DI | Manual container (`AppContainer`) — the app is small enough that Hilt isn't needed |
| Images | `ActivityResultContracts.TakePicture` / `PickVisualMedia`; Coil for loading |
| Drag & drop | Compose `pointerInput` with `detectDragGesturesAfterLongPress` and a custom drop-target registry (`onGloballyPositioned` bounds) |
| Charts | Hand-drawn Compose bar charts (no chart library) |
| Timer | Foreground service (`specialUse` / `shortService` type as appropriate) + `NotificationCompat` ongoing notification with actions |
| Min / Target SDK | minSdk 26 (Android 8.0) / targetSdk latest stable |

**Permissions:** `CAMERA` (requested only when the user taps Take Photo), `POST_NOTIFICATIONS` (Android 13+, for the timer), `FOREGROUND_SERVICE` plus the matching type permission, and `VIBRATE`. The gallery and export/import use the system pickers, which need no storage permission.

---

## 9. Non-Functional Requirements

- **Offline:** every feature works without a network connection.
- **Performance:** cold start to an interactive Day screen in under 1 s on mid-range devices. Drag interactions and day swipes hold 60 fps.
- **Usability:** touch targets at least 48 dp. Number inputs use a numeric keypad. Key numbers use large type.
- **Data safety:** data is written immediately on change. Export and import are atomic.
- **Accessibility:** TalkBack labels on all icons. Drag & drop has an accessible alternative: a set's edit dialog offers "Superset with…" and opens a picker of exercise and set. Superset groups are identified by their label as well as their color, so color-blind users can tell them apart.

---

## 10. Acceptance Criteria (v1)

1. Launching the app shows today's date and today's entries, or the empty state.
2. Swiping left shows the previous day and swiping right shows the next day.
3. A user can create an exercise with a name, one or more muscle groups and an optional photo. The photo appears on the right side of that exercise's row (initials if there's no photo), and it can be changed later.
4. A user can add a library exercise to the day shown, then add, edit and delete sets with reps and weight in lb.
5. Tapping an entry expands and collapses its set list.
6. Dragging set *i* of Exercise A onto set *j* of Exercise B creates a color-coded superset covering every set from those points onward, sets added later included. The change can be undone.
7. Logging the first set of today starts the stopwatch automatically. It also starts from the Gym Timer menu item. The card at the top of the day's list has working Pause/Resume and Stop buttons, and the stopwatch keeps running in the background.
8. Analytics shows sets per muscle group and weight lifted, with a working 1 Day / 1 Week toggle.
9. Settings offers Dark/Light mode and 8 theme colors. Changes apply immediately and persist across restarts. Dark mode is the default.
10. Exporting creates a `.zip` with all data and photos. Importing it on a fresh install restores the history exactly, photos included.

---

## 11. Future Ideas (post-v1)
- Routines and templates ("Push Day")
- A CSV export for spreadsheets
- Plate calculator
- Bodyweight and assisted exercises; time and distance for cardio
- Longer analytics ranges (month, all-time) and per-exercise progress charts

---

## 12. Open Questions

Every question has been answered. A few smaller **[ASSUMPTION]** items remain in the spec, and none of them block starting work:
- Tapping the date opens a date picker; future days can be browsed.
- Rows can be reordered; recently used exercises appear first in the picker.
- Collapsed rows auto-expand on hover during a drag.
- "Save & add to this day" is on by default in New Exercise.
- Tapping an analytics bar shows the exercises behind it.
- The stopwatch's final time isn't saved.
- Android Auto Backup is used as a fallback.
