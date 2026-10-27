package com.example.vnscreenreader

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.vnscreenreader.ui.AppRoot
import com.example.vnscreenreader.ui.HighContrastTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Từ targetSdk 35 hệ thống ép edge-to-edge và bỏ qua android:statusBarColor/navigationBarColor
        // trong theme (đã deprecated). Thanh hệ thống nền đen, biểu tượng sáng, khớp giao diện tương phản cao.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        setContent {
            HighContrastTheme {
                AppRoot()
            }
        }
    }
}
