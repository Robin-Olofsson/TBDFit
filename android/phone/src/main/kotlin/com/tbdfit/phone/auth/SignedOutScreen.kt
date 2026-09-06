package com.tbdfit.phone.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

// Landing has no internal back target — system back from there is normal Android behavior
// (typically exiting), not a transition this screen owns. Email does: back returns to Landing.
// Extracted as a pure function so this is directly testable — see SignedOutNavigationTest.
internal fun hasInternalBackTarget(step: SignedOutStep): Boolean = step == SignedOutStep.Email

// Hosts the two signed-out screens (Landing, Email) behind one horizontal-slide transition. Uses
// local state + AnimatedContent rather than Navigation-Compose: two ephemeral, non-deep-linkable
// screens with exactly one level of "back" don't need a nav graph — see the phone-authentication
// report for the full comparison. Revisit if a third signed-out screen or real deep-linking into a
// specific step is ever needed.
@Composable
fun SignedOutScreen(
    authGateway: AuthGateway,
    googleIdTokenProvider: GoogleIdTokenProvider,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf<SignedOutStep>(SignedOutStep.Landing) }
    var googleErrorMessage by remember { mutableStateOf<String?>(null) }
    var isGoogleBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler(enabled = hasInternalBackTarget(step)) { step = SignedOutStep.Landing }

    // Shared by both the landing screen's button and the "check your email" screen's Google
    // escape path (see EmailAuthScreen.kt) — one Google sign-in implementation, not duplicated.
    val onContinueWithGoogle: () -> Unit = {
        googleErrorMessage = null
        isGoogleBusy = true
        scope.launch {
            googleIdTokenProvider.requestIdToken(context)
                .onSuccess { credential ->
                    authGateway.signInWithGoogle(credential.idToken, credential.rawNonce)
                        .onFailure { googleErrorMessage = it.message }
                }
                .onFailure { googleErrorMessage = googleCredentialFailureMessage(it) }
            isGoogleBusy = false
        }
    }

    AnimatedContent(
        targetState = step,
        modifier = modifier,
        transitionSpec = {
            if (targetState == SignedOutStep.Email) {
                (slideInHorizontally(tween(250)) { it } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it } + fadeOut(tween(250)))
            } else {
                (slideInHorizontally(tween(250)) { -it } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)))
            }
        },
        label = "signed-out-flow",
    ) { currentStep ->
        when (currentStep) {
            SignedOutStep.Landing -> AuthLandingScreen(
                isGoogleBusy = isGoogleBusy,
                errorMessage = googleErrorMessage,
                onContinueWithEmail = {
                    googleErrorMessage = null
                    step = SignedOutStep.Email
                },
                onContinueWithGoogle = onContinueWithGoogle,
            )
            SignedOutStep.Email -> EmailAuthScreen(
                gateway = authGateway,
                onBack = { step = SignedOutStep.Landing },
                onContinueWithGoogle = onContinueWithGoogle,
                isGoogleBusy = isGoogleBusy,
                googleErrorMessage = googleErrorMessage,
            )
        }
    }
}
