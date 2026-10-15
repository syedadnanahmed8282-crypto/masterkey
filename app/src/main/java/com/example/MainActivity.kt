package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.MasterKeyDatabase
import com.example.data.remote.SupabaseClientManager
import com.example.data.repository.SupabaseCredentialRepository
import com.example.data.security.AppLockManager
import com.example.data.security.BiometricAuthManager
import com.example.data.security.ClipboardHelper
import com.example.data.security.CryptoManager
import com.example.data.security.MasterKeyManager
import com.example.ui.screens.AddEditCredentialScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DetailCredentialScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MasterPasswordEntryScreen
import com.example.ui.screens.MasterPasswordSetupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UnlockScreen
import com.example.ui.theme.BackgroundWhite
import com.example.ui.theme.MasterKeyTheme
import com.example.ui.viewmodel.MasterKeyViewModel
import com.example.ui.viewmodel.MasterKeyViewModelFactory
import com.example.ui.viewmodel.VaultState

class MainActivity : FragmentActivity() {

    private lateinit var appLockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core security, local Room storage, and remote Supabase sync engine
        val cryptoManager = CryptoManager()
        val database = MasterKeyDatabase.getDatabase(applicationContext)
        val supabaseManager = SupabaseClientManager(applicationContext)
        val masterKeyManager = MasterKeyManager(applicationContext, cryptoManager, supabaseManager)
        val repository = SupabaseCredentialRepository(
            credentialDao = database.credentialDao(),
            cryptoManager = cryptoManager,
            masterKeyManager = masterKeyManager,
            supabaseManager = supabaseManager
        )
        appLockManager = AppLockManager(applicationContext)
        val clipboardHelper = ClipboardHelper(applicationContext)
        val biometricAuthManager = BiometricAuthManager(applicationContext)

        val factory = MasterKeyViewModelFactory(
            repository = repository,
            appLockManager = appLockManager,
            clipboardHelper = clipboardHelper,
            biometricAuthManager = biometricAuthManager,
            supabaseManager = supabaseManager,
            masterKeyManager = masterKeyManager
        )

        setContent {
            MasterKeyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundWhite
                ) {
                    val viewModel: MasterKeyViewModel = viewModel(factory = factory)
                    MasterKeyApp(viewModel = viewModel)
                }
            }
        }
    }

    fun applyFlagSecure(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStart() {
        super.onStart()
        if (::appLockManager.isInitialized) {
            appLockManager.onAppForegrounded()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::appLockManager.isInitialized) {
            appLockManager.onAppBackgrounded()
        }
    }
}

@Composable
fun MasterKeyApp(viewModel: MasterKeyViewModel) {
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val isFlagSecure by viewModel.isFlagSecure.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? MainActivity

    var enterMasterPasswordManually by remember { mutableStateOf(false) }

    LaunchedEffect(isFlagSecure) {
        activity?.applyFlagSecure(isFlagSecure)
    }

    Crossfade(targetState = vaultState, label = "vault_state_crossfade") { state ->
        when (state) {
            VaultState.NEEDS_AUTH -> {
                AuthScreen(viewModel = viewModel)
            }

            VaultState.NEEDS_MASTER_PASSWORD_SETUP -> {
                MasterPasswordSetupScreen(viewModel = viewModel)
            }

            VaultState.NEEDS_MASTER_PASSWORD_ENTRY -> {
                MasterPasswordEntryScreen(viewModel = viewModel)
            }

            VaultState.LOCKED -> {
                if (enterMasterPasswordManually) {
                    BackHandler { enterMasterPasswordManually = false }
                    MasterPasswordEntryScreen(viewModel = viewModel)
                } else {
                    UnlockScreen(
                        viewModel = viewModel,
                        onUseMasterPassword = {
                            enterMasterPasswordManually = true
                        }
                    )
                }
            }

            VaultState.UNLOCKED -> {
                enterMasterPasswordManually = false
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToAdd = {
                                navController.navigate("add")
                            },
                            onNavigateToDetail = { credentialId ->
                                navController.navigate("detail/$credentialId")
                            },
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }

                    composable("add") {
                        AddEditCredentialScreen(
                            viewModel = viewModel,
                            credentialId = null,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(
                        route = "edit/{credentialId}",
                        arguments = listOf(navArgument("credentialId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val credentialId = backStackEntry.arguments?.getLong("credentialId") ?: 0L
                        AddEditCredentialScreen(
                            viewModel = viewModel,
                            credentialId = credentialId,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(
                        route = "detail/{credentialId}",
                        arguments = listOf(navArgument("credentialId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val credentialId = backStackEntry.arguments?.getLong("credentialId") ?: 0L
                        DetailCredentialScreen(
                            viewModel = viewModel,
                            credentialId = credentialId,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onNavigateToEdit = { id ->
                                navController.navigate("edit/$id")
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
