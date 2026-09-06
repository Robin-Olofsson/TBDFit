package com.tbdfit.phone.backend.profile

import android.util.Log
import com.tbdfit.phone.backend.SupabaseClientProvider
import com.tbdfit.phone.profile.Profile
import com.tbdfit.phone.profile.ProfileGateway
import com.tbdfit.phone.profile.UsernameUnavailableException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val TABLE_NAME = "profiles"
private const val LOG_TAG = "TBDFit.profile"

// Postgres SQLSTATE for unique_violation — what the database actually returns when
// profiles_normalized_username_key rejects an insert. See
// supabase/migrations/20260906120000_create_profiles.sql.
private const val UNIQUE_VIOLATION_CODE = "23505"

// Remote schema is defined under /supabase/migrations
// (20260906120000_create_profiles.sql) — that migration is the authoritative definition of this
// table, its RLS policies, and the uniqueness/syntax invariants it enforces. This Kotlin code is a
// consumer of that shared backend contract, not its source of truth.

// Read shape: user_id is present because the row already exists (server-assigned via
// `default auth.uid()` at insert time — never sent by this client).
@Serializable
private data class ProfileRow(
    @SerialName("user_id") val userId: String,
    val username: String,
)

// Insert shape: user_id deliberately absent — same reasoning as LocalRecordRemoteDto. The column
// defaults to auth.uid() and RLS enforces it, so the client cannot claim another user's row merely
// by naming a user_id, because it never sends one.
@Serializable
private data class NewProfileRow(val username: String)

// The only place in the app that knows Supabase exists for this capability (ADR-004 boundary).
class SupabaseProfileGateway(
    private val clientProvider: () -> SupabaseClient = { SupabaseClientProvider.client },
) : ProfileGateway {
    private val client: SupabaseClient get() = clientProvider()

    override suspend fun loadOwnProfile(): Result<Profile?> = runCatching {
        try {
            // No explicit user_id filter needed: RLS's "select own profile" policy already scopes
            // this to exactly the caller's own row (or none).
            val rows = client.postgrest[TABLE_NAME].select().decodeList<ProfileRow>()
            rows.firstOrNull()?.let { Profile(userId = it.userId, username = it.username) }
        } catch (e: PostgrestRestException) {
            Log.e(LOG_TAG, "load profile failed status=${e.statusCode} code=${e.code} error=${e.error}")
            throw e
        } catch (e: RestException) {
            Log.e(LOG_TAG, "load profile failed status=${e.statusCode} error=${e.error}")
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "load profile failed exceptionType=${e::class.simpleName}")
            throw e
        }
    }

    // Reads the signup-time desired_username hint straight from the current Supabase session's
    // user metadata — Supabase-specific (JsonObject, currentSessionOrNull) details stay entirely
    // in this file; ProfileGateway's contract only ever exposes the resulting plain String?. This
    // is a local, already-cached read (no network round trip), so it cannot meaningfully fail —
    // any absence (no session, no metadata, no key) is just null, the normal case for a
    // pre-existing or Google-first account.
    override suspend fun loadDesiredUsernameHint(): String? =
        client.auth.currentSessionOrNull()?.user?.userMetadata?.get("desired_username")?.jsonPrimitive?.contentOrNull

    override suspend fun createOwnProfile(username: String): Result<Profile> = runCatching {
        try {
            val created = client.postgrest[TABLE_NAME]
                .insert(NewProfileRow(username = username)) { select() }
                .decodeSingle<ProfileRow>()
            Profile(userId = created.userId, username = created.username)
        } catch (e: PostgrestRestException) {
            if (e.code == UNIQUE_VIOLATION_CODE) {
                Log.w(LOG_TAG, "create profile rejected: username unavailable")
                throw UsernameUnavailableException()
            }
            Log.e(LOG_TAG, "create profile failed status=${e.statusCode} code=${e.code} error=${e.error}")
            throw e
        } catch (e: RestException) {
            Log.e(LOG_TAG, "create profile failed status=${e.statusCode} error=${e.error}")
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "create profile failed exceptionType=${e::class.simpleName}")
            throw e
        }
    }
}
