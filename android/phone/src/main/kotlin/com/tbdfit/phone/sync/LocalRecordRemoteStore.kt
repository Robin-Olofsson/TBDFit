package com.tbdfit.phone.sync

import com.tbdfit.phone.localstorage.LocalRecordEntity

// The narrow boundary the sync coordinator depends on. Whichever backend capability implements
// this (see ADR-004) owns all Supabase-specific SDK/schema/query details — nothing in this
// package, or in localstorage, may know Supabase exists.
interface LocalRecordRemoteStore {
    suspend fun upsert(record: LocalRecordEntity): Result<Unit>
}

// Thrown by an implementation when no authenticated session is available to perform the remote
// write. A remote store must fail with this rather than manufacture an identity of its own (e.g.
// by signing in anonymously) — establishing/restoring a session belongs to the auth capability
// alone (see com.tbdfit.phone.auth), never to a remote data capability. The sync coordinator
// treats this exactly like any other remote failure: the local record is left pending, never
// marked synced, never deleted.
class NoAuthenticatedSessionException : Exception("No authenticated session available for remote sync")
