package com.example.vlsi_booking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.vlsi_booking.ui.MainViewModel
import com.example.vlsi_booking.ui.auth.AuthViewModel
import com.example.vlsi_booking.ui.screens.MainScreen
import com.example.vlsi_booking.ui.screens.LoginScreen
import com.example.vlsi_booking.ui.theme.LabDeskBookingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LabDeskBookingTheme {
                val authVm: AuthViewModel = viewModel()
                val authState by authVm.state.collectAsState()

                if (authState.isLoggedIn) {
                    val vm: MainViewModel = viewModel()
                    MainScreen(
                        vm = vm,
                        defaultBookingName = authState.username,
                        onLogout = { authVm.logout() },
                        onChangePassword = { oldP, newP, onOk, onErr ->
                            authVm.changePassword(oldP, newP, onOk, onErr)
                        }
                    )
                } else {
                    LoginScreen(vm = authVm)
                }
            }
        }
    }
}
