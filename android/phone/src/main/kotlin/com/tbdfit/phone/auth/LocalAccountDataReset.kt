package com.tbdfit.phone.auth

import com.tbdfit.phone.localstorage.LocalRecordDao
import com.tbdfit.phone.wearreplication.WearReplicaDao

// On sign-out, clears technical-proof local data (LocalRecord, WearReplica) so the next signed-in
// account on this device never sees a previous account's rows — Room has no concept of "current
// user" today, so without this, one account's locally-visible rows would leak into the next
// account signed in on the same device.
//
// This blunt delete-everything approach is acceptable ONLY because both tables are explicitly
// disposable technical-proof data (see docs/development/supabase-setup-and-verification.md). Real
// future workout history must NOT be cleared this way on logout — that would be a data-loss bug,
// not a privacy fix. It must instead be scoped/filtered by the currently authenticated identity.
// Future workout account-isolation / unsynced-data policy is a separate, not-yet-made decision —
// this function's behavior must not be read as establishing a precedent for it.
//
// Call-site boundary: this must only ever run as part of an explicit, confirmed account
// transition (the user pressing "Logout" — see performLogout in AccountTransition.kt). It must
// NEVER run for AuthState.SessionUnavailable, which represents an unverified but not-necessarily-
// lost session, not a logout. Do not wire this to session-state observation in general — only to
// a deliberate, user-initiated transition.
suspend fun clearAccountScopedTechnicalProofData(
    localRecordDao: LocalRecordDao,
    wearReplicaDao: WearReplicaDao,
) {
    localRecordDao.clearAll()
    wearReplicaDao.clearAll()
}
