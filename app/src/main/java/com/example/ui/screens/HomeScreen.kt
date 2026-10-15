package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DecryptedCredential
import com.example.data.model.PendingCredential
import com.example.ui.theme.BackgroundWhite
import com.example.ui.theme.LightGrayDivider
import com.example.ui.theme.OutlineBorder
import com.example.ui.theme.SkyBlueContainer
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextDarkGray
import com.example.ui.theme.TextMutedGray
import com.example.ui.theme.TextSecondaryGray
import com.example.ui.viewmodel.MasterKeyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MasterKeyViewModel,
    onNavigateToAdd: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    val pendingCredentials by viewModel.pendingCredentials.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.triggerManualSync()
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
            .testTag("home_screen"),
        containerColor = BackgroundWhite,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MasterKey",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkGray
                        )
                        if (pendingCredentials.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(SkyBluePrimary, CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${pendingCredentials.size} New",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BackgroundWhite
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Sync Status Indicator Button
                    IconButton(
                        onClick = { viewModel.triggerManualSync() },
                        modifier = Modifier.testTag("home_sync_button")
                    ) {
                        val icon = when (syncStatus) {
                            com.example.data.repository.SyncStatus.SYNCED -> Icons.Default.Check
                            com.example.data.repository.SyncStatus.SYNCING -> Icons.Default.Refresh
                            com.example.data.repository.SyncStatus.OFFLINE -> Icons.Default.CloudQueue
                            com.example.data.repository.SyncStatus.ERROR -> Icons.Default.Close
                        }
                        val tint = when (syncStatus) {
                            com.example.data.repository.SyncStatus.SYNCED -> SkyBluePrimary
                            com.example.data.repository.SyncStatus.SYNCING -> SkyBluePrimary
                            com.example.data.repository.SyncStatus.OFFLINE -> TextSecondaryGray
                            com.example.data.repository.SyncStatus.ERROR -> Color(0xFFC62828)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Sync Status: ${syncStatus.label}. Tap to sync.",
                            tint = tint
                        )
                    }
                    IconButton(
                        onClick = { viewModel.lockVault() },
                        modifier = Modifier.testTag("home_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = SkyBluePrimary
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondaryGray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundWhite
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = SkyBluePrimary,
                contentColor = BackgroundWhite,
                shape = CircleShape,
                modifier = Modifier.testTag("add_credential_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add New Account"
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Pending Autofill Saves Section (if any exist)
            if (pendingCredentials.isNotEmpty()) {
                PendingSavesSection(
                    pendingList = pendingCredentials,
                    onSave = { pending ->
                        viewModel.commitPendingCredential(pending.id)
                    },
                    onDismiss = { pending ->
                        viewModel.dismissPendingCredential(pending.id)
                    }
                )
                HorizontalDivider(color = LightGrayDivider, thickness = 1.dp)
            }

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_field"),
                    placeholder = {
                        Text(
                            text = "Search accounts, domains, or usernames...",
                            color = TextMutedGray,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondaryGray
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = TextSecondaryGray
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SkyBluePrimary,
                        unfocusedBorderColor = OutlineBorder,
                        focusedContainerColor = BackgroundWhite,
                        unfocusedContainerColor = SurfaceCard,
                        focusedTextColor = TextDarkGray,
                        unfocusedTextColor = TextDarkGray
                    )
                )
            }

            // Sync Status Indicator Banner
            if (syncStatus != com.example.data.repository.SyncStatus.SYNCED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(
                            when (syncStatus) {
                                com.example.data.repository.SyncStatus.SYNCING -> SkyBlueContainer
                                com.example.data.repository.SyncStatus.ERROR -> Color(0xFFFFEBEE)
                                else -> SurfaceCard
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.triggerManualSync() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (syncStatus) {
                                com.example.data.repository.SyncStatus.SYNCING -> Icons.Default.Refresh
                                com.example.data.repository.SyncStatus.ERROR -> Icons.Default.Close
                                else -> Icons.Default.CloudQueue
                            },
                            contentDescription = null,
                            tint = when (syncStatus) {
                                com.example.data.repository.SyncStatus.SYNCING -> SkyBluePrimary
                                com.example.data.repository.SyncStatus.ERROR -> Color(0xFFC62828)
                                else -> TextSecondaryGray
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (syncStatus) {
                                com.example.data.repository.SyncStatus.SYNCING -> "Syncing vault with cloud..."
                                com.example.data.repository.SyncStatus.ERROR -> "Sync Error. Tap to retry."
                                else -> "Offline mode (Local Cache active). Tap to sync."
                            },
                            fontSize = 12.sp,
                            color = TextDarkGray
                        )
                    }

                    Text(
                        text = "Retry",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SkyBluePrimary
                    )
                }
            }

            HorizontalDivider(color = LightGrayDivider, thickness = 1.dp)

            if (credentials.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching accounts found" else "No accounts saved yet",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = TextDarkGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try searching for a different title or domain" else "Tap '+' below to store an account, or log in via browser/apps to autofill",
                            fontSize = 14.sp,
                            color = TextSecondaryGray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("credentials_list")
                ) {
                    items(
                        items = credentials,
                        key = { it.id }
                    ) { credential ->
                        CredentialListItem(
                            credential = credential,
                            onClick = { onNavigateToDetail(credential.id) },
                            onCopyUsername = {
                                viewModel.copyToClipboard("Username", credential.username)
                            }
                        )
                        HorizontalDivider(
                            color = LightGrayDivider,
                            thickness = 1.dp,
                            modifier = Modifier.padding(start = 72.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PendingSavesSection(
    pendingList: List<PendingCredential>,
    onSave: (PendingCredential) -> Unit,
    onDismiss: (PendingCredential) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SkyBlueContainer)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = SkyBluePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Captured by Autofill (${pendingList.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkGray
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        pendingList.forEach { pending ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pending.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkGray
                        )
                        Text(
                            text = pending.username,
                            fontSize = 13.sp,
                            color = TextSecondaryGray
                        )
                        if (!pending.appId.isNullOrBlank()) {
                            Text(
                                text = pending.appId,
                                fontSize = 11.sp,
                                color = TextMutedGray
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onDismiss(pending) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TextSecondaryGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { onSave(pending) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SkyBluePrimary,
                                contentColor = BackgroundWhite
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Save to Vault", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CredentialListItem(
    credential: DecryptedCredential,
    onClick: () -> Unit,
    onCopyUsername: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Initials / Emblem avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(SkyBlueContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val initial = credential.title.firstOrNull()?.uppercaseChar() ?: 'K'
            Text(
                text = initial.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SkyBluePrimary
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = credential.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDarkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = credential.username,
                fontSize = 13.sp,
                color = TextSecondaryGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!credential.appId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = credential.appId,
                    fontSize = 11.sp,
                    color = TextMutedGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Quick copy username button
        IconButton(
            onClick = onCopyUsername,
            modifier = Modifier
                .size(36.dp)
                .testTag("copy_username_${credential.id}")
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy username",
                tint = TextSecondaryGray,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = TextMutedGray,
            modifier = Modifier.size(14.dp)
        )
    }
}
