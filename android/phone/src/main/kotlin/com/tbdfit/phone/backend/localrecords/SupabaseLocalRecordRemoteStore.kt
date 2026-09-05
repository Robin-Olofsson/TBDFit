package com.tbdfit.phone.backend.localrecords

import android.util.Log
import com.tbdfit.phone.backend.SupabaseClientProvider
import com.tbdfit.phone.localstorage.LocalRecordEntity
import com.tbdfit.phone.sync.LocalRecordRemoteStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TABLE_NAME = "local_records"
private const val LOG_TAG = "TBDFit.sync"

// Remote schema is defined under /supabase/migrations (see
// 20260905120000_create_local_records.sql) — that migration is the authoritative definition of
// this table, its RLS policies, and the security invariant it enforces. This Kotlin code is a
// consumer of that shared backend contract, not its source of truth; it must not duplicate the
// SQL here.

// Supabase-facing shape only (ADR-004 capability boundary) — never used outside this file.
// `user_id` is deliberately absent: the remote column defaults to auth.uid() and RLS enforces it,
// so ownership is derived server-side rather than trusted from whatever this DTO could carry. The
// client cannot gain ownership of a row merely by naming a user_id, because it never sends one.
@Serializable
private data class LocalRecordRemoteDto(
    val id: String,
    @SerialName("created_at") val createdAt: Long,
    val value: String,
)

// The only place in the app that knows Supabase exists for this capability. Room's
// LocalRecordEntity and the LocalRecordRemoteStore interface stay Supabase-agnostic; this class is
// the translation point between them.
//
// Retry invariant: retrying the same LocalRecord creation with the same id may safely result in a
// no-op remotely (id is the primary key; upsert uses ignoreDuplicates). This is approved ONLY
// because LocalRecord is create-once and immutable for this technical proof. Do NOT generalize
// this upsert-by-id-ignore-duplicates behavior to future mutable entities (workout plans, profile,
// preferences, editable workouts) — those need their own versioning/conflict semantics, decided
// independently when they're designed.
//
// The client is resolved lazily (on first actual use inside upsert/ensureSignedIn), not at
// construction time: this type is constructed eagerly in MainActivity.onCreate(), and local record
// creation must keep working even if Supabase is unreachable or misconfigured (PD-002's
// local-first invariant applies here too, not only to workout execution). Resolving the client
// eagerly would mean a bad Supabase config crashes the whole app at launch instead of only failing
// sync when it's actually attempted.
class SupabaseLocalRecordRemoteStore(
    private val clientProvider: () -> SupabaseClient = { SupabaseClientProvider.client },
) : LocalRecordRemoteStore {
    private val client: SupabaseClient get() = clientProvider()

    override suspend fun upsert(record: LocalRecordEntity): Result<Unit> = runCatching {
        Log.d(LOG_TAG, "remote write starting id=${record.id} createdAt=${record.createdAt}")
        ensureSignedIn()
        val dto = LocalRecordRemoteDto(id = record.id, createdAt = record.createdAt, value = record.value)
        try {
            client.postgrest[TABLE_NAME].upsert(dto) {
                onConflict = "id"
                ignoreDuplicates = true
            }
            Log.d(LOG_TAG, "remote write succeeded id=${record.id}")
        } catch (e: PostgrestRestException) {
            // Most specific: carries the actual Postgres/PostgREST error code, hint, and details
            // (e.g. an RLS policy rejection) — exactly what distinguishes "RLS blocked this" from
            // any other failure.
            Log.e(
                LOG_TAG,
                "remote write failed id=${record.id} status=${e.statusCode} code=${e.code} " +
                    "error=${e.error} hint=${e.hint} details=${e.details}",
            )
            throw e
        } catch (e: RestException) {
            Log.e(
                LOG_TAG,
                "remote write failed id=${record.id} status=${e.statusCode} error=${e.error} " +
                    "description=${e.description}",
            )
            throw e
        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "remote write failed id=${record.id} exceptionType=${e::class.simpleName} message=${e.message}",
            )
            throw e
        }
    }

    // Smallest auth flow that still exercises real, non-bypassed Supabase Auth: anonymous sign-in
    // is a first-class Supabase Auth feature, not a workaround, and every row is still owned by a
    // real authenticated identity. Product-facing login (email/social) is a separate later
    // decision — this exists only to correctly test authenticated sync.
    private suspend fun ensureSignedIn() {
        val existing = client.auth.currentSessionOrNull()
        if (existing != null) {
            Log.d(LOG_TAG, "existing session uid=${existing.user?.id}")
            return
        }

        Log.d(LOG_TAG, "no session -> anonymous sign-in starting")
        try {
            client.auth.signInAnonymously()
        } catch (e: RestException) {
            Log.e(LOG_TAG, "anonymous sign-in failed status=${e.statusCode} error=${e.error} description=${e.description}")
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "anonymous sign-in failed exceptionType=${e::class.simpleName} message=${e.message}")
            throw e
        }

        // Only the UID is logged: a Supabase user id is not sensitive, but bearer credentials
        // (access/refresh tokens) must never be written to Logcat, even temporarily.
        val uid = client.auth.currentSessionOrNull()?.user?.id
        Log.d(LOG_TAG, "anonymous sign-in succeeded uid=$uid")
    }
}
