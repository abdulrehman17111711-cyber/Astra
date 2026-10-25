package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
        configureLockScreenDisplay()
        handleUnlockIntent(intent)
        handleAutoActivateIntent(intent)

        setContent {
            AstraTheme {
                AstraNavHost(app = AstraApplication.instance)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUnlockIntent(intent)
        handleAutoActivateIntent(intent)
    }

    private fun handleAutoActivateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("EXTRA_AUTO_ACTIVATE_VOICE", false) == true) {
            val app = application as? AstraApplication ?: AstraApplication.instance
            val wakeQuery = intent.getStringExtra("EXTRA_WAKE_QUERY")
            if (!wakeQuery.isNullOrBlank()) {
                app.assistantController.processQuery(wakeQuery)
            } else {
                app.assistantController.textToSpeechManager.speak("جی بتائیں، کیا کام ہے؟") {
                    app.assistantController.activateVoiceSession()
                }
            }
        }
    }

    private fun configureLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun handleUnlockIntent(intent: Intent?) {
        if (intent?.getStringExtra("action") == "request_unlock") {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager?.requestDismissKeyguard(this, null)
            }
        }
    }
}

