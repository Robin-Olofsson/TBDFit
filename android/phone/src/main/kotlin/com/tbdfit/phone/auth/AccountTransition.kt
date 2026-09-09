package com.tbdfit.phone.auth

import com.tbdfit.phone.localstorage.LocalRecordDao
import com.tbdfit.phone.wearreplication.WearReplicaDao

// The one deliberate account-transition action in this slice: a user-initiated logout. This is
// the ONLY call site for clearAccountScopedTechnicalProofData, by design — a transient
// AuthState.SessionUnavailable must never reach this function, since it is not a logout (see
// LocalAccountDataReset.kt). Named and extracted specifically so that fact is checkable in one
// place instead of buried inside a Compose lambda.
//
// Also clears LastSignedInAccountCache (local-workout-ownership audit) — not because leaving it
// stale is known to leak anything today (no code path currently reads it outside
// AuthState.SessionUnavailable, which cannot follow a genuine SignedOut without an intervening
// SignedIn that would overwrite it anyway), but so correctness does not rest on that being true
// forever. Workout rows themselves are deliberately NOT cleared/deleted here — see
// clearAccountScopedTechnicalProofData's own doc comment: durable workout data must be
// scoped/filtered by owner, never destroyed on logout.
suspend fun performLogout(
    authGateway: AuthGateway,
    localRecordDao: LocalRecordDao,
    wearReplicaDao: WearReplicaDao,
    lastSignedInAccountCache: LastSignedInAccountCache,
) {
    authGateway.signOut()
    clearAccountScopedTechnicalProofData(localRecordDao, wearReplicaDao)
    lastSignedInAccountCache.clear()
}
