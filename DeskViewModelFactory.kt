package com.example.labdesks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.labdesks.data.DeskRepository

class DeskViewModelFactory(
    private val repo: DeskRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DeskViewModel(repo) as T
    }
}
