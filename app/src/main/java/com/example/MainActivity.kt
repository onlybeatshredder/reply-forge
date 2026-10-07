package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.ReplyForgeTheme
import com.example.viewmodel.ReplyForgeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ReplyForgeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReplyForgeTheme(darkTheme = true) {
                HomeScreen(viewModel = viewModel)
            }
        }
    }
}
