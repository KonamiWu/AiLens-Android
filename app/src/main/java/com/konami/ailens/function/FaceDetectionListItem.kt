package com.konami.ailens.function

import android.content.Context
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R

class FaceDetectionListItem(private val navController: NavController): ListItem {
    override val title: String
        get() = "Face Detection"

    override fun execute() {
        navController.navigate(R.id.action_FunctionFragment_to_FaceDetectionFragment)
    }
}