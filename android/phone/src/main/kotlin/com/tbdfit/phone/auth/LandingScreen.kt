package com.tbdfit.phone.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.R

// The first screen an unauthenticated user sees. The two auth choices are deliberately the most
// visually prominent elements near the bottom, per the product requirement — everything above is
// just branding, not competing UI.
@Composable
fun AuthLandingScreen(
    onContinueWithGoogle: () -> Unit,
    onContinueWithEmail: () -> Unit,
    isGoogleBusy: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Full-bleed brand background. R.drawable.tbdfit_main_background resolves to a real photo
        // once the user drops one in at res/drawable-nodpi/tbdfit_main_background.png (Android's
        // resource-qualifier resolution prefers it automatically over the flat-color fallback
        // vector at res/drawable/tbdfit_main_background.xml — no code change needed either way).
        // Decorative only, hence contentDescription = null.
        Image(
            painter = painterResource(R.drawable.tbdfit_main_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Scrim: transparent near the top (where the image reads clearest) to solidly dark near
        // the bottom (where the interactive controls live) — deliberately a gradient, not a flat
        // overlay, so the image isn't excessively darkened everywhere just to keep the buttons
        // readable. No runtime blur/processing — this is a single cheap Brush fill.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "TBDFit",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Track every workout, on your terms.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.weight(1f))

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            ContinueWithGoogleButton(onClick = onContinueWithGoogle, enabled = !isGoogleBusy, isBusy = isGoogleBusy)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onContinueWithEmail,
                enabled = !isGoogleBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.height(20.dp).width(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Continue with Email", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// Google sign-in remains fully implemented (GoogleIdTokenProvider, AuthGateway.signInWithGoogle)
// but its live provider configuration is intentionally unfinished (Google Cloud OAuth client +
// Supabase Google provider — see the real-device auth readiness audit's "Google must not block
// email" finding: GOOGLE_WEB_CLIENT_ID is currently blank, so a tap would only reach a generic
// failure). Forced disabled here rather than removed, so this one flag is the sole thing to flip
// once that configuration exists — no code path is deleted or reworked.
private const val GOOGLE_SIGN_IN_AVAILABLE = false

// Google's branding guidelines require the standard "G" logo and an approved call-to-action text
// ("Continue with Google" is an approved variant) on a light, bordered button.
//
// R.drawable.ic_google_logo is Google's own real, unmodified official multi-color "G" mark — not
// hand-drawn or approximated. Sourced directly from Google's own open-source FirebaseUI-Android
// repository (github.com/firebase/FirebaseUI-Android, Apache-2.0,
// auth/src/main/res/drawable/fui_ic_googleg_color_24dp.xml), which Google itself maintains and
// ships specifically for use in Google Sign-In buttons — this is the correct current source given
// Google's official branding-guidelines download page could not be fetched directly from this
// environment. Do not redraw or recolor this vector; replace the whole file wholesale if Google's
// mark ever changes.
// Not private: reused by AwaitingVerificationScreen's Google escape path (EmailAuthScreen.kt) so
// that screen doesn't need its own copy of Google's branded button.
@Composable
internal fun ContinueWithGoogleButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled && GOOGLE_SIGN_IN_AVAILABLE,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color.White,
        ),
        border = BorderStroke(1.dp, Color(0xFFDADCE0)),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "Continue with Google" },
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp).width(20.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF4285F4),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_google_logo),
                contentDescription = null,
                modifier = Modifier.height(20.dp).width(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (GOOGLE_SIGN_IN_AVAILABLE) "Continue with Google" else "Google sign-in — coming later",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
