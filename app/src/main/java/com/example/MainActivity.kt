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
        startWakeWordServiceIfPermitted()

        setContent {
            AstraTheme {
                AstraNavHost(app = AstraApplication.instance)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startWakeWordServiceIfPermitted()
    }

    private fun startWakeWordServiceIfPermitted() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            com.astra.ai.voice.WakeWordBackgroundService.start(this)
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
                app.assistantController.textToSpeechManager.speak("Hello, how can I help you?") {
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

    private var lastVolumeDownTime = 0L

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastVolumeDownTime in 50..650) {
                lastVolumeDownTime = 0L
                val app = application as? AstraApplication ?: AstraApplication.instance
                app.assistantController.textToSpeechManager.speak("Hello, how can I help you?") {
                    app.assistantController.activateVoiceSession()
                }
                return true
            } else {
                lastVolumeDownTime = now
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

