package com.example.labdesks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.labdesks.data.ApiClient
import com.example.labdesks.data.DeskRepository
import com.example.labdesks.ui.DeskScreen
import com.example.labdesks.ui.DeskViewModel
import com.example.labdesks.ui.DeskViewModelFactory
import com.example.labdesks.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = DeskRepository(ApiClient.api)

        setContent {
            AppTheme {
                val vm: DeskViewModel = viewModel(factory = DeskViewModelFactory(repo))
                DeskScreen(vm = vm)
            }
        }
    }
}
