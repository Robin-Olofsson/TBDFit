package com.tbdfit.phone.auth

import com.tbdfit.phone.localstorage.LocalRecordDao
import com.tbdfit.phone.wearreplication.WearReplicaDao

// The one deliberate account-transition action in this slice: a user-initiated logout. This is
// the ONLY call site for clearAccountScopedTechnicalProofData, by design — a transient
// AuthState.SessionUnavailable must never reach this function, since it is not a logout (see
// LocalAccountDataReset.kt). Named and extracted specifically so that fact is checkable in one
// place instead of buried inside a Compose lambda.
suspend fun performLogout(
    authGateway: AuthGateway,
    localRecordDao: LocalRecordDao,
    wearReplicaDao: WearReplicaDao,
) {
    authGateway.signOut()
    clearAccountScopedTechnicalProofData(localRecordDao, wearReplicaDao)
}
