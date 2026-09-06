package com.tbdfit.phone.backend.auth

import android.content.Intent
import android.util.Log
import com.tbdfit.phone.auth.AuthGateway
import com.tbdfit.phone.auth.AuthSession
import com.tbdfit.phone.auth.AuthState
import com.tbdfit.phone.backend.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val LOG_TAG = "TBDFit.auth"

// The only place in the app that knows Supabase Auth exists for this capability (ADR-004
// boundary). Maps Supabase's own SessionStatus onto the app's narrow AuthState, and Supabase's
// exceptions onto plain, safe, user-facing failure messages — nothing Supabase-shaped crosses
// this boundary in either direction.
//
// The client is resolved lazily via clientProvider, same reasoning as
// SupabaseLocalRecordRemoteStore: this type is constructed eagerly in MainActivity.onCreate(), and
// a bad/missing Supabase config must not crash the whole app at launch — only fail when an auth
// action or state read is actually attempted.
class SupabaseAuthGateway(
    private val clientProvider: () -> SupabaseClient = { SupabaseClientProvider.client },
) : AuthGateway {
    private val client: SupabaseClient get() = clientProvider()

    override val state: Flow<AuthState>
        get() = client.auth.sessionStatus.map(::toAuthState)

    override suspend fun signUp(email: String, password: String, desiredUsername: String?): Result<Unit> = runCatching {
        Log.d(LOG_TAG, "sign-up starting")
        runAuthCall {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                // Carries onboarding intent across the email-verification boundary only — see
                // AuthGateway.signUp's doc comment. Retrieved later exclusively via
                // ProfileGateway.loadDesiredUsernameHint(), never through AuthSession/AuthState —
                // see the profile/username hardening report. Never logged (a username isn't a
                // credential, but there's no reason to write it to Logcat either).
                if (desiredUsername != null) {
                    this.data = buildJsonObject { put("desired_username", desiredUsername) }
                }
            }
        }
        Log.d(LOG_TAG, "sign-up request completed")
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        Log.d(LOG_TAG, "sign-in starting")
        runAuthCall { client.auth.signInWith(Email) { this.email = email; this.password = password } }
        Log.d(LOG_TAG, "sign-in request completed")
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        Log.d(LOG_TAG, "sign-out starting")
        runAuthCall { client.auth.signOut() }
        Log.d(LOG_TAG, "sign-out request completed")
    }

    override suspend fun retryRestoringSession(): Result<Unit> = runCatching {
        Log.d(LOG_TAG, "session retry starting")
        runAuthCall { client.auth.refreshCurrentSession() }
        Log.d(LOG_TAG, "session retry request completed")
    }

    // Native ID-token flow (not the OAuth browser-redirect flow): idToken/rawNonce were already
    // obtained on-device via Credential Manager (see GoogleIdTokenProvider) before this is called.
    // Supabase verifies the token's signature/audience itself — no browser round-trip involved.
    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<Unit> = runCatching {
        Log.d(LOG_TAG, "google sign-in starting")
        runAuthCall {
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                this.provider = Google
                this.nonce = rawNonce
            }
        }
        Log.d(LOG_TAG, "google sign-in request completed")
    }

    override suspend fun resendEmailVerification(email: String): Result<Unit> = runCatching {
        Log.d(LOG_TAG, "resend verification email starting")
        runAuthCall { client.auth.resendEmail(OtpType.Email.SIGNUP, email) }
        Log.d(LOG_TAG, "resend verification email request completed")
    }

    // The only place handleDeeplinks (a Supabase Auth SDK extension function) is called — see
    // AuthGateway.handleAuthDeepLink for why this still takes a plain Intent. handleDeeplinks
    // itself checks the intent's scheme/host against the Auth plugin's own config and returns
    // immediately if they don't match, so calling this unconditionally for every intent
    // (launcher intents included) is safe by construction, not something this class needs to
    // pre-filter itself.
    override fun handleAuthDeepLink(intent: Intent) {
        client.handleDeeplinks(
            intent = intent,
            onSessionSuccess = { session -> Log.d(LOG_TAG, "auth deep link established session uid=${session.user?.id}") },
            onError = { e -> logDeepLinkFailure(e) },
        )
    }

    // Deliberately not logging e.message directly for RestException-family failures — same
    // reasoning as LocalRecordSyncCoordinator's remote-failure logging: the inherited
    // Throwable.message on those types is a multi-line dump that includes the request URL and
    // (masked, but still present) header metadata.
    private fun logDeepLinkFailure(e: Throwable) {
        when (e) {
            is AuthRestException -> Log.e(
                LOG_TAG,
                "auth deep link failed status=${e.statusCode} errorCode=${e.errorCode} description=${e.errorDescription}",
            )
            is RestException -> Log.e(LOG_TAG, "auth deep link failed status=${e.statusCode} error=${e.error}")
            else -> Log.e(LOG_TAG, "auth deep link failed exceptionType=${e::class.simpleName}")
        }
    }

    // Same three-tier catch pattern as SupabaseLocalRecordRemoteStore.upsert: most specific type
    // first (carries a structured, safe-to-log error code), then the general REST exception, then
    // a last-resort catch for network/IO-level failures (no response was ever received). Never
    // logs or surfaces raw exception text — only structured fields or the mapped message below.
    private suspend fun runAuthCall(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: AuthRestException) {
            Log.e(
                LOG_TAG,
                "auth call failed status=${e.statusCode} errorCode=${e.errorCode} description=${e.errorDescription}",
            )
            throw IllegalStateException(messageFor(e.errorCode))
        } catch (e: RestException) {
            Log.e(LOG_TAG, "auth call failed status=${e.statusCode} error=${e.error}")
            throw IllegalStateException("Something went wrong. Please try again.")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "auth call failed exceptionType=${e::class.simpleName} message=${e.message}")
            throw IllegalStateException("Couldn't reach the server. Check your connection and try again.")
        }
    }
}

private fun messageFor(code: AuthErrorCode?): String = when (code) {
    AuthErrorCode.InvalidCredentials -> "Incorrect email or password."
    AuthErrorCode.WeakPassword -> "Password is too weak."
    AuthErrorCode.EmailNotConfirmed -> "Please confirm your email before logging in."
    AuthErrorCode.OverEmailSendRateLimit -> "Too many attempts. Please wait and try again."
    AuthErrorCode.ValidationFailed -> "Please check your email and password and try again."
    AuthErrorCode.UserAlreadyExists -> "An account with this email may already exist. Try logging in instead."
    AuthErrorCode.IdentityAlreadyExists -> "This Google account is already linked to a different TBDFit account."
    else -> "Something went wrong. Please try again."
}

// A pure, deterministic translation with no Supabase network/IO involved — kept as a standalone,
// internal top-level function specifically so it stays testable without faking Supabase (see
// AuthStateMappingTest, which constructs real SessionStatus/UserSession/UserInfo values).
//
// RefreshFailure means "a stored session exists but could not currently be refreshed" — supabase-kt
// itself keeps retrying refresh in this state rather than treating it as an explicit logout, so it
// is NOT the same fact as NotAuthenticated. Correction (see phone-authentication follow-up):
// mapping it to SignedOut previously conflated "refresh failed" with "user signed out," which would
// incorrectly show the login screen and made it eligible for logout-style local data clearing for
// what may be a purely transient (e.g. offline) condition. It now maps to its own SessionUnavailable
// state instead — Supabase's RefreshFailure type itself still never crosses this boundary.
//
// Deliberately identity/session-only: this mapping never reads user metadata for anything beyond
// id/email. The signup-time desired_username hint is real, but it's profile-onboarding intent, not
// authentication/session state — reading it back is ProfileGateway.loadDesiredUsernameHint()'s
// job, implemented in SupabaseProfileGateway, not here. See the profile/username hardening report
// for why an earlier pass got this boundary wrong.
internal fun toAuthState(status: SessionStatus): AuthState = when (status) {
    is SessionStatus.Initializing -> AuthState.Unknown
    is SessionStatus.NotAuthenticated -> AuthState.SignedOut
    is SessionStatus.RefreshFailure -> AuthState.SessionUnavailable
    is SessionStatus.Authenticated -> AuthState.SignedIn(
        AuthSession(userId = status.session.user?.id.orEmpty(), email = status.session.user?.email),
    )
}
