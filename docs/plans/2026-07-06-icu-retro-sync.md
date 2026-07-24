# Retroactive intervals.icu Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user re-upload any completed workout to intervals.icu from the app after the fact, and persist a "synced" marker so both the user and the code know which sessions already made it.

**Architecture:** Sessions already persist everything needed to rebuild a FIT file (`samplesJson`, `startedAt`, `durationSec`), so retroactive upload is: decode samples → `FitEncoder.encode` → `IntervalsIcuClient.uploadActivity`. We add one nullable column `icuSyncedAt` to the Room `sessions` table (marker + timestamp in one field), extract the upload-and-mark logic into a single shared `IcuActivityUploader`, and reuse it from the existing post-workout path (`WorkoutCompleteViewModel`) and a new manual "Upload to intervals.icu" action on the session detail screen. The history list shows a synced badge.

**Tech Stack:** Kotlin, Room (migration 1→2), Jetpack Compose, kotlinx.serialization, plain `HttpURLConnection` client (`IntervalsIcuClient`), JUnit4 + turbine + `kotlinx-coroutines-test` for JVM unit tests.

## Global Constraints

- All paths below are relative to `android/` (the app module lives at `android/app`).
- No new dependencies. No new HTTP library. Room stays at the versions already declared.
- Room migration must be a real `ALTER TABLE` migration — **no** `fallbackToDestructiveMigration()` (existing user sessions must survive the upgrade).
- Run unit tests with: `./gradlew :app:testDebugUnitTest` (or targeted `--tests` filters as shown per task) from the `android/` directory.
- Test style: fast JVM tests with fakes, matching `app/src/test/java/com/trainerloop/data/repository/SessionRepositoryTest.kt`. No Robolectric/instrumentation in this plan (that's the existing project convention — see the comment at the top of `SessionRepositoryTest.kt`).
- Commit after each task with the message given in the task.

## Background: current behavior (read before starting)

`WorkoutCompleteViewModel.init` (`app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt`) fires three independent things: `computeSummary()`, `createFitFile()` (which immediately calls `uploadToIntervalsIcu(file)` fire-and-forget), and `saveSession()`. If the upload fails — network down, app killed, ICU not yet configured — nothing is recorded and there is no way to retry: the session sits in Room forever, un-synced, invisible as such. There is also a latent race: the upload and the DB insert run in parallel coroutines, so nothing could safely "mark the row synced" today. This plan fixes the sequencing (save first, then upload, then mark) and adds the retry surface.

**Duplicate-upload caveat:** intervals.icu rejects a re-upload of an identical activity with HTTP `422` and a body containing a "duplicate"-style message. We treat that as success (the activity IS on the server) so a retry after a lost success-response still converges to `synced`. Task 3 verifies the exact body wording against the real API and adjusts the match string if needed.

---

### Task 1: Room column `icuSyncedAt` + migration + repository plumbing

**Files:**
- Modify: `app/src/main/java/com/trainerloop/data/source/local/SessionEntity.kt`
- Modify: `app/src/main/java/com/trainerloop/data/source/local/SessionDao.kt`
- Modify: `app/src/main/java/com/trainerloop/data/source/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/trainerloop/data/model/Session.kt`
- Modify: `app/src/main/java/com/trainerloop/data/repository/SessionRepository.kt`
- Test: `app/src/test/java/com/trainerloop/data/repository/SessionRepositoryTest.kt`

**Interfaces:**
- Consumes: existing `SessionDao` / `SessionRepository` / `SessionData` / `SessionSummary`.
- Produces:
  - `SessionEntity.icuSyncedAt: String?` (nullable ISO-8601 instant; `null` = never synced)
  - `SessionData.icuSyncedAt: String?` and `SessionSummary.icuSyncedAt: String?` (default `null`)
  - `suspend fun SessionRepository.markIcuSynced(id: String, syncedAt: String)`
  - `suspend fun SessionDao.markIcuSynced(id: String, syncedAt: String)`
  - `AppDatabase.MIGRATION_1_2: Migration`, database `version = 2`

- [ ] **Step 1: Write the failing tests**

Add to `SessionRepositoryTest.kt` (the file already has a `FakeSessionDao` at the bottom — it will fail to compile until Step 3 adds the new DAO method to it and the entity field; that compile failure is the "red" state):

```kotlin
@Test
fun `markIcuSynced stamps the session and summaries expose it`() = runTest {
  val dao = FakeSessionDao()
  val repository = SessionRepository(dao)
  repository.save(sampleSession(id = "s1"))

  // Fresh sessions are unsynced.
  assertNull(repository.getById("s1")!!.icuSyncedAt)

  repository.markIcuSynced("s1", "2026-07-06T10:00:00Z")

  assertEquals("2026-07-06T10:00:00Z", repository.getById("s1")!!.icuSyncedAt)
  repository.summaries().test {
    assertEquals("2026-07-06T10:00:00Z", awaitItem().single().icuSyncedAt)
    cancelAndIgnoreRemainingEvents()
  }
}

@Test
fun `markIcuSynced on missing id is a no-op`() = runTest {
  val repository = SessionRepository(FakeSessionDao())
  repository.markIcuSynced("missing", "2026-07-06T10:00:00Z")
  assertNull(repository.getById("missing"))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `android/`): `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.repository.SessionRepositoryTest"`
Expected: FAIL — compilation error, `markIcuSynced` / `icuSyncedAt` unresolved.

- [ ] **Step 3: Implement**

`SessionEntity.kt` — add the column with a default so Room's expected schema matches the migration:

```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val workoutName: String,
    val startedAt: String,
    val endedAt: String?,
    val durationSec: Int,
    val samplesJson: String,
    val coachEventsJson: String,
    val completed: Boolean,
    val avgPower: Int,
    val maxPower: Int,
    val avgCadence: Int,
    val avgHr: Int,
    /** ISO-8601 instant of the last successful intervals.icu upload; null = never synced. */
    val icuSyncedAt: String? = null
)
```

`SessionDao.kt` — add:

```kotlin
@Query("UPDATE sessions SET icuSyncedAt = :syncedAt WHERE id = :id")
suspend fun markIcuSynced(id: String, syncedAt: String)
```

`AppDatabase.kt` — bump the version and register a real migration:

```kotlin
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SessionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN icuSyncedAt TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trainerloop.db"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }

        fun inMemory(context: Context): AppDatabase { /* unchanged */ }
    }
}
```

`Session.kt` — add `val icuSyncedAt: String? = null` as the last property of **both** `SessionSummary` and `SessionData` (defaulted, so existing constructor call sites keep compiling).

`SessionRepository.kt` — add the passthrough and thread the field through all three mappers:

```kotlin
suspend fun markIcuSynced(id: String, syncedAt: String) {
    dao.markIcuSynced(id, syncedAt)
}
```

and add `icuSyncedAt = s.icuSyncedAt` to `toEntity`, `icuSyncedAt = e.icuSyncedAt` to `toSummary` and `toData`.

`SessionRepositoryTest.kt` — update `FakeSessionDao` (bottom of file) with the new method:

```kotlin
override suspend fun markIcuSynced(id: String, syncedAt: String) {
    val existing = storage[id] ?: return
    storage[id] = existing.copy(icuSyncedAt = syncedAt)
    emit()
}
```

(Adapt names to the fake's actual internals — it stores entities in a map/list and re-emits its flow; follow whatever `insert` does in that file.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.repository.SessionRepositoryTest"`
Expected: PASS (all tests in the class, old and new).

- [ ] **Step 5: Commit**

```bash
git add -A android/app/src
git commit -m "feat(sync): add icuSyncedAt marker column with room migration 1->2"
```

---

### Task 2: Treat intervals.icu duplicate-upload responses as success

**Files:**
- Modify: `app/src/main/java/com/trainerloop/data/source/remote/IntervalsIcuClient.kt:48-66`
- Test: `app/src/test/java/com/trainerloop/data/source/remote/IntervalsIcuClientTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `IntervalsIcuClient.Companion.isUploadAccepted(code: Int, body: String): Boolean` — pure, testable; `uploadActivity` keeps its `suspend (athleteId, fitBytes, name) -> Boolean` signature but now returns `true` for duplicates.

Why: a retry of an already-uploaded activity gets HTTP 422 "duplicate" from intervals.icu. For our purposes that means "it's on the server" → the session should be marked synced, otherwise the retry button can never turn a lost-response session green.

- [ ] **Step 1: Write the failing test**

Add to `IntervalsIcuClientTest.kt`:

```kotlin
@Test
fun `upload accepted on 2xx and on 422 duplicate`() {
  org.junit.Assert.assertTrue(IntervalsIcuClient.isUploadAccepted(200, ""))
  org.junit.Assert.assertTrue(IntervalsIcuClient.isUploadAccepted(201, "{}"))
  org.junit.Assert.assertTrue(
    IntervalsIcuClient.isUploadAccepted(422, """{"error":"Duplicate of activity i12345"}""")
  )
  org.junit.Assert.assertFalse(IntervalsIcuClient.isUploadAccepted(422, """{"error":"Invalid file"}"""))
  org.junit.Assert.assertFalse(IntervalsIcuClient.isUploadAccepted(401, "unauthorized"))
  org.junit.Assert.assertFalse(IntervalsIcuClient.isUploadAccepted(500, "boom"))
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.source.remote.IntervalsIcuClientTest"`
Expected: FAIL — `isUploadAccepted` unresolved.

- [ ] **Step 3: Implement**

In `IntervalsIcuClient`, change the tail of `uploadActivity` and add a companion:

```kotlin
suspend fun uploadActivity(athleteId: String, fitBytes: ByteArray, name: String): Boolean =
  withContext(Dispatchers.IO) {
    // ... multipart write unchanged ...
    val code = conn.responseCode
    val body = conn.readBody()
    isUploadAccepted(code, body)
  }

companion object {
  /** 2xx = uploaded; 422 mentioning "duplicate" = already on the server, equally fine. */
  fun isUploadAccepted(code: Int, body: String): Boolean =
    code in 200..299 || (code == 422 && body.contains("duplicate", ignoreCase = true))
}
```

**Manual verification note (do this once during Task 6's end-to-end check):** upload the same FIT twice against a real intervals.icu account and confirm the second response is 422 with "duplicate" in the body. If the wording differs, widen the match here — the unit test pins whatever string is agreed on.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.source.remote.IntervalsIcuClientTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A android/app/src
git commit -m "feat(sync): treat intervals.icu duplicate upload (422) as success"
```

---

### Task 3: Shared `IcuActivityUploader` (rebuild FIT from stored session, upload, mark synced)

**Files:**
- Create: `app/src/main/java/com/trainerloop/data/repository/IcuActivityUploader.kt`
- Test: `app/src/test/java/com/trainerloop/data/repository/IcuActivityUploaderTest.kt`

**Interfaces:**
- Consumes: `SessionData` (Task 1 shape, incl. `icuSyncedAt`), `SessionRepository.markIcuSynced(id, syncedAt)` (Task 1), `FitEncoder.encode(startTimeMs: Long, elapsedSec: Int, samples: List<TelemetrySample>): ByteArray` (exists, `domain/fit/FitEncoder.kt`), `TelemetrySample.serializer()` (exists).
- Produces:

```kotlin
class IcuActivityUploader(
  private val sessionRepository: SessionRepository,
  private val upload: suspend (fitBytes: ByteArray, name: String) -> Boolean,
  private val nowIso: () -> String = { java.time.Instant.now().toString() }
) {
  /** Rebuilds the FIT from stored telemetry, uploads it, marks the session synced on success. */
  suspend fun uploadSession(session: SessionData): Boolean
}
```

The network dependency is a plain lambda (not the concrete `IntervalsIcuClient`) so tests need no HTTP fake; callers bind it as `{ bytes, name -> IntervalsIcuClient(apiKey).uploadActivity(athleteId, bytes, name) }`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/trainerloop/data/repository/IcuActivityUploaderTest.kt`:

```kotlin
package com.trainerloop.data.repository

import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.TelemetrySample
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcuActivityUploaderTest {

  private fun session(id: String = "s1", samples: List<TelemetrySample>): SessionData {
    val json = Json.encodeToString(ListSerializer(TelemetrySample.serializer()), samples)
    return SessionData(
      id = id,
      workoutId = "w1",
      workoutName = "Sweet Spot",
      startedAt = "2026-07-06T09:00:00Z",
      endedAt = "2026-07-06T10:00:00Z",
      durationSec = 3600,
      samplesJson = json,
      coachEventsJson = "",
      completed = true,
      avgPower = 200,
      maxPower = 300,
      avgCadence = 90,
      avgHr = 140
    )
  }

  // Reuse the FakeSessionDao pattern from SessionRepositoryTest for a real repository.
  private fun repo() = SessionRepository(FakeSessionDao())

  private val someSamples = listOf(
    TelemetrySample(timeSec = 0, power = 200, cadence = 90, hr = 140, targetPower = 200),
    TelemetrySample(timeSec = 1, power = 210, cadence = 91, hr = 141, targetPower = 200)
  )

  @Test
  fun `successful upload marks session synced with timestamp`() = runTest {
    val repository = repo()
    val data = session(samples = someSamples)
    repository.save(data)
    var uploadedName: String? = null
    val uploader = IcuActivityUploader(
      sessionRepository = repository,
      upload = { bytes, name ->
        assertTrue(bytes.isNotEmpty())
        uploadedName = name
        true
      },
      nowIso = { "2026-07-06T11:00:00Z" }
    )

    assertTrue(uploader.uploadSession(data))
    assertEquals("Sweet Spot", uploadedName)
    assertEquals("2026-07-06T11:00:00Z", repository.getById("s1")!!.icuSyncedAt)
  }

  @Test
  fun `failed upload leaves session unsynced`() = runTest {
    val repository = repo()
    val data = session(samples = someSamples)
    repository.save(data)
    val uploader = IcuActivityUploader(repository, upload = { _, _ -> false })

    assertFalse(uploader.uploadSession(data))
    assertNull(repository.getById("s1")!!.icuSyncedAt)
  }

  @Test
  fun `upload exception is caught and leaves session unsynced`() = runTest {
    val repository = repo()
    val data = session(samples = someSamples)
    repository.save(data)
    val uploader = IcuActivityUploader(repository, upload = { _, _ -> throw RuntimeException("offline") })

    assertFalse(uploader.uploadSession(data))
    assertNull(repository.getById("s1")!!.icuSyncedAt)
  }

  @Test
  fun `session with no samples is not uploaded`() = runTest {
    val repository = repo()
    val data = session(samples = emptyList())
    repository.save(data)
    var called = false
    val uploader = IcuActivityUploader(repository, upload = { _, _ -> called = true; true })

    assertFalse(uploader.uploadSession(data))
    assertFalse(called)
  }
}
```

Notes for the implementer:
- Adjust the `TelemetrySample(...)` constructor arguments to the real fields of `app/src/main/java/com/trainerloop/data/model/TelemetrySample.kt` — read that file first; the test intent is just "two non-empty samples".
- `FakeSessionDao` lives inside `SessionRepositoryTest.kt` and is likely `private` to that file. Either move it to its own file `app/src/test/java/com/trainerloop/data/repository/FakeSessionDao.kt` (visible to both tests — preferred, delete the inline copy) or duplicate the minimal fake here. Moving it is a mechanical cut-paste; keep `SessionRepositoryTest` green.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.repository.IcuActivityUploaderTest"`
Expected: FAIL — `IcuActivityUploader` unresolved.

- [ ] **Step 3: Implement**

Create `app/src/main/java/com/trainerloop/data/repository/IcuActivityUploader.kt`:

```kotlin
package com.trainerloop.data.repository

import com.trainerloop.data.model.SessionData
import com.trainerloop.data.model.TelemetrySample
import com.trainerloop.domain.fit.FitEncoder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Rebuilds a FIT file from a stored session and uploads it to intervals.icu,
 * stamping [SessionData.icuSyncedAt] on success. Shared by the post-workout
 * auto-upload and the manual retry on the session detail screen.
 */
class IcuActivityUploader(
  private val sessionRepository: SessionRepository,
  private val upload: suspend (fitBytes: ByteArray, name: String) -> Boolean,
  private val nowIso: () -> String = { Instant.now().toString() }
) {

  suspend fun uploadSession(session: SessionData): Boolean {
    val samples: List<TelemetrySample> = try {
      Json.decodeFromString(ListSerializer(TelemetrySample.serializer()), session.samplesJson)
    } catch (_: Exception) {
      emptyList()
    }
    if (samples.isEmpty()) return false

    val startTimeMs = try {
      Instant.parse(session.startedAt).toEpochMilli()
    } catch (_: Exception) {
      return false
    }

    val fitBytes = FitEncoder.encode(
      startTimeMs = startTimeMs,
      elapsedSec = session.durationSec,
      samples = samples
    )

    val ok = try {
      upload(fitBytes, session.workoutName)
    } catch (_: Exception) {
      false
    }
    if (ok) sessionRepository.markIcuSynced(session.id, nowIso())
    return ok
  }
}
```

(Check `FitEncoder.encode`'s actual parameter names in `domain/fit/FitEncoder.kt` and match them.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.trainerloop.data.repository.IcuActivityUploaderTest" --tests "com.trainerloop.data.repository.SessionRepositoryTest"`
Expected: PASS (both classes — confirms the FakeSessionDao move didn't break the old tests).

- [ ] **Step 5: Commit**

```bash
git add -A android/app/src
git commit -m "feat(sync): shared IcuActivityUploader rebuilds FIT and marks sessions synced"
```

---

### Task 4: Fix post-workout flow — save first, then upload, then mark synced

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/complete/WorkoutCompleteViewModel.kt`

**Interfaces:**
- Consumes: `IcuActivityUploader` (Task 3), `SessionRepository.getById` (exists).
- Produces: no new public API. Behavior change only: the DB insert now happens **before** the upload, and a successful upload stamps `icuSyncedAt` — eliminating the current race where upload and insert run in parallel and success is never recorded.

No new unit test in this task: `WorkoutCompleteViewModel` is an `AndroidViewModel` with no existing JVM test harness, and all the new logic it calls (`IcuActivityUploader`) is already covered by Task 3's tests. Verification is the manual end-to-end check in Task 6. (If a `WorkoutCompleteViewModel` test harness is added later, cover the sequencing there.)

- [ ] **Step 1: Rewire the init sequencing**

In `WorkoutCompleteViewModel.kt`:

1. In `createFitFile()` (line ~204), **delete** the `uploadToIntervalsIcu(file)` call — `createFitFile` now only produces the file for the Share button.

2. Change `saveSession()` so the upload chains after a successful insert. Replace the body of `saveSession()`'s `viewModelScope.launch` block:

```kotlin
private fun saveSession() {
  val state = _uiState.value
  if (samples.isEmpty()) return

  val samplesJson = Json.encodeToString(ListSerializer(TelemetrySample.serializer()), samples)
  val sessionData = SessionData(
    id = sessionId,
    workoutId = workoutId,
    workoutName = workoutName,
    startedAt = Instant.ofEpochMilli(startTimeMs).toString(),
    endedAt = Instant.now().toString(),
    durationSec = state.durationSec,
    samplesJson = samplesJson,
    coachEventsJson = coachJson,
    completed = true,
    avgPower = state.avgPower,
    maxPower = state.maxPower,
    avgCadence = state.avgCadence,
    avgHr = state.avgHr
  )

  viewModelScope.launch {
    try {
      sessionRepository.save(sessionData)
      _uiState.value = _uiState.value.copy(isSaved = true)
    } catch (e: Exception) {
      _uiState.value = _uiState.value.copy(error = "Failed to save session: ${e.message}")
      return@launch
    }
    uploadToIntervalsIcu(sessionData)
  }
}
```

3. Replace `uploadToIntervalsIcu(file: File)` with a session-based version using the shared uploader:

```kotlin
private suspend fun uploadToIntervalsIcu(session: SessionData) {
  val profile = profileRepository.getProfileSync()
  val athleteId = profile.intervalsIcuAthleteId
  val apiKey = profile.intervalsIcuApiKey
  if (athleteId.isBlank() || apiKey.isBlank()) return

  _uiState.value = _uiState.value.copy(uploadStatus = "Uploading…")
  val uploader = IcuActivityUploader(
    sessionRepository = sessionRepository,
    upload = { bytes, name -> IntervalsIcuClient(apiKey).uploadActivity(athleteId, bytes, name) }
  )
  val ok = uploader.uploadSession(session)
  _uiState.value = _uiState.value.copy(
    uploadStatus = if (ok) "Uploaded to intervals.icu" else "Upload failed — retry from History"
  )
}
```

4. Update imports: add `com.trainerloop.data.repository.IcuActivityUploader`; `java.io.File` stays (still used by `fitFile` share path).

Note the failure copy points the user at the new retry surface ("retry from History").

- [ ] **Step 2: Verify it compiles and the full test suite is green**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add -A android/app/src
git commit -m "fix(sync): save session before upload and mark icuSyncedAt on success"
```

---

### Task 5: History list — synced badge

**Files:**
- Modify: `app/src/main/java/com/trainerloop/ui/history/HistoryScreen.kt` (the `SessionCard` composable, line ~107)

**Interfaces:**
- Consumes: `SessionSummary.icuSyncedAt` (Task 1).
- Produces: UI only.

- [ ] **Step 1: Add the badge to `SessionCard`**

Inside `SessionCard(session: SessionSummary, ...)`, next to the workout name / date row (fit it into the card's existing header `Row` — read the composable and place it where the layout allows), add:

```kotlin
if (session.icuSyncedAt != null) {
  Icon(
    imageVector = Icons.Filled.CloudDone,
    contentDescription = "Synced to intervals.icu",
    tint = MaterialTheme.colorScheme.primary,
    modifier = Modifier.size(16.dp)
  )
}
```

Imports: `androidx.compose.material.icons.filled.CloudDone`, `androidx.compose.material3.Icon`, `androidx.compose.foundation.layout.size`. If `CloudDone` is unavailable in the bundled `material-icons` artifact, use `Icons.Filled.Check` with the same `contentDescription` — do not add the extended icons dependency for one glyph.

No badge for un-synced sessions — absence of the icon is the "not synced" state; the detail screen (Task 6) carries the explicit action.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A android/app/src
git commit -m "feat(sync): show synced-to-icu badge on history cards"
```

---

### Task 6: Session detail — manual "Upload to intervals.icu" action

**Files:**
- Create: `app/src/main/java/com/trainerloop/ui/history/SessionDetailViewModel.kt`
- Create: `app/src/main/java/com/trainerloop/ui/history/SessionDetailViewModelFactory.kt`
- Modify: `app/src/main/java/com/trainerloop/ui/history/SessionDetailScreen.kt`

**Interfaces:**
- Consumes: `SessionRepository.getById`, `IcuActivityUploader` (Task 3), `ProfileRepository.getProfileSync()` (exists — fields `intervalsIcuAthleteId`, `intervalsIcuApiKey`), `IntervalsIcuClient.uploadActivity` (Task 2).
- Produces:

```kotlin
data class SessionDetailUiState(
  val session: SessionData? = null,
  val icuConfigured: Boolean = false,
  val uploadStatus: String? = null,
  val isUploading: Boolean = false
)

class SessionDetailViewModel(application: Application, sessionId: String) : AndroidViewModel {
  val uiState: StateFlow<SessionDetailUiState>
  fun uploadToIcu()
}
```

Upload runs in `viewModelScope` (not a composable scope) so navigating away mid-upload doesn't cancel it.

- [ ] **Step 1: Create the ViewModel**

`app/src/main/java/com/trainerloop/ui/history/SessionDetailViewModel.kt`:

```kotlin
package com.trainerloop.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.model.SessionData
import com.trainerloop.data.repository.IcuActivityUploader
import com.trainerloop.data.repository.ProfileRepository
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.data.source.remote.IntervalsIcuClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionDetailUiState(
  val session: SessionData? = null,
  val icuConfigured: Boolean = false,
  val uploadStatus: String? = null,
  val isUploading: Boolean = false
)

class SessionDetailViewModel(
  application: Application,
  private val sessionId: String,
  private val sessionRepository: SessionRepository =
    SessionRepository.create(AppDatabase.getInstance(application)),
  private val profileRepository: ProfileRepository = ProfileRepository(application)
) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(SessionDetailUiState())
  val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      val profile = profileRepository.getProfileSync()
      _uiState.value = _uiState.value.copy(
        session = sessionRepository.getById(sessionId),
        icuConfigured = profile.intervalsIcuAthleteId.isNotBlank() &&
          profile.intervalsIcuApiKey.isNotBlank()
      )
    }
  }

  fun uploadToIcu() {
    val session = _uiState.value.session ?: return
    if (_uiState.value.isUploading) return
    val profile = profileRepository.getProfileSync()
    if (profile.intervalsIcuAthleteId.isBlank() || profile.intervalsIcuApiKey.isBlank()) return

    _uiState.value = _uiState.value.copy(isUploading = true, uploadStatus = "Uploading…")
    viewModelScope.launch {
      val uploader = IcuActivityUploader(
        sessionRepository = sessionRepository,
        upload = { bytes, name ->
          IntervalsIcuClient(profile.intervalsIcuApiKey)
            .uploadActivity(profile.intervalsIcuAthleteId, bytes, name)
        }
      )
      val ok = uploader.uploadSession(session)
      _uiState.value = _uiState.value.copy(
        isUploading = false,
        uploadStatus = if (ok) "Uploaded to intervals.icu" else "Upload failed — check connection and try again",
        // Reload so icuSyncedAt reflects the new state.
        session = sessionRepository.getById(sessionId)
      )
    }
  }
}
```

`app/src/main/java/com/trainerloop/ui/history/SessionDetailViewModelFactory.kt` (same pattern as `WorkoutDetailViewModelFactory`):

```kotlin
package com.trainerloop.ui.history

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SessionDetailViewModelFactory(
  private val application: Application,
  private val sessionId: String
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(SessionDetailViewModel::class.java)) {
      return SessionDetailViewModel(application, sessionId) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
```

- [ ] **Step 2: Wire the screen**

In `SessionDetailScreen.kt`:

1. Replace the `produceState` session loading (lines 53-56) with the ViewModel:

```kotlin
val context = LocalContext.current
val viewModel: SessionDetailViewModel = viewModel(
  factory = SessionDetailViewModelFactory(
    context.applicationContext as Application,
    sessionId
  )
)
val state by viewModel.uiState.collectAsState()
val session = state.session
```

(Imports: `android.app.Application`, `androidx.lifecycle.viewmodel.compose.viewModel`, `androidx.compose.runtime.collectAsState`. Keep the existing loading spinner while `session == null`. The rest of the screen keeps reading from `s = session` as before.)

2. After the stats `Card` (below line ~132), add the sync section:

```kotlin
Spacer(modifier = Modifier.height(16.dp))

if (s.completed && state.icuConfigured) {
  if (s.icuSyncedAt != null) {
    Text(
      text = "Synced to intervals.icu · ${formatDate(s.icuSyncedAt!!)}",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary
    )
  } else {
    Button(
      onClick = { viewModel.uploadToIcu() },
      enabled = !state.isUploading,
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(if (state.isUploading) "Uploading…" else "Upload to intervals.icu")
    }
  }
  state.uploadStatus?.let {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = it,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
```

(Import `androidx.compose.material3.Button`. `formatDate` already exists at the bottom of this file.)

- [ ] **Step 3: Verify build + full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Manual end-to-end verification (device/emulator)**

1. With intervals.icu configured, finish (or simulate) a workout → complete screen shows "Uploaded to intervals.icu"; History card shows the synced badge; detail screen shows "Synced to intervals.icu · <date>".
2. Turn on airplane mode, finish a workout → complete screen shows "Upload failed — retry from History"; no badge in History.
3. Airplane mode off, open that session's detail → tap "Upload to intervals.icu" → status flips to uploaded, badge appears in History, activity visible on intervals.icu.
4. Tap-retry a session that already uploaded (e.g. after clearing `icuSyncedAt` manually or re-testing step 1's session before the marker existed): intervals.icu answers 422 duplicate → app reports success and marks it synced (this validates Task 2's duplicate handling against the real API; adjust the match string if the real body doesn't contain "duplicate").
5. Upgrade path: install the previous build (schema v1) with existing sessions, then install this build → sessions still listed (migration ran, no data loss), all un-synced.

- [ ] **Step 5: Commit**

```bash
git add -A android/app/src
git commit -m "feat(sync): manual upload-to-icu retry on session detail screen"
```

---

## Out of scope (deliberately)

- **Background auto-retry** (WorkManager job that retries un-synced sessions): skipped — the manual retry button covers the failure case with zero new machinery. Add a WorkManager sync job only if users demonstrably forget to retry.
- **Bulk "sync all" button in History**: one-tap-per-session is fine at current volumes; add when someone has a backlog.
- **Structured upload result type** (`Success/Duplicate/Failure` sealed class): a Boolean plus the duplicate-as-success rule is all callers need today.
