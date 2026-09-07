package com.tbdfit.phone.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.profile.usernameValidationError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// internal, not private: EmailAuthNavigationTest exercises backTargetFor against these directly.
internal enum class EmailMode { LOGIN, CREATE_ACCOUNT }

internal sealed interface EmailScreenStep {
    data class Credentials(val mode: EmailMode) : EmailScreenStep
    data class AwaitingVerification(val email: String) : EmailScreenStep

    // Terms of Service / Privacy Policy / Forgot password — all development placeholders (see
    // PlaceholderScreen). returnTo remembers which tab to come back to.
    data class Placeholder(val title: String, val message: String, val returnTo: EmailMode) : EmailScreenStep
}

// The single source of truth for "what does Back do from this step" — used by BOTH the system
// BackHandler and (indirectly, via the same onBack callback threaded through) the top bar's back
// arrow, so there is exactly one navigation semantics, not two competing ones. null means this
// step has no internal back target (Credentials is the root of this screen; back from here exits
// to Landing via the onBack callback passed in from SignedOutScreen, not this function).
internal fun backTargetFor(step: EmailScreenStep): EmailScreenStep? = when (step) {
    is EmailScreenStep.Placeholder -> EmailScreenStep.Credentials(step.returnTo)
    is EmailScreenStep.AwaitingVerification -> EmailScreenStep.Credentials(EmailMode.LOGIN)
    is EmailScreenStep.Credentials -> null
}

// Deliberately non-committal: Supabase returns an obfuscated 200-OK response — and sends no
// email — when signup targets an address that already belongs to a confirmed account (e.g. one
// created via Google), specifically to prevent account enumeration (confirmed directly against
// the Supabase Auth server source, see the phone-authentication research report). This copy must
// never assert that an account was created, that an email exists, or that anything was actually
// sent — only that it "may" have happened. Extracted as named constants (rather than inline
// strings) so their semantics stay checkable without Compose UI testing — see
// VerificationCopyTest.
internal const val AWAITING_VERIFICATION_COPY =
    "If an account can be created for this email, we've sent verification instructions to:"
internal const val RESEND_VERIFICATION_COPY =
    "If verification is available for this email, new instructions have been requested."

// VALIDATION: is this a plausible enough email to actually attempt Supabase authentication with?
// Deliberately permissive and NOT an RFC-authoritative parser — this only needs to reject
// obviously-incomplete input like "foo@" locally (exactly one "@", non-empty/non-whitespace local
// and domain parts) so the user gets a clear local error instead of a pointless network call. Does
// NOT require a dot in the domain: that requirement is not something Supabase itself demands. Used
// for both the login and create-account email fields, so there is exactly one definition of email
// validity in this file.
internal fun isValidEmail(value: String): Boolean {
    val trimmed = value.trim()
    val atIndex = trimmed.indexOf('@')
    if (atIndex <= 0 || atIndex != trimmed.lastIndexOf('@')) return false
    val localPart = trimmed.substring(0, atIndex)
    val domainPart = trimmed.substring(atIndex + 1)
    return localPart.isNotEmpty() && domainPart.isNotEmpty() &&
        localPart.none { it.isWhitespace() } && domainPart.none { it.isWhitespace() }
}

// Login identifier is email-only (see the repository truth audit's username-login findings:
// username login has never existed — no username->auth-identity resolution RPC/endpoint exists,
// and the database's normalized_username column exists solely for profile-username uniqueness, not
// login resolution). Mirrors createAccountValidationError's shape exactly, one definition of email
// validity shared between both forms via isValidEmail above.
internal fun loginValidationError(email: String, password: String): String? = when {
    email.isBlank() || password.isBlank() -> "Enter your email and password."
    !isValidEmail(email) -> "Enter a valid email address."
    else -> null
}

internal fun createAccountValidationError(username: String, email: String, password: String): String? {
    usernameValidationError(username)?.let { return it }
    return when {
        email.isBlank() || password.isBlank() -> "Enter your email and password."
        !isValidEmail(email) -> "Enter a valid email address."
        else -> null
    }
}

// True whenever a just-completed sign-up call must NOT be treated as an authenticated session yet
// — i.e. whenever the global AuthState did not become SignedIn as a direct result of that call
// (email confirmation is still required). Extracted as a pure function so the "don't falsely claim
// SignedIn" contract is unit-testable without Compose (see EmailAuthScreenLogicTest). The call
// succeeding is not by itself proof of an authenticated session — only AuthState is.
internal fun requiresEmailVerificationPrompt(stateAfterSignUp: AuthState): Boolean =
    stateAfterSignUp !is AuthState.SignedIn

// Holds both forms' field values at a level that survives navigating to/from a Placeholder
// (Terms/Privacy/Forgot password) and back. EmailCredentialsScreen and its child forms are fully
// unmounted while a Placeholder is shown (see EmailAuthScreen's `when` below), so a plain
// remember{} inside them alone isn't enough — this lives in EmailAuthScreen instead, which
// persists across all of this screen's internal steps. Deliberately in-memory only (plain
// remember, never rememberSaveable): must NOT survive process death, and must never be written to
// Room/DataStore/SavedStateHandle/disk — this matters especially for the password fields.
private class EmailFormFields {
    var loginEmail by mutableStateOf("")
    var loginPassword by mutableStateOf("")
    var createUsername by mutableStateOf("")
    var createEmail by mutableStateOf("")
    var createPassword by mutableStateOf("")
}

@Composable
fun EmailAuthScreen(
    gateway: AuthGateway,
    onBack: () -> Unit,
    onContinueWithGoogle: () -> Unit,
    isGoogleBusy: Boolean,
    googleErrorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf<EmailScreenStep>(EmailScreenStep.Credentials(EmailMode.LOGIN)) }
    val fields = remember { EmailFormFields() }

    // Internal back navigation (Placeholder/AwaitingVerification -> Credentials) is handled here,
    // separately from SignedOutScreen's own BackHandler (Email -> Landing) — the innermost enabled
    // BackHandler wins, so system back always does the locally-correct thing at every depth. Both
    // this and the top bar's back arrow (in EmailCredentialsScreen) resolve through the exact same
    // backTargetFor/onBack — one navigation semantics, not two.
    BackHandler(enabled = backTargetFor(step) != null) {
        backTargetFor(step)?.let { step = it }
    }

    when (val current = step) {
        is EmailScreenStep.Credentials -> EmailCredentialsScreen(
            gateway = gateway,
            mode = current.mode,
            fields = fields,
            onModeChange = { step = EmailScreenStep.Credentials(it) },
            onBack = onBack,
            onNeedsVerification = { email -> step = EmailScreenStep.AwaitingVerification(email) },
            onShowPlaceholder = { title, message ->
                step = EmailScreenStep.Placeholder(title, message, returnTo = current.mode)
            },
            modifier = modifier,
        )
        is EmailScreenStep.AwaitingVerification -> AwaitingVerificationScreen(
            email = current.email,
            onResend = { gateway.resendEmailVerification(current.email) },
            onBackToLogin = { step = EmailScreenStep.Credentials(EmailMode.LOGIN) },
            onContinueWithGoogle = onContinueWithGoogle,
            isGoogleBusy = isGoogleBusy,
            googleErrorMessage = googleErrorMessage,
            modifier = modifier,
        )
        is EmailScreenStep.Placeholder -> PlaceholderScreen(
            title = current.title,
            message = current.message,
            onBack = { step = EmailScreenStep.Credentials(current.returnTo) },
            modifier = modifier,
        )
    }
}

@Composable
private fun EmailCredentialsScreen(
    gateway: AuthGateway,
    mode: EmailMode,
    fields: EmailFormFields,
    onModeChange: (EmailMode) -> Unit,
    onBack: () -> Unit,
    onNeedsVerification: (String) -> Unit,
    onShowPlaceholder: (title: String, message: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TbdfitAuthTopBar(mode = mode, onModeChange = onModeChange, onBack = onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            when (mode) {
                EmailMode.LOGIN -> LoginForm(
                    gateway = gateway,
                    fields = fields,
                    onForgotPassword = {
                        onShowPlaceholder(
                            "Forgot password",
                            "Password reset isn't available in this version yet. It will arrive as a " +
                                "separate capability in a future update.",
                        )
                    },
                )
                EmailMode.CREATE_ACCOUNT -> CreateAccountForm(
                    gateway = gateway,
                    fields = fields,
                    onNeedsVerification = onNeedsVerification,
                    onTermsClick = {
                        onShowPlaceholder(
                            "Terms of Service",
                            "Final Terms of Service content is pending before release. This is a " +
                                "placeholder screen for development.",
                        )
                    },
                    onPrivacyClick = {
                        onShowPlaceholder(
                            "Privacy Policy",
                            "Final Privacy Policy content is pending before release. This is a " +
                                "placeholder screen for development.",
                        )
                    },
                )
            }
        }
    }
}

// TbdfitAuthTopBar: back arrow (reuses the exact same onBack callback the system BackHandler would
// invoke — see backTargetFor's doc comment) + the existing Login/Create-account slider, moved in
// here rather than duplicated. Small and concrete on purpose — not a generic top-bar framework.
// surfaceContainerHigh is a neutral dark grey explicitly set in Theme.kt for exactly this use,
// visibly distinct from the page's near-black background without introducing the purple `primary`
// color anywhere.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TbdfitAuthTopBar(
    mode: EmailMode,
    onModeChange: (EmailMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Back to landing" },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        title = { EmailModeSwitcher(mode = mode, onModeChange = onModeChange) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

// A deliberate, polished mode switch rather than a plain text toggle link — TabRow is the small
// native Compose component for exactly this ("deliberate and polished" switching between two peer
// destinations), nothing custom-drawn. Explicitly neutral-grey throughout: a default SecondaryTabRow
// derives its indicator/selected-tab color from colorScheme.primary, which is the purple this pass
// removes from the auth UI — see the UI-polish report for why `primary` itself stays unchanged
// globally rather than being edited here.
@Composable
private fun EmailModeSwitcher(mode: EmailMode, onModeChange: (EmailMode) -> Unit, modifier: Modifier = Modifier) {
    val selectedIndex = if (mode == EmailMode.LOGIN) 0 else 1
    SecondaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = true),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        NoRippleTab(
            selected = mode == EmailMode.LOGIN,
            onClick = { onModeChange(EmailMode.LOGIN) },
            text = "LOG IN",
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NoRippleTab(
            selected = mode == EmailMode.CREATE_ACCOUNT,
            onClick = { onModeChange(EmailMode.CREATE_ACCOUNT) },
            text = "CREATE ACCOUNT",
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Material3's own `Tab` (1.4.0) builds its ripple by calling `ripple()` directly rather than
// reading `LocalIndication`, so a `CompositionLocalProvider(LocalIndication provides ...)` around
// it cannot suppress that ripple — confirmed by inspecting the resolved material3-1.4.0 bytecode
// (`TabKt` calls `RippleKt.ripple` inline). This reimplements just enough of `Tab` (same
// `Role.Tab`/selected semantics, same text-centered layout) on top of `Modifier.selectable`'s
// explicit-`indication` overload, which does accept `indication = null`, to genuinely remove the
// press feedback and leave only the slider indicator showing selection.
@Composable
private fun NoRippleTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) selectedContentColor else unselectedContentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

// Centralized neutral-grey button/link treatment for this whole email-auth surface — reused by
// every filled/text button below instead of accepting Material3's default (which is
// colorScheme.primary, the purple this pass removes). Built entirely from existing theme tokens
// (onSurface/background/onSurfaceVariant) rather than new hardcoded colors — see the UI-polish
// report for why colorScheme.primary itself stays unchanged globally.
@Composable
private fun authPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.onSurface,
    contentColor = MaterialTheme.colorScheme.background,
    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    disabledContentColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
)

@Composable
private fun authTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
)

@Composable
private fun LoginForm(
    gateway: AuthGateway,
    fields: EmailFormFields,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Welcome back", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = fields.loginEmail,
            onValueChange = { fields.loginEmail = it; errorMessage = null },
            label = { Text("Email") },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = fields.loginPassword,
            onValueChange = { fields.loginPassword = it; errorMessage = null },
            label = { Text("Password") },
            singleLine = true,
            enabled = !isBusy,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }, colors = authTextButtonColors()) {
                    Icon(
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            enabled = !isBusy,
            colors = authPrimaryButtonColors(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = {
                val error = loginValidationError(fields.loginEmail, fields.loginPassword)
                if (error != null) {
                    errorMessage = error
                    return@Button
                }
                errorMessage = null
                isBusy = true
                scope.launch {
                    gateway.signIn(fields.loginEmail.trim(), fields.loginPassword).onFailure { errorMessage = it.message }
                    isBusy = false
                }
            },
        ) {
            Text("Log in")
        }
        if (isBusy) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(enabled = !isBusy, onClick = onForgotPassword, colors = authTextButtonColors()) {
            Text("Forgot password?")
        }
    }
}

@Composable
private fun CreateAccountForm(
    gateway: AuthGateway,
    fields: EmailFormFields,
    onNeedsVerification: (String) -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Username is captured here and carried into signUp as desired-username metadata (see
    // AuthGateway.signUp) — it is client-controlled/untrusted input, not proof the username is
    // available, and not the product profile itself. The actual profiles row is only created by
    // com.tbdfit.phone.profile.ProfileGateway.createOwnProfile, once a real authenticated session
    // exists, gated by the database's own uniqueness constraint. See the profile/username
    // research report and implementation report for the full design.
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Create your account", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = fields.createUsername,
            onValueChange = { fields.createUsername = it; errorMessage = null },
            label = { Text("Username") },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = fields.createEmail,
            onValueChange = { fields.createEmail = it; errorMessage = null },
            label = { Text("Email") },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = fields.createPassword,
            onValueChange = { fields.createPassword = it; errorMessage = null },
            label = { Text("Password") },
            singleLine = true,
            enabled = !isBusy,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }, colors = authTextButtonColors()) {
                    Icon(
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !isBusy,
            colors = authPrimaryButtonColors(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = {
                val error = createAccountValidationError(fields.createUsername, fields.createEmail, fields.createPassword)
                if (error != null) {
                    errorMessage = error
                    return@Button
                }
                errorMessage = null
                isBusy = true
                scope.launch {
                    val result = gateway.signUp(
                        email = fields.createEmail.trim(),
                        password = fields.createPassword,
                        desiredUsername = fields.createUsername.trim(),
                    )
                    result.onFailure { errorMessage = it.message }
                    if (result.isSuccess && requiresEmailVerificationPrompt(gateway.state.first())) {
                        onNeedsVerification(fields.createEmail.trim())
                    }
                    isBusy = false
                }
            },
        ) {
            Text("Create account")
        }
        if (isBusy) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        TermsAndPrivacyNotice(onTermsClick = onTermsClick, onPrivacyClick = onPrivacyClick)
    }
}

@Composable
private fun TermsAndPrivacyNotice(onTermsClick: () -> Unit, onPrivacyClick: () -> Unit, modifier: Modifier = Modifier) {
    val linkStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textDecoration = TextDecoration.Underline,
    )
    Column(modifier = modifier) {
        Text("By creating an account, you agree to the", style = MaterialTheme.typography.bodySmall)
        FlowRow {
            Text(text = "Terms of Service", style = linkStyle, modifier = Modifier.clickable(onClick = onTermsClick))
            Text(" and ", style = MaterialTheme.typography.bodySmall)
            Text(text = "Privacy Policy", style = linkStyle, modifier = Modifier.clickable(onClick = onPrivacyClick))
            Text(".", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = onBack, colors = authTextButtonColors()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.height(18.dp).width(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Back")
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AwaitingVerificationScreen(
    email: String,
    onResend: suspend () -> Result<Unit>,
    onBackToLogin: () -> Unit,
    onContinueWithGoogle: () -> Unit,
    isGoogleBusy: Boolean,
    googleErrorMessage: String?,
    modifier: Modifier = Modifier,
) {
    var isResendBusy by remember { mutableStateOf(false) }
    var resendMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("Check your email", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(AWAITING_VERIFICATION_COPY)
        Text(email, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(
            enabled = !isResendBusy,
            colors = authPrimaryButtonColors(),
            onClick = {
                isResendBusy = true
                scope.launch {
                    onResend()
                        .onSuccess { resendMessage = RESEND_VERIFICATION_COPY }
                        .onFailure { resendMessage = it.message }
                    isResendBusy = false
                }
            },
        ) { Text("Resend instructions") }
        if (isResendBusy) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
        }
        resendMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it)
        }

        Spacer(Modifier.height(32.dp))
        Text("Already use Google with this email?", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        // Google escape path: reuses the same Google sign-in flow/button as the landing screen
        // (see SignedOutScreen's onContinueWithGoogle) — no separate Credential Manager call, no
        // account-existence lookup. If this email really does belong to a Google account, this
        // just signs the user in normally; if not, Google's own account chooser handles it.
        ContinueWithGoogleButton(onClick = onContinueWithGoogle, enabled = !isGoogleBusy, isBusy = isGoogleBusy)
        googleErrorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBackToLogin, colors = authTextButtonColors()) { Text("Back to login") }
    }
}
