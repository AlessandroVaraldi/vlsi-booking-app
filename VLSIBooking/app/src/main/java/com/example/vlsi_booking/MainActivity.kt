package com.example.vlsi_booking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vlsi_booking.ui.MainViewModel
import com.example.vlsi_booking.ui.screens.MainScreen
import com.example.vlsi_booking.ui.theme.LabDeskBookingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LabDeskBookingTheme {
                val vm: MainViewModel = viewModel()
                MainScreen(vm)
            }
        }
    }
}
