package com.talksy.app.ui.screens.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.talksy.app.AppGraph
import com.talksy.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun signInWithGoogle(context: Context) {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) {
            _state.value = LoginUiState(error = "Google client id is not configured in gradle.properties")
            return
        }
        _state.value = LoginUiState(loading = true)
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                val idToken = if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                } else null

                if (idToken.isNullOrBlank()) {
                    _state.value = LoginUiState(error = "Could not read Google account")
                    return@launch
                }

                val response = AppGraph.auth.loginWithGoogle(idToken).getOrElse {
                    _state.value = LoginUiState(error = it.message)
                    return@launch
                }
                AppGraph.session.save(
                    access = response.accessToken,
                    refresh = response.refreshToken,
                    userId = response.user.id,
                    name = response.user.name,
                    username = response.user.username,
                    picture = response.user.picture,
                )
                AppGraph.socket.connect(response.accessToken)
                _state.value = LoginUiState(loading = false)
            } catch (e: Exception) {
                _state.value = LoginUiState(error = e.message ?: "Sign-in cancelled")
            }
        }
    }
}
