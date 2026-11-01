# StudyHive

An offline-first Android app for university students to track units, assessments and study time,
with deadline reminders that arrive even when the app is closed. COS30017 Assignment 3.

- Design, decisions and architecture diagrams: [`docs/DESIGN.md`](docs/DESIGN.md)
- Technical investigation (SQL vs Kotlin aggregation): [`docs/INVESTIGATION.md`](docs/INVESTIGATION.md)
- Evidence (test reports, benchmark CSVs, screenshots): [`docs/evidence/`](docs/evidence/)

## Tech stack

Kotlin 2.2, Jetpack Compose (Material 3), Navigation Compose (type-safe routes, deep link),
ViewModel + StateFlow + SavedStateHandle, Room 2.8 (KSP, exported schema), coroutines/Flow,
WorkManager 2.12, kotlinx-serialization. minSdk 26, targetSdk 37. No network permission.

## Build and run

```bash
./gradlew :app:assembleDebug            # build
./gradlew :app:installDebug             # install on a running emulator/device
```

## Tests

```bash
# JVM: validators, policies, ViewModels, theme contrast
./gradlew :app:testDebugUnitTest

# Device: Room/DAO, repository + WorkManager integration, Compose UI, end-to-end, accessibility
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notPackage=com.example.assignment_3.benchmark,com.example.assignment_3.evidence
```

Last executed on 1 Nov 2026 (Pixel 8 emulator, API 34): **61/61 JVM** and **39/39 instrumented**
tests passed. Reports: `docs/evidence/tests/`.

The two excluded packages are tools, not tests: `benchmark` runs the investigation and
`evidence` captures report screenshots. Both write files to the app's external storage, so run them
with `adb shell am instrument` (see the class comments) to avoid the uninstall that
`connectedAndroidTest` performs. Analyse benchmark output with `python3 scripts/analyse_benchmark.py`.

## Project structure

```
app/src/main/java/com/example/assignment_3/
  domain/          pure rules: validators, ReminderPolicy, WeekRange, enums
  data/local/      Room entities, DAOs, read models, database
  data/repository/ repository interfaces + Room implementations
  notifications/   WorkManager scheduler, worker, notification helper
  di/              AppContainer (manual DI)
  ui/              navigation, screens + ViewModels per feature, components, theme
app/src/test/          JVM tests (fakes in testing/)
app/src/androidTest/   device tests, benchmark, screenshot capture
```

## Compliance with the A3 brief

| Brief section | How StudyHive meets it | Evidence |
|---|---|---|
| Design chain (vision -> stories -> use cases -> prototype -> data model -> architecture) | `DESIGN.md` sections 1-7; prototype sketches to be added by the student | `docs/DESIGN.md` |
| 1. Compose UI, multiple meaningful screens, list UI | 7 screens + bottom sheet; `LazyColumn` (dashboard, unit list, unit detail, assessment detail), `LazyRow` (dashboard units) | Screenshots 01-14 |
| 2. Persistent CRUD with validation, empty and failure handling | Room CRUD on units, assessments and sessions; FK cascade; unique code; validators; empty/error states; failure messages | `StudyHiveDaoTest`, `RepositoryIntegrationTest`, `FormViewModelTest` |
| 3. Architecture and state | UI -> ViewModel -> Repository -> DAO -> Room, plus pure domain; Flow -> `stateIn` -> `collectAsStateWithLifecycle` | `DESIGN.md` sec 7-8, Appendix A1, A4 |
| 4. Coroutines with justified scopes | `viewModelScope` writes, `stateIn` observation, `CoroutineWorker`, `rememberCoroutineScope` for Undo; cancellation re-thrown | `DESIGN.md` sec 9 |
| 5. Meaningful notification + side effects | Deadline reminder via WorkManager, re-checked at fire time, deep link; contextual permission; 9 side-effect uses each with a purpose | `ReminderNotificationTest`, screenshot 15, `DESIGN.md` sec 10-11 |
| 6. Multi-level testing incl. normal/boundary/error | 100 automated tests across 7 levels; bugs found and fixed listed | `docs/evidence/tests/`, `DESIGN.md` sec 13 |
| 7. UX: states, validation, confirmation, accessibility, destructive actions, preservation | Sealed UiStates, inline errors, cascade counts, Undo, labels/48dp/contrast/200% font tested, SavedStateHandle drafts | `AccessibilityTest`, `ThemeContrastTest`, screenshots |
| 8. Integration and justification | Reminders, Room Flows, validation and navigation work as one workflow; each library justified | `DESIGN.md` |
| Investigation (Question -> ... -> Recommendation) | SQL vs `@Relation` + Kotlin aggregation at 500/5,000/50,000 sessions, 3 runs; SQL shipped | `docs/INVESTIGATION.md`, `docs/evidence/benchmark/` |
| Architecture diagrams in appendix | Mermaid diagrams: layers, reminder sequence, navigation, read path, ER | `DESIGN.md` Appendix, sec 6 |
| A2 reflection | Technical comparison prepared; personal reflection by the student | `DESIGN.md` end |
| Single PDF + GitHub Classroom link | Report to be produced from these documents | - |
