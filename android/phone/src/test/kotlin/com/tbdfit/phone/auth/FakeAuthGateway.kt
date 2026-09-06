package com.tbdfit.phone.auth

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthGateway(initial: AuthState = AuthState.SignedOut) : AuthGateway {
    override val state = MutableStateFlow(initial)

    var signOutCalls = 0
        private set

    var lastDesiredUsername: String? = null
        private set

    override suspend fun signUp(email: String, password: String, desiredUsername: String?): Result<Unit> {
        lastDesiredUsername = desiredUsername
        return Result.success(Unit)
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)

    override suspend fun signOut(): Result<Unit> {
        signOutCalls++
        return Result.success(Unit)
    }

    override suspend fun retryRestoringSession(): Result<Unit> = Result.success(Unit)

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<Unit> = Result.success(Unit)

    override suspend fun resendEmailVerification(email: String): Result<Unit> = Result.success(Unit)

    var handledDeepLinks = 0
        private set

    override fun handleAuthDeepLink(intent: Intent) {
        handledDeepLinks++
    }
}
