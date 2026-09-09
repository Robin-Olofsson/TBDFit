package com.tbdfit.phone.auth

import android.content.Context

// A tiny local cache of "which account was last confirmed signed in on this device" — exists
// solely so the SessionUnavailable local-workout exception (see SessionUnavailableWorkoutAccess.kt
// and MainActivity's SessionUnavailableOrLocalWorkoutScreen) knows which account's local ACTIVE
// workout to resume, since AuthState.SessionUnavailable itself carries no session payload — see
// the local-workout-ownership audit report for why an unscoped fallback query would have
// reintroduced exactly the cross-account leak that audit exists to close.
//
// This is NOT an authentication mechanism and grants no access to anything remote — it only scopes
// which already-durable, local Room rows are shown, and requires no network call to read or write.
// Recorded whenever AuthState.SignedIn is actually observed; cleared only on an explicit, confirmed
// logout (see performLogout) — never on SessionUnavailable itself, since that state's entire
// purpose is representing continuity of the same session through a temporary verification failure,
// not a different account arriving.
class LastSignedInAccountCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordSignedIn(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_USER_ID).apply()
    }

    fun lastKnownUserId(): String? = prefs.getString(KEY_USER_ID, null)

    private companion object {
        const val PREFS_NAME = "tbdfit_last_signed_in_account"
        const val KEY_USER_ID = "user_id"
    }
}
