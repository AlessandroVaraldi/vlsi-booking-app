package com.example.vlsi_booking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vlsi_booking.data.api.ApiClient
import com.example.vlsi_booking.data.model.BookingCreate
import com.example.vlsi_booking.data.model.DeskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val desks: List<DeskStatus> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        refresh()
    }

    fun setDate(d: LocalDate) {
        _state.value = _state.value.copy(selectedDate = d, errorMessage = null)
        refresh()
    }

    fun refresh() {
        val day = _state.value.selectedDate.toString()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val desks = ApiClient.api.getDesks(day)
                _state.value = _state.value.copy(desks = desks, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Network error"
                )
            }
        }
    }

    fun bookDesk(deskId: Int, name: String, am: Boolean, pm: Boolean) {
        val day = _state.value.selectedDate.toString()
        viewModelScope.launch {
            try {
                ApiClient.api.createBooking(
                    BookingCreate(
                        desk_id = deskId,
                        day = day,
                        booked_by = name,
                        am = am,
                        pm = pm
                    )
                )
                refresh()
            } catch (e: retrofit2.HttpException) {
                val msg = when (e.code()) {
                    409 -> "Conflict: desk already booked or you already have a booking for this slot."
                    400 -> "Invalid request."
                    else -> "Error ${e.code()}"
                }
                _state.value = _state.value.copy(errorMessage = msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = e.message ?: "Network error")
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
