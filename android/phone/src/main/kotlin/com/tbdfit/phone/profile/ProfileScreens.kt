package com.tbdfit.phone.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Shown for ProfileState.Missing — an authenticated user (either provider) with no profile row
// yet. suggestedUsername prefills from signup-time intent when available (email/password) and is
// simply empty for Google-first or pre-existing accounts (see the profile/username research
// report) — both are the same screen, same capability, no GoogleUser/EmailUser split.
@Composable
fun ProfileCompletionScreen(
    suggestedUsername: String?,
    onSubmit: suspend (String) -> Result<Profile>,
    onComplete: (Profile) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf(suggestedUsername.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("Choose a username", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "This is how you'll be identified in TBDFit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorMessage = null },
            label = { Text("Username") },
            singleLine = true,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = {
                val error = usernameValidationError(username)
                if (error != null) {
                    errorMessage = error
                    return@Button
                }
                errorMessage = null
                isBusy = true
                scope.launch {
                    onSubmit(username.trim())
                        .onSuccess { onComplete(it) }
                        .onFailure { errorMessage = usernameConflictMessage(it) }
                    isBusy = false
                }
            },
        ) { Text("Continue") }
        if (isBusy) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(enabled = !isBusy, onClick = onLogout) { Text("Logout") }
    }
}

// Profile-side analogue of AuthState.SessionUnavailable's screen — a genuine backend failure while
// loading the profile, not a reason to treat the user as signed out.
@Composable
fun ProfileUnavailableScreen(onRetry: () -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("Unable to load your profile. Check your connection and retry.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("Retry") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onLogout) { Text("Logout") }
    }
}

// The real, product-facing account header — username first, email as secondary account info.
// Lives in the profile package (not auth) so auth stays unaware of Profile; this composable is the
// one place allowed to depend on both.
@Composable
fun ProfileSummaryHeader(profile: Profile, email: String?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(profile.username, style = MaterialTheme.typography.titleMedium)
        email?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLogout) { Text("Logout") }
    }
}
