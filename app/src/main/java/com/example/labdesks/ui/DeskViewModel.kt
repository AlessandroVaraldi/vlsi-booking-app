package com.example.labdesks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.labdesks.data.DeskRepository
import com.example.labdesks.data.DeskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DeskUiState(
    val day: LocalDate = LocalDate.now(),
    val loading: Boolean = false,
    val desks: List<DeskStatus> = emptyList(),
    val errorMessage: String? = null,
    val snackMessage: String? = null,
)

class DeskViewModel(
    private val repo: DeskRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DeskUiState())
    val state: StateFlow<DeskUiState> = _state

    init {
        refresh()
    }

    fun setDay(newDay: LocalDate) {
        _state.value = _state.value.copy(day = newDay)
        refresh()
    }

    fun nextDay() = setDay(_state.value.day.plusDays(1))
    fun prevDay() = setDay(_state.value.day.minusDays(1))

    fun consumeSnack() {
        _state.value = _state.value.copy(snackMessage = null)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, errorMessage = null)
            try {
                val dayStr = _state.value.day.toString()
                val desks = repo.loadDesks(dayStr)
                _state.value = _state.value.copy(loading = false, desks = desks)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    errorMessage = e.message ?: "Network error"
                )
            }
        }
    }

    fun book(deskId: Int, name: String, am: Boolean, pm: Boolean) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                _state.value = _state.value.copy(snackMessage = "Please enter a name.")
                return@launch
            }
            if (!am && !pm) {
                _state.value = _state.value.copy(snackMessage = "Select AM and/or PM.")
                return@launch
            }

            try {
                _state.value = _state.value.copy(loading = true)
                repo.bookDesk(_state.value.day.toString(), deskId, trimmed, am, pm)
                _state.value = _state.value.copy(
                    loading = false,
                    snackMessage = "Booked successfully."
                )
                refresh()
            } catch (e: Exception) {
                // Retrofit will throw HttpException for 409 etc; message may be generic
                _state.value = _state.value.copy(
                    loading = false,
                    snackMessage = e.message ?: "Booking failed."
                )
            }
        }
    }
}
