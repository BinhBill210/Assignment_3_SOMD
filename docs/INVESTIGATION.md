# Technical Investigation: Dashboard aggregation strategy

## Research question

For the StudyHive dashboard's *unit progress* row (per unit: number of assessments, number
submitted, minutes studied this week), is it better to

- **A. aggregate in SQL** - one Room query using correlated sub-queries / `GROUP BY`, returning
  one `UnitProgress` row per unit, or
- **B. aggregate in Kotlin** - load `StudyUnit -> Assessment -> StudySession` with nested Room
  `@Relation` and compute the same numbers with collection operations?

The answer decides which implementation the shipped `StudyUnitDao` exposes to `DashboardViewModel`.

## Why this question matters to the app

The dashboard is the start destination and re-queries every time any of the three tables changes,
because Room `Flow` invalidation is table-based. Study sessions are the only table that grows
without bound (a student logging 3 sessions a day for 4 years produces ~4,000 rows), so the
strategy must stay fast as that table grows.

## Hypothesis

- H1: At a realistic one-semester size (about 500 sessions) both approaches complete well under
  one 16 ms frame, so either is acceptable for users.
- H2: B's latency grows roughly linearly with the total number of sessions, because every session
  row crosses the SQLite cursor and becomes a Kotlin object even though most are outside the
  current week. A grows more slowly because SQLite does the filtering and only one row per unit
  leaves the database.
- H3: At 50,000 sessions A is at least 5x faster than B.

## Variables

| Type | Variable | Values |
|---|---|---|
| Independent | Aggregation strategy | A (SQL), B (`@Relation` + Kotlin) |
| Independent | Total study sessions | 500, 5,000, 50,000 |
| Controlled | Units / assessments | 8 units x 6 assessments = 48 assessments |
| Controlled | Session distribution | Uniform across assessments; `startedAt` uniform over 16 weeks, fixed RNG seed |
| Controlled | Device, build type, DB | Same emulator, debug build, fresh in-memory Room DB per dataset size |
| Dependent | Query latency | Wall-clock time of one complete read, `System.nanoTime()` |
| Dependent | Rows materialised | Number of entity objects created to answer the query (deterministic) |
| Dependent | Flow re-emission latency | Time from inserting one session to the dashboard `Flow` emitting the updated value |

## Method

1. An instrumented test (`DashboardAggregationBenchmark`, `androidTest`) builds an in-memory
   `StudyHiveDatabase` and seeds it in a single transaction for each dataset size.
2. **Correctness gate:** A and B must return identical `UnitProgress` lists before any timing is
   recorded; otherwise the test fails.
3. For each strategy: 5 warm-up runs (discarded, lets ART JIT-compile the code path), then 30
   measured runs. Strategy order alternates per run (A,B,B,A,...) to reduce ordering bias.
4. Report median, p90, min and max per strategy and size. The median is used for conclusions
   because emulator timing has outliers (GC pauses, host scheduling).
5. Flow re-emission: subscribe to each strategy's `Flow`, insert one session, measure the time
   until the new value arrives; 20 repetitions at 50,000 sessions.
6. Results are written as CSV to the app's external files directory and logged with tag
   `StudyHiveBench`, then pulled with `adb pull` into `docs/evidence/`.
7. The whole experiment is repeated in 3 separate test executions to check stability across runs.

## Test environment

- Host: Apple M1 MacBook, 8 GB RAM, macOS 15.7.4
- Device: Android Emulator `Pixel_8`, Android 14 (API 34) arm64-v8a, 2 GB RAM
- Build: debug variant, in-memory Room database

## Expected evidence

- CSV of every measured run, and a summary table (median / p90 per strategy and size)
- A chart of median latency against dataset size
- The rows-materialised count per strategy, which explains *why* the latency differs
- The test report showing the correctness gate passed

## Limitations (stated in advance)

- An emulator is not a phone: absolute times will differ on real hardware, so conclusions rely on
  the *relative* difference and the growth trend, not absolute milliseconds.
- A debug build disables some optimisations. Jetpack Microbenchmark (a separate module that locks
  clocks and works best on a physical device) would be more rigorous; it was not used to keep the
  project to one module, and because no physical device was available.
- An in-memory database removes disk I/O, which favours both strategies equally but under-states
  real cold-start latency.
- Synthetic data: uniform distribution may not match real study behaviour.

## Results

Recorded in Phase 8 from real executions; see `docs/evidence/`.
