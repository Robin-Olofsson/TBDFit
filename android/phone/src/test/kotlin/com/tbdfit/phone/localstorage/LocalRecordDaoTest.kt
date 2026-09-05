package com.tbdfit.phone.localstorage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

// Runs against Robolectric's real (non-fake) SQLite implementation on the JVM — the strongest
// automated verification available without a physical device/emulator (this sandbox has no
// hardware virtualization, so a real Android emulator cannot run here; see reported manual
// verification steps for the real process-death check).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalRecordDaoTest {
    private lateinit var context: Context
    private lateinit var dbName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun insertedRecordIsReadableFromTheSameDatabaseInstance() = runTest {
        val db = AppDatabase.build(context, dbName)
        val record = LocalRecordEntity(
            id = UUID.randomUUID().toString(),
            createdAt = 1_700_000_000_000L,
            value = "same-instance",
        )

        db.localRecordDao().insert(record)
        val all = db.localRecordDao().getAll().first()

        assertEquals(1, all.size)
        assertEquals(record, all.first())
        db.close()
    }

    @Test
    fun recordCommittedByOneInstanceIsReadableFromAFreshInstanceAgainstTheSameFile() = runTest {
        val record = LocalRecordEntity(
            id = UUID.randomUUID().toString(),
            createdAt = 1_700_000_000_001L,
            value = "cross-instance",
        )

        // Instance A: commit, then close — simulates the object graph going away.
        val dbA = AppDatabase.build(context, dbName)
        dbA.localRecordDao().insert(record)
        dbA.close()

        // Instance B: a brand-new database object opened against the same on-disk file — the
        // strongest available proxy in this environment for "process died and restarted."
        val dbB = AppDatabase.build(context, dbName)
        val all = dbB.localRecordDao().getAll().first()

        assertTrue(all.contains(record))
        dbB.close()
    }
}
