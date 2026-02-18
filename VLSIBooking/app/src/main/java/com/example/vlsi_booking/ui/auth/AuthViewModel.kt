package com.example.vlsi_booking.ui.auth

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vlsi_booking.data.api.ApiClient
import com.example.vlsi_booking.data.api.AuthTokenHolder
import com.example.vlsi_booking.data.model.LoginRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException

private val Application.authDataStore by preferencesDataStore(name = "auth")

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private object Keys {
        val LOGGED_IN = booleanPreferencesKey("logged_in")
        val USERNAME = stringPreferencesKey("username")
        val TOKEN = stringPreferencesKey("token")
    }

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    init {
        viewModelScope.launch {
            getApplication<Application>().authDataStore.data
                .catch { emit(emptyPreferences()) }
                .map { prefs ->
                    val isLoggedIn = prefs[Keys.LOGGED_IN] ?: false
                    val username = prefs[Keys.USERNAME] ?: ""
                    val token = prefs[Keys.TOKEN] ?: ""

                    // Keep OkHttp auth header source in sync.
                    AuthTokenHolder.token = token.ifBlank { null }

                    AuthState(
                        isLoading = false,
                        isLoggedIn = isLoggedIn && username.isNotBlank() && token.isNotBlank(),
                        username = username,
                        token = token
                    )
                }
                .distinctUntilChanged()
                .collect { derived ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoggedIn = derived.isLoggedIn,
                        username = derived.username,
                        token = derived.token
                    )
                }
        }
    }

    fun login(username: String, password: String) {
        val u = username.trim()
        val p = password
        if (u.isEmpty() || p.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val resp = ApiClient.api.login(LoginRequest(username = u, password = p))
                val token = resp.token

                getApplication<Application>().authDataStore.edit { prefs ->
                    prefs[Keys.USERNAME] = resp.username
                    prefs[Keys.TOKEN] = token
                    prefs[Keys.LOGGED_IN] = true
                }

                AuthTokenHolder.token = token
            } catch (e: HttpException) {
                // Wrong credentials (or backend auth error).
                val msg = if (e.code() == 401) "Credenziali non valide" else "Errore ${e.code()}"
                getApplication<Application>().authDataStore.edit { prefs ->
                    prefs[Keys.USERNAME] = ""
                    prefs[Keys.TOKEN] = ""
                    prefs[Keys.LOGGED_IN] = false
                }
                AuthTokenHolder.token = null
                _state.value = _state.value.copy(isLoading = false, errorMessage = msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: "Network error")
            }
        }
    }

    fun signup(username: String, password: String) {
        val u = username.trim()
        val p = password
        if (u.isEmpty() || p.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val resp = ApiClient.api.signup(LoginRequest(username = u, password = p))
                val token = resp.token

                getApplication<Application>().authDataStore.edit { prefs ->
                    prefs[Keys.USERNAME] = resp.username
                    prefs[Keys.TOKEN] = token
                    prefs[Keys.LOGGED_IN] = true
                }

                AuthTokenHolder.token = token
            } catch (e: HttpException) {
                val msg = when (e.code()) {
                    409 -> "Utente già esistente"
                    400 -> "Password troppo corta"
                    404 -> "Endpoint registrazione non trovato (404): controlla BASE_URL e riavvia il backend"
                    else -> "Errore ${e.code()}"
                }
                getApplication<Application>().authDataStore.edit { prefs ->
                    prefs[Keys.USERNAME] = ""
                    prefs[Keys.TOKEN] = ""
                    prefs[Keys.LOGGED_IN] = false
                }
                AuthTokenHolder.token = null
                _state.value = _state.value.copy(isLoading = false, errorMessage = msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: "Network error")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            getApplication<Application>().authDataStore.edit { prefs ->
                prefs[Keys.USERNAME] = ""
                prefs[Keys.TOKEN] = ""
                prefs[Keys.LOGGED_IN] = false
            }
            AuthTokenHolder.token = null
            _state.value = _state.value.copy(isLoading = false, errorMessage = null)
        }
    }
}
