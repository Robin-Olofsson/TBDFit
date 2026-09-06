package com.tbdfit.phone.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

data class GoogleCredentialResult(val idToken: String, val rawNonce: String)

// Thrown when Credential Manager returns a credential that isn't the Google ID-token type we
// asked for. Should not happen in practice given the request contains only a
// GetSignInWithGoogleOption, but is checked defensively per Google's own examples rather than
// handed unchecked to GoogleIdTokenCredential.createFrom. Never carries the raw credential data.
class UnexpectedCredentialTypeException : Exception("Unexpected credential type returned")

// The only file in the app that touches Android Credential Manager / Google Identity SDK types —
// keeps AuthGateway and Compose UI free of them; they only ever see a plain idToken/nonce string
// pair, the same way they only ever see plain email/password strings for the email provider. Not
// a generic "credential provider" abstraction: this exists for Google specifically, because it's
// the only provider here that needs an on-device credential picker. Shared unchanged by both UI
// entry points (landing screen, verification-screen Google escape path) via SignedOutScreen's one
// onContinueWithGoogle closure — this class itself has no notion of which entry point called it.
//
// Uses GetSignInWithGoogleOption, not GetGoogleIdOption: this is the current Google-recommended
// API specifically for an explicit, user-tapped "Continue with Google" button. Confirmed present
// in the exact pinned googleid:1.2.0 artifact (verified by inspecting its compiled classes, not
// merely assumed from docs) — no dependency change needed. Unlike GetGoogleIdOption, it has no
// setFilterByAuthorizedAccounts/setAutoSelectEnabled: it always shows the full account chooser and
// never auto-selects or silently signs in, which is the deliberate, unchanged product behavior
// here — the signed-out landing page stays explicitly user-driven. Existing Supabase session
// restoration remains solely responsible for returning previously authenticated users directly to
// SignedIn before this screen ever renders.
//
// webClientId must be the OAuth 2.0 Web application Client ID from Google Cloud Console — not the
// Android Client ID — per Supabase's and Google's own current guidance for this native ID-token
// flow. It's client-safe configuration, not a secret (same tier as the Supabase anon key): read
// from local.properties/BuildConfig, not hardcoded, but not treated as confidential.
class GoogleIdTokenProvider(private val webClientId: String) {
    suspend fun requestIdToken(context: Context): Result<GoogleCredentialResult> = runCatching {
        val rawNonce = UUID.randomUUID().toString()
        // Nonce semantics are unchanged and already verified against both Supabase's Android
        // documentation and the Supabase Auth server's own nonce-validation source: the SHA-256
        // hash goes to Google (ends up as the ID token's own nonce claim), the raw value goes to
        // Supabase (which independently re-hashes it to check for a match) — see
        // SupabaseAuthGateway.signInWithGoogle.
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(context).getCredential(context, request)

        val credential = response.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw UnexpectedCredentialTypeException()
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)

        GoogleCredentialResult(idToken = googleCredential.idToken, rawNonce = rawNonce)
    }
}

// Maps a Credential Manager failure to a safe, user-facing message — or null when no message
// should be shown at all (the user simply dismissed the account chooser, which is not an error
// worth surfacing). Never surfaces raw exception text or credential contents.
fun googleCredentialFailureMessage(e: Throwable): String? = when (e) {
    is GetCredentialCancellationException -> null
    is NoCredentialException -> "No Google account is available on this device."
    is GetCredentialException -> "Google sign-in isn't available right now. Please try again."
    else -> "Something went wrong. Please try again."
}
