package com.pinbeatfinder

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pinbeatfinder.ui.MainScreen
import com.pinbeatfinder.ui.theme.PinBeatFinderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app bar is always post-box red, so status-bar icons must always be light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            PinBeatFinderTheme {
                MainScreen()
            }
        }
    }
}
