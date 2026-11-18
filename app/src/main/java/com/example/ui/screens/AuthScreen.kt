package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.auth.GoogleAuthManager
import com.example.data.auth.LocalAccountStore
import com.example.ui.components.GPlusFlatCard
import com.example.ui.theme.GPlusBlue
import com.example.ui.theme.GPlusGreen
import com.example.ui.theme.GPlusRed
import kotlinx.coroutines.launch

private enum class AuthTab {
    GOOGLE,
    SONGQ_LOGIN,
    SONGQ_REGISTER
}

@Composable
fun AuthScreen(
    localAccountStore: LocalAccountStore,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var selectedTab by remember { mutableStateOf(AuthTab.GOOGLE) }
    var isLoading by remember { mutableStateOf(false) }
    var isCheckingSilent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Custom Song/Q Login & Registration fields
    var loginInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        GoogleAuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSilent = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingSilent = false
            },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Retro Google+ Hero Banner Card
            GPlusFlatCard(accentStripColor = GPlusRed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_songq_banner_clean_1794959059242),
                        contentDescription = "Song/Q DirexSoft Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_songq_widget_logo),
                                contentDescription = "Song/Q Logo",
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(2.dp, Color.White, RectangleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Song/Q  v1.15",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "DirexSoft Corporations • Karol Kopeć",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }

            // Dual Auth Card: Google Sign-In + Własny System Logowania i Rejestracji Song/Q
            GPlusFlatCard(accentStripColor = GPlusBlue) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Flat Google+ Mode Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        AuthModeTab(
                            title = "Google",
                            selected = selectedTab == AuthTab.GOOGLE,
                            accent = GPlusBlue,
                            modifier = Modifier.weight(1f),
                            onClick = { errorMessage = null; selectedTab = AuthTab.GOOGLE }
                        )
                        AuthModeTab(
                            title = "Logowanie",
                            selected = selectedTab == AuthTab.SONGQ_LOGIN,
                            accent = GPlusRed,
                            modifier = Modifier.weight(1f),
                            onClick = { errorMessage = null; selectedTab = AuthTab.SONGQ_LOGIN }
                        )
                        AuthModeTab(
                            title = "Rejestracja",
                            selected = selectedTab == AuthTab.SONGQ_REGISTER,
                            accent = GPlusGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { errorMessage = null; selectedTab = AuthTab.SONGQ_REGISTER }
                        )
                    }

                    when (selectedTab) {
                        AuthTab.GOOGLE -> {
                            Text(
                                text = stringResource(id = R.string.google_sign_in_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Zaloguj się przez Google, aby synchronizować playlisty .sq, aktywność i ustawienia EQ w chmurze.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (isCheckingSilent || isLoading) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, MaterialTheme.colorScheme.outline)
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = GPlusRed,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(id = R.string.google_sign_in_loading),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    errorMessage = null
                                    isLoading = true
                                    GoogleAuthManager.onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isLoading = false
                                            onAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isLoading = false
                                            errorMessage = msg
                                        },
                                        onAuthCancelled = {
                                            isLoading = false
                                        },
                                        scope = scope
                                    )
                                },
                                enabled = !isLoading,
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GPlusBlue,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("google_sign_in_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(id = R.string.google_sign_in_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        AuthTab.SONGQ_LOGIN -> {
                            Text(
                                text = "Logowanie do własnego konta Song/Q",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Zaloguj się swoim identyfikatorem DirexSoft ID (bezpieczny skrót SHA-256 + lokalne pliki .sq/.mfa).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            OutlinedTextField(
                                value = loginInput,
                                onValueChange = { loginInput = it },
                                label = { Text("Login lub e-mail") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                shape = RectangleShape,
                                modifier = Modifier.fillMaxWidth().testTag("local_login_input")
                            )
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Hasło") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RectangleShape,
                                modifier = Modifier.fillMaxWidth().testTag("local_password_input")
                            )
                            Button(
                                onClick = {
                                    errorMessage = null
                                    isLoading = true
                                    scope.launch {
                                        val res = localAccountStore.login(loginInput, passwordInput)
                                        isLoading = false
                                        res.onSuccess { onAuthSuccess() }
                                            .onFailure { errorMessage = it.localizedMessage }
                                    }
                                },
                                enabled = !isLoading,
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = GPlusRed),
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("local_login_submit")
                            ) {
                                Text("Zaloguj się do Song/Q")
                            }
                        }

                        AuthTab.SONGQ_REGISTER -> {
                            Text(
                                text = "Rejestracja konta Song/Q (DirexSoft ID)",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            OutlinedTextField(
                                value = displayNameInput,
                                onValueChange = { displayNameInput = it },
                                label = { Text("Imię / Nazwa profilu") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                shape = RectangleShape,
                                modifier = Modifier.fillMaxWidth().testTag("local_reg_name_input")
                            )
                            OutlinedTextField(
                                value = loginInput,
                                onValueChange = { loginInput = it },
                                label = { Text("Login lub adres e-mail (min. 3 znaki)") },
                                singleLine = true,
                                shape = RectangleShape,
                                modifier = Modifier.fillMaxWidth().testTag("local_reg_login_input")
                            )
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Hasło (min. 6 znaków)") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RectangleShape,
                                modifier = Modifier.fillMaxWidth().testTag("local_reg_password_input")
                            )
                            OutlinedTextField(
                                value = confirmPasswordInput,
                                onValueChange = { confirmPasswordInput = it },
                                label = { Text("Powtórz hasło") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RectangleShape,
                                modifier = Modifier.fillMaxWidth().testTag("local_reg_confirm_input")
                            )
                            Button(
                                onClick = {
                                    errorMessage = null
                                    if (passwordInput != confirmPasswordInput) {
                                        errorMessage = "Hasła nie są identyczne."
                                        return@Button
                                    }
                                    isLoading = true
                                    scope.launch {
                                        val res = localAccountStore.register(
                                            login = loginInput,
                                            displayName = displayNameInput,
                                            password = passwordInput
                                        )
                                        isLoading = false
                                        res.onSuccess { onAuthSuccess() }
                                            .onFailure { errorMessage = it.localizedMessage }
                                    }
                                },
                                enabled = !isLoading,
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = GPlusGreen),
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("local_reg_submit")
                            ) {
                                Text("Utwórz konto Song/Q")
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GPlusRed.copy(alpha = 0.1f))
                                .border(1.dp, GPlusRed)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GPlusRed
                            )
                        }
                    }
                }
            }

            // Feature highlights in 2014 Google+ flat style
            GPlusFlatCard(accentStripColor = GPlusGreen) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureRow(Icons.Default.LibraryMusic, "Format .MFA (MediaFileAudio) z kompresją PCM-Z oraz playlisty .SQ")
                    FeatureRow(Icons.Default.GraphicEq, "Korektor EQ z suwakami Bass / Mids / Treble i usługą odtwarzania")
                    FeatureRow(Icons.Default.Search, "Pasek wyszukiwarki w stylu Google w aplikacji i na widżecie")
                }
            }
        }
    }
}

@Composable
private fun AuthModeTab(
    title: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GPlusRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
