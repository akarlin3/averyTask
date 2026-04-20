package com.averycorp.prismtask.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.BuildConfig
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.SortPreferencesSyncService
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.remote.ThemePreferencesSyncService
import com.averycorp.prismtask.testing.EmulatorAuthHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    data object SignedOut : AuthState()

    data object Loading : AuthState()

    data object SignedIn : AuthState()

    data class Error(
        val message: String
    ) : AuthState()
}

@HiltViewModel
class AuthViewModel
@Inject
constructor(
    private val authManager: AuthManager,
    private val syncService: SyncService,
    private val sortPreferencesSyncService: SortPreferencesSyncService,
    private val themePreferencesSyncService: ThemePreferencesSyncService
) : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(
        if (authManager.isSignedIn.value) AuthState.SignedIn else AuthState.SignedOut
    )
    val authState: StateFlow<AuthState> = _authState

    val isSignedIn = authManager.isSignedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, authManager.isSignedIn.value)

    val userEmail: String? get() = authManager.currentUser.value?.email

    private val _skippedSignIn = MutableStateFlow(false)
    val skippedSignIn: StateFlow<Boolean> = _skippedSignIn

    fun onGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authManager.signInWithGoogle(idToken)
            if (result.isSuccess) {
                _authState.value = AuthState.SignedIn
                // Initial upload on first sign-in (launched in SyncService's own scope
                // so it survives navigation away from AuthScreen)
                syncService.launchInitialUpload()
                syncService.startRealtimeListeners()
                sortPreferencesSyncService.startAfterSignIn()
                themePreferencesSyncService.startAfterSignIn()
            } else {
                // Firebase rejected the token (commonly a stale/revoked
                // credential from Credential Manager auto-select). Clear the
                // cached credential so the next attempt shows the account
                // picker instead of silently reusing the bad one.
                authManager.clearCredentialState()
                _authState.value = AuthState.Error("Sign-in failed")
            }
        }
    }

    fun onSignInError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    /**
     * Debug-only: sign in against the Firebase Auth emulator as the default
     * test user so two-device sync can be exercised without a real Google
     * account. The UI gates this behind the same compile-time flags, but the
     * early return here guards against accidental calls from release code.
     */
    fun signInAsEmulatorTestUser() {
        if (!BuildConfig.DEBUG || !BuildConfig.USE_FIREBASE_EMULATOR) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                EmulatorAuthHelper.signInAsTestUser()
                _authState.value = AuthState.SignedIn
                syncService.launchInitialUpload()
                syncService.startRealtimeListeners()
                sortPreferencesSyncService.startAfterSignIn()
                themePreferencesSyncService.startAfterSignIn()
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    "Emulator sign-in failed: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            syncService.stopRealtimeListeners()
            sortPreferencesSyncService.stopAfterSignOut()
            themePreferencesSyncService.stopAfterSignOut()
            authManager.signOut()
            _authState.value = AuthState.SignedOut
        }
    }

    fun onSkipSignIn() {
        _skippedSignIn.value = true
    }
}
