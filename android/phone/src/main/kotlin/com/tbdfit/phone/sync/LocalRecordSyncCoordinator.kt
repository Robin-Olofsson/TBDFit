package com.tbdfit.phone.sync

import android.util.Log
import com.tbdfit.phone.localstorage.LocalRecordDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val LOG_TAG = "TBDFit.sync"

// The one application-level responsibility for this capability: find locally-pending records,
// attempt remote replication, and record acknowledgement on success. A failed attempt leaves the
// local record exactly as it was — pending, present, unchanged — never deleted or rolled back.
// Remote sync is replication of an already-durable local fact, not the durability boundary.
//
// This is deliberately scoped to one entity/capability. If a second synced entity later needs the
// same shape, that is the point to consider generalizing — not before.
//
// The Mutex makes sync() single-flight for this one coordinator instance: a second concurrent call
// (e.g. a fast double-tap of "Sync now") waits for the first to finish, then re-reads pending state
// itself, so already-synced records are never resubmitted. This is not a generic lock/sync engine —
// it's scoped to exactly this coordinator's own operation, owned by this instance alone.
class LocalRecordSyncCoordinator(
    private val dao: LocalRecordDao,
    private val remoteStore: LocalRecordRemoteStore,
) {
    private val mutex = Mutex()

    suspend fun sync() = mutex.withLock {
        val pending = dao.getPending()
        Log.d(LOG_TAG, "sync requested pendingCount=${pending.size}")

        pending.forEach { record ->
            Log.d(LOG_TAG, "syncing id=${record.id}")
            remoteStore.upsert(record)
                .onSuccess {
                    Log.d(LOG_TAG, "remote success id=${record.id}")
                    Log.d(LOG_TAG, "markSynced start id=${record.id}")
                    try {
                        dao.markSynced(record.id, System.currentTimeMillis())
                        Log.d(LOG_TAG, "markSynced success id=${record.id}")
                    } catch (e: Exception) {
                        // Distinguishes "remote succeeded but the local acknowledgement failed"
                        // from either "remote never called" or "remote failed" — the record is
                        // still correctly pending locally in this case, just not yet acknowledged.
                        // e.message is safe here: this is a local Room/SQLite exception, not a
                        // Supabase RestException (see upsert()'s failure log below for why that
                        // distinction matters).
                        Log.e(LOG_TAG, "markSynced failed id=${record.id} exceptionType=${e::class.simpleName} message=${e.message}")
                    }
                }
                .onFailure { e ->
                    // Deliberately NOT logging e.message here: for Supabase's RestException family,
                    // the inherited Throwable.message is a multi-line dump that includes the
                    // request URL and (masked, but still present) header metadata — see
                    // SupabaseLocalRecordRemoteStore, which already logs the safe, structured
                    // fields (status/code/error/hint/details) at the actual point of failure. This
                    // log is only for coordinator-level visibility that a failure happened.
                    Log.w(LOG_TAG, "remote failure id=${record.id} exceptionType=${e::class.simpleName}")
                }
        }
    }
}
