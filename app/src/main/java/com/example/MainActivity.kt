package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.astra.ai.AstraApplication
import com.astra.ai.ui.navigation.AstraNavHost
import com.astra.ai.ui.theme.AstraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AstraTheme {
                AstraNavHost(app = AstraApplication.instance)
            }
        }
    }
}
