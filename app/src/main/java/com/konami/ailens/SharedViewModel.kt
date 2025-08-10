package com.konami.ailens

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class SharedViewModel : ViewModel() {
    val permissionGranted = MutableSharedFlow<Boolean>()
}