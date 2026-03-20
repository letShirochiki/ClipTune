package com.cliptune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliptune.ui.ClipTuneTheme
import com.cliptune.ui.MainScreen
import com.cliptune.viewmodel.MainViewModel

/**
 * 应用入口 Activity，负责承载 Compose UI
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainViewModel: MainViewModel = viewModel()

            ClipTuneTheme {
                MainScreen(viewModel = mainViewModel)
            }
        }
    }
}
