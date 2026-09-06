package com.tbdfit.phone.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// The old technical-proof email/password-only screen (single field pair, Log in / Create account
// buttons) has been superseded by SignedOutScreen/AuthLandingScreen/EmailAuthScreen — the real
// TBDFit landing + email flow with Google auth and email verification. See the phone-authentication
// report for the design.
//
// The old identity-only ProfileHeader (just email/userId + logout) has likewise been superseded by
// com.tbdfit.phone.profile.ProfileSummaryHeader, which shows the real TBDFit username now that a
// Profile capability exists. It lives in the profile package, not here, so that auth stays
// unaware of Profile (dependency points one way: profile depends on auth's AuthGateway/AuthSession
// types, never the reverse).

// Shown for AuthState.SessionUnavailable — deliberately NOT the login/create-account screen: a
// persisted session exists but couldn't currently be verified, which is not the same fact as the
// user having signed out. No Logout affordance appears here on purpose, since this screen must
// never be able to trigger logout-style local account-data clearing (see
// LocalAccountDataReset.kt) — only an explicit, confirmed logout may do that.
@Composable
fun SessionUnavailableScreen(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text("Unable to restore your online session. Check your connection and retry.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
