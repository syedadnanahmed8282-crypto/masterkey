package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DecryptedCredential
import com.example.ui.theme.BackgroundWhite
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LightGrayDivider
import com.example.ui.theme.SkyBlueContainer
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextDarkGray
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextSecondaryGray
import com.example.ui.viewmodel.MasterKeyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailCredentialScreen(
    viewModel: MasterKeyViewModel,
    credentialId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var credential by remember { mutableStateOf<DecryptedCredential?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(credentialId) {
        credential = viewModel.getCredentialById(credentialId)
    }

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("detail_screen"),
        containerColor = BackgroundWhite,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = credential?.title ?: "Account Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkGray
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextDarkGray
                        )
                    }
                },
                actions = {
                    if (credential != null) {
                        IconButton(
                            onClick = { onNavigateToEdit(credentialId) },
                            modifier = Modifier.testTag("detail_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Account",
                                tint = SkyBluePrimary
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("detail_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Account",
                                tint = ErrorRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundWhite
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (credential == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading credential...", color = TextSecondaryGray)
            }
        } else {
            val cred = credential!!
            val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Header emblem card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(SkyBlueContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = cred.title.firstOrNull()?.uppercaseChar() ?: 'K'
                        Text(
                            text = initial.toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = SkyBluePrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = cred.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkGray
                        )
                        if (!cred.appId.isNullOrBlank()) {
                            Text(
                                text = cred.appId,
                                fontSize = 13.sp,
                                color = SkyBluePrimary
                            )
                        }
                    }
                }

                HorizontalDivider(color = LightGrayDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                // Username Card Section
                DetailFieldItem(
                    label = "Username / Email",
                    value = cred.username,
                    icon = Icons.Default.Person,
                    onCopy = {
                        viewModel.copyToClipboard("Username", cred.username)
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password Card Section (Masked by default with eye toggle + Copy)
                DetailPasswordFieldItem(
                    label = "Password",
                    value = cred.password,
                    isVisible = isPasswordVisible,
                    onToggleVisibility = { isPasswordVisible = !isPasswordVisible },
                    onCopy = {
                        viewModel.copyToClipboard("Password", cred.password)
                    }
                )

                // Optional Website / App ID
                if (!cred.appId.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    DetailFieldItem(
                        label = "Website or App ID",
                        value = cred.appId,
                        icon = Icons.Default.Language,
                        onCopy = {
                            viewModel.copyToClipboard("Website / App ID", cred.appId)
                        }
                    )
                }

                // Optional Notes
                if (cred.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    DetailNotesItem(
                        label = "Notes",
                        value = cred.notes,
                        icon = Icons.Default.Notes,
                        onCopy = {
                            viewModel.copyToClipboard("Notes", cred.notes)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Metadata Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = TextMutedGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Timestamps & Security",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondaryGray
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Created: ${dateFormatter.format(Date(cred.createdAt))}",
                            fontSize = 12.sp,
                            color = TextMutedGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last updated: ${dateFormatter.format(Date(cred.updatedAt))}",
                            fontSize = 12.sp,
                            color = TextMutedGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cipher: AES-256-GCM hardware-backed vault key",
                            fontSize = 11.sp,
                            color = TextMutedGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete Account?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextDarkGray
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete this credential for \"${credential?.title}\"? This action cannot be undone.",
                    fontSize = 14.sp,
                    color = TextDarkGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteCredential(credentialId) {
                            onNavigateBack()
                        }
                    }
                ) {
                    Text("Delete", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondaryGray)
                }
            },
            containerColor = BackgroundWhite,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun DetailFieldItem(
    label: String,
    value: String,
    icon: ImageVector,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SkyBluePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = TextMutedGray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 15.sp,
                    color = TextDarkGray,
                    fontWeight = FontWeight.Normal
                )
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DetailPasswordFieldItem(
    label: String,
    value: String,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = null,
                tint = SkyBluePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = TextMutedGray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (isVisible) {
                    Text(
                        text = value,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = TextDarkGray,
                        fontWeight = FontWeight.Normal
                    )
                } else {
                    Text(
                        text = "••••••••••••",
                        fontSize = 18.sp,
                        color = TextDarkGray,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isVisible) "Hide password" else "Show password",
                    tint = TextSecondaryGray,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy password",
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DetailNotesItem(
    label: String,
    value: String,
    icon: ImageVector,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = TextMutedGray,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                color = TextDarkGray,
                lineHeight = 20.sp
            )
        }
    }
}
