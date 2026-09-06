package com.tbdfit.phone.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localstorage.AppDatabase
import com.tbdfit.phone.localstorage.LocalRecordEntity
import com.tbdfit.phone.wearreplication.WearReplicaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

// Proves the concrete account-isolation invariant identified in the phone-authentication report:
// on logout, technical-proof local data must not remain visible to whichever account signs in
// next on this device. This is real Room (Robolectric), not a fake of Supabase — Supabase itself
// is not involved in this behavior at all, which is exactly why it's safe to unit-test directly.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAccountDataResetTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "account-reset-test-${UUID.randomUUID()}.db"
        db = AppDatabase.build(context, dbName)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun clearingLeavesNoLocalRecordsOrWearReplicasBehind() = runTest {
        db.localRecordDao().insert(
            LocalRecordEntity(id = UUID.randomUUID().toString(), createdAt = 1L, value = "account A's data"),
        )
        db.wearReplicaDao().insertIfAbsent(
            WearReplicaEntity(id = UUID.randomUUID().toString(), createdAt = 1L, value = "account A's data", receivedAt = 1L),
        )

        clearAccountScopedTechnicalProofData(db.localRecordDao(), db.wearReplicaDao())

        assertTrue(db.localRecordDao().getAll().first().isEmpty())
        assertTrue(db.wearReplicaDao().getAll().first().isEmpty())
    }

    @Test
    fun clearingWhenAlreadyEmptyIsSafe() = runTest {
        clearAccountScopedTechnicalProofData(db.localRecordDao(), db.wearReplicaDao())

        assertTrue(db.localRecordDao().getAll().first().isEmpty())
        assertTrue(db.wearReplicaDao().getAll().first().isEmpty())
    }
}
