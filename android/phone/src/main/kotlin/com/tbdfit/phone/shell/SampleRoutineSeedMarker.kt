package com.tbdfit.phone.shell

import android.content.Context

// Tracks which accounts have already received the one-time sample-Routine seed (see
// seedSampleRoutinesIfAbsent in RoutineLibraryScreens.kt) — Slice A of
// program-routine-first-slice-design.md's least-invasive prototype→real bridge (§28-29 of the
// Slice A implementation brief).
//
// Deliberately NOT based on "does this owner currently have zero routines": a user who deliberately
// deletes all their sample routines must not have them silently reappear the next time this screen
// renders. This is local UI-layer bootstrap bookkeeping only, not a domain concept — no Room table
// for it, the same reasoning as LastSignedInAccountCache being a plain SharedPreferences file
// rather than a persisted entity.
class SampleRoutineSeedMarker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSeeded(ownerId: String): Boolean =
        prefs.getStringSet(KEY_SEEDED_OWNERS, emptySet())?.contains(ownerId) == true

    fun markSeeded(ownerId: String) {
        val updated = (prefs.getStringSet(KEY_SEEDED_OWNERS, emptySet()) ?: emptySet()).toMutableSet()
        updated.add(ownerId)
        prefs.edit().putStringSet(KEY_SEEDED_OWNERS, updated).apply()
    }

    private companion object {
        const val PREFS_NAME = "tbdfit_sample_routine_seed"
        const val KEY_SEEDED_OWNERS = "seeded_owners"
    }
}
