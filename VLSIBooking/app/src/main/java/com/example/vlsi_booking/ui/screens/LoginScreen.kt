package com.example.vlsi_booking.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.vlsi_booking.ui.auth.AuthViewModel
import com.example.vlsi_booking.ui.components.FakePercentProgressBar

@Composable
fun LoginScreen(vm: AuthViewModel) {
    val current by vm.state.collectAsState()

    if (current.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FakePercentProgressBar(active = true, modifier = Modifier.fillMaxWidth(0.8f))
        }
        return
    }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val canLogin = username.trim().isNotEmpty() && password.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Login")
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome utente") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.login(username, password) },
            enabled = canLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Accedi")
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { vm.signup(username, password) },
            enabled = canLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Registrati")
        }

        current.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it)
        }
    }
}
