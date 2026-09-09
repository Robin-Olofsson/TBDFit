package com.tbdfit.phone.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localstorage.AppDatabase
import com.tbdfit.phone.localstorage.LocalRecordEntity
import com.tbdfit.phone.wearreplication.WearReplicaEntity
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

// Proves the positive direction of the data-reset boundary: an explicit logout (performLogout) —
// and only performLogout — signs out and clears technical-proof local data. The negative
// direction (AuthState.SessionUnavailable must never reach this) is enforced structurally, not by
// this test: SessionUnavailableScreen has no Logout affordance and no code path calls
// performLogout except that button's onClick in MainActivity — there is no Compose UI test
// harness in this project to exercise that wiring directly, and adding one is out of scope here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformLogoutTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "perform-logout-test-${UUID.randomUUID()}.db"
        db = AppDatabase.build(context, dbName)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun logoutSignsOutAndClearsTechnicalProofData() = runTest {
        db.localRecordDao().insert(LocalRecordEntity(id = UUID.randomUUID().toString(), createdAt = 1L, value = "v"))
        db.wearReplicaDao().insertIfAbsent(
            WearReplicaEntity(id = UUID.randomUUID().toString(), createdAt = 1L, value = "v", receivedAt = 1L),
        )
        val gateway = FakeAuthGateway(initial = AuthState.SignedIn(AuthSession("uid-1", "user@example.com")))
        val accountCache = LastSignedInAccountCache(context)
        accountCache.recordSignedIn("uid-1")

        performLogout(gateway, db.localRecordDao(), db.wearReplicaDao(), accountCache)

        assertEquals(1, gateway.signOutCalls)
        assertTrue(db.localRecordDao().getAll().first().isEmpty())
        assertTrue(db.wearReplicaDao().getAll().first().isEmpty())
        assertEquals(null, accountCache.lastKnownUserId())
    }
}
