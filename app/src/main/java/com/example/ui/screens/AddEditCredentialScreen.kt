package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.PasswordGenerator
import com.example.ui.theme.BackgroundWhite
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LightGrayDivider
import com.example.ui.theme.OutlineBorder
import com.example.ui.theme.SkyBlueContainer
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextDarkGray
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextSecondaryGray
import com.example.ui.viewmodel.MasterKeyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCredentialScreen(
    viewModel: MasterKeyViewModel,
    credentialId: Long?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditMode = credentialId != null && credentialId > 0

    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var showGeneratorDialog by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    LaunchedEffect(credentialId) {
        if (isEditMode) {
            val existing = viewModel.getCredentialById(credentialId!!)
            if (existing != null) {
                title = existing.title
                username = existing.username
                password = existing.password
                appId = existing.appId ?: ""
                notes = existing.notes
            }
        }
    }

    val passwordStrength = remember(password) {
        PasswordGenerator.calculateStrength(password)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_edit_screen"),
        containerColor = BackgroundWhite,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Edit Account" else "New Account",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkGray
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextDarkGray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundWhite
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Title Field
            Text(
                text = "Account or Service Title *",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextDarkGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_title"),
                placeholder = { Text("e.g. Google, GitHub, Netflix", color = TextMutedGray, fontSize = 14.sp) },
                singleLine = true,
                isError = titleError,
                supportingText = if (titleError) { { Text("Title cannot be empty", color = ErrorRed) } } else null,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SkyBluePrimary,
                    unfocusedBorderColor = OutlineBorder,
                    focusedTextColor = TextDarkGray,
                    unfocusedTextColor = TextDarkGray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Username / Email Field
            Text(
                text = "Username or Email *",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextDarkGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    if (it.isNotBlank()) usernameError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_username"),
                placeholder = { Text("e.g. user@example.com", color = TextMutedGray, fontSize = 14.sp) },
                singleLine = true,
                isError = usernameError,
                supportingText = if (usernameError) { { Text("Username or email is required", color = ErrorRed) } } else null,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SkyBluePrimary,
                    unfocusedBorderColor = OutlineBorder,
                    focusedTextColor = TextDarkGray,
                    unfocusedTextColor = TextDarkGray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field with Generator button and Show/Hide toggle
            Text(
                text = "Password *",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextDarkGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (it.isNotEmpty()) passwordError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_password"),
                placeholder = { Text("Enter or generate password", color = TextMutedGray, fontSize = 14.sp) },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showGeneratorDialog = true },
                            modifier = Modifier.testTag("open_generator_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Generate Password",
                                tint = SkyBluePrimary
                            )
                        }
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible },
                            modifier = Modifier.testTag("toggle_password_visibility")
                        ) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                tint = TextSecondaryGray
                            )
                        }
                    }
                },
                isError = passwordError,
                supportingText = if (passwordError) { { Text("Password is required", color = ErrorRed) } } else null,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SkyBluePrimary,
                    unfocusedBorderColor = OutlineBorder,
                    focusedTextColor = TextDarkGray,
                    unfocusedTextColor = TextDarkGray
                )
            )

            // Password strength bar
            if (password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val strengthColor = when (passwordStrength) {
                        PasswordGenerator.Strength.VERY_WEAK,
                        PasswordGenerator.Strength.WEAK -> ErrorRed
                        PasswordGenerator.Strength.FAIR -> Color(0xFFF59E0B)
                        PasswordGenerator.Strength.STRONG,
                        PasswordGenerator.Strength.VERY_STRONG -> Color(0xFF10B981)
                    }

                    LinearProgressIndicator(
                        progress = { passwordStrength.score / 5f },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp),
                        color = strengthColor,
                        trackColor = LightGrayDivider
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = passwordStrength.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = strengthColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Website or App ID (optional)
            // Website Domain or App Package Name (for Autofill matching)
            Text(
                text = "Website Domain or App Package Name",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextDarkGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = appId,
                onValueChange = { appId = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_appid"),
                placeholder = { Text("e.g. facebook.com, github.com, or com.spotify.music", color = TextMutedGray, fontSize = 14.sp) },
                supportingText = {
                    Text(
                        text = "Used by MasterKey Autofill to automatically suggest this login in Chrome and Android apps.",
                        fontSize = 11.sp,
                        color = SkyBluePrimary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SkyBluePrimary,
                    unfocusedBorderColor = OutlineBorder,
                    focusedTextColor = TextDarkGray,
                    unfocusedTextColor = TextDarkGray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Notes (optional)
            Text(
                text = "Notes (Optional)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextDarkGray
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .testTag("input_notes"),
                placeholder = { Text("Security questions, pin codes, or account recovery details...", color = TextMutedGray, fontSize = 14.sp) },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SkyBluePrimary,
                    unfocusedBorderColor = OutlineBorder,
                    focusedTextColor = TextDarkGray,
                    unfocusedTextColor = TextDarkGray
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Save Button
            Button(
                onClick = {
                    val t = title.trim()
                    val u = username.trim()
                    val p = password

                    titleError = t.isBlank()
                    usernameError = u.isBlank()
                    passwordError = p.isEmpty()

                    if (!titleError && !usernameError && !passwordError) {
                        if (isEditMode) {
                            viewModel.updateCredential(
                                id = credentialId!!,
                                title = t,
                                username = u,
                                password = p,
                                appId = appId,
                                notes = notes,
                                onSuccess = onNavigateBack
                            )
                        } else {
                            viewModel.saveCredential(
                                title = t,
                                username = u,
                                password = p,
                                appId = appId,
                                notes = notes,
                                onSuccess = { onNavigateBack() }
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_credential_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SkyBluePrimary,
                    contentColor = BackgroundWhite
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isEditMode) "Save Changes" else "Save Account",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Password Generator Dialog
    if (showGeneratorDialog) {
        PasswordGeneratorDialog(
            initialLength = 16,
            onDismiss = { showGeneratorDialog = false },
            onApply = { generatedPassword ->
                password = generatedPassword
                passwordError = false
                isPasswordVisible = true
                showGeneratorDialog = false
            }
        )
    }
}

@Composable
fun PasswordGeneratorDialog(
    initialLength: Int = 16,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var length by remember { mutableFloatStateOf(initialLength.toFloat()) }
    var incUpper by remember { mutableStateOf(true) }
    var incLower by remember { mutableStateOf(true) }
    var incDigits by remember { mutableStateOf(true) }
    var incSymbols by remember { mutableStateOf(true) }

    var currentGenerated by remember {
        mutableStateOf(
            PasswordGenerator.generate(
                PasswordGenerator.GeneratorConfig(
                    length = initialLength,
                    includeUppercase = true,
                    includeLowercase = true,
                    includeNumbers = true,
                    includeSymbols = true
                )
            )
        )
    }

    fun regenerate() {
        currentGenerated = PasswordGenerator.generate(
            PasswordGenerator.GeneratorConfig(
                length = length.toInt(),
                includeUppercase = incUpper,
                includeLowercase = incLower,
                includeNumbers = incDigits,
                includeSymbols = incSymbols
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Generate Password",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextDarkGray
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Generated preview card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SkyBlueContainer, RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentGenerated,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDarkGray,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { regenerate() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate",
                                tint = SkyBluePrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Length slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Length", fontSize = 13.sp, color = TextDarkGray)
                    Text("${length.toInt()} chars", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SkyBluePrimary)
                }

                Slider(
                    value = length,
                    onValueChange = {
                        length = it
                        regenerate()
                    },
                    valueRange = 8f..32f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = SkyBluePrimary,
                        activeTrackColor = SkyBluePrimary,
                        inactiveTrackColor = LightGrayDivider
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Checkbox options
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = incUpper,
                        onCheckedChange = { incUpper = it; regenerate() },
                        colors = CheckboxDefaults.colors(checkedColor = SkyBluePrimary)
                    )
                    Text("Uppercase (A-Z)", fontSize = 13.sp, color = TextDarkGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = incLower,
                        onCheckedChange = { incLower = it; regenerate() },
                        colors = CheckboxDefaults.colors(checkedColor = SkyBluePrimary)
                    )
                    Text("Lowercase (a-z)", fontSize = 13.sp, color = TextDarkGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = incDigits,
                        onCheckedChange = { incDigits = it; regenerate() },
                        colors = CheckboxDefaults.colors(checkedColor = SkyBluePrimary)
                    )
                    Text("Numbers (0-9)", fontSize = 13.sp, color = TextDarkGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = incSymbols,
                        onCheckedChange = { incSymbols = it; regenerate() },
                        colors = CheckboxDefaults.colors(checkedColor = SkyBluePrimary)
                    )
                    Text("Special Symbols (!@#$)", fontSize = 13.sp, color = TextDarkGray)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(currentGenerated) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SkyBluePrimary,
                    contentColor = BackgroundWhite
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Use This Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondaryGray)
            }
        },
        containerColor = BackgroundWhite,
        shape = RoundedCornerShape(12.dp)
    )
}
