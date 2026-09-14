package com.pinbeatfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pinbeatfinder.ui.MainScreen
import com.pinbeatfinder.ui.theme.PinBeatFinderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PinBeatFinderTheme {
                MainScreen()
            }
        }
    }
}
