package com.tbdfit.phone.localaccount

import androidx.room.Entity
import androidx.room.PrimaryKey

// The smallest local identity root that owner-scoped local data (Workout, custom Exercise) needs —
// see the local-account-ownership correction to the earlier ownerId-as-free-string audit fix. This
// is deliberately NOT an authentication/session table: com.tbdfit.phone.auth.AuthState/AuthSession
// already own that concern, and com.tbdfit.phone.auth.LastSignedInAccountCache already owns "which
// account to route SessionUnavailable to" — this table's only job is giving Room/SQLite something
// real to declare a foreign key against, since a Supabase `auth.users` row lives in a separate
// backend database and can never be referenced by a local FK directly.
//
// `id` is the same stable backend-authenticated user id already used everywhere else in this
// codebase as "the account" (AuthSession.userId). A row existing here means only "this account id
// has been used to own local data on this device at least once" — it implies nothing about whether
// that account currently has a valid/verified backend session. A local account row must never be
// treated as proof of a valid session.
//
// No delete path is exposed anywhere in the app for this table on purpose — see WorkoutEntity's and
// ExerciseEntity's own doc comments for why their ownerId foreign keys use ON DELETE RESTRICT rather
// than CASCADE: a LocalAccount row disappearing must never silently take a user's workout or custom
// exercise history down with it.
@Entity(tableName = "local_accounts")
data class LocalAccountEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
)
