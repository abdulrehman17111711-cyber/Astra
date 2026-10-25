package com.astra.ai

import android.app.Application
import com.astra.ai.ai.DefaultAiEngine
import com.astra.ai.apps.AndroidAppManager
import com.astra.ai.apps.AppManager
import com.astra.ai.assistant.AssistantController
import com.astra.ai.assistant.ResponseGenerator
import com.astra.ai.assistant.ResultVerifier
import com.astra.ai.backend.AndroidNetworkMonitor
import com.astra.ai.backend.NetworkMonitor
import com.astra.ai.core.AstraConstants
import com.astra.ai.core.AstraLogger
import com.astra.ai.core.DefaultDispatcherProvider
import com.astra.ai.core.DispatcherProvider
import com.astra.ai.memory.AstraDatabase
import com.astra.ai.memory.DefaultHistoryRepository
import com.astra.ai.memory.DefaultMemoryRepository
import com.astra.ai.memory.HistoryRepository
import com.astra.ai.memory.MemoryRepository
import com.astra.ai.permissions.PermissionManager
import com.astra.ai.settings.DefaultSettingsRepository
import com.astra.ai.settings.SettingsRepository
import com.astra.ai.tasks.DefaultTaskManager
import com.astra.ai.tasks.TaskManager
import com.astra.ai.tools.ToolRegistry
import com.astra.ai.tools.builtin.DeviceStatusTool
import com.astra.ai.tools.builtin.QuickNoteTool
import com.astra.ai.tools.builtin.WorldClockTool
import com.astra.ai.voice.SpeechRecognizerManager
import com.astra.ai.voice.TextToSpeechManager
import com.astra.ai.voice.VoiceSessionManager
import com.astra.ai.voice.WakeActivationManager

class AstraApplication : Application() {

    lateinit var database: AstraDatabase
        private set

    lateinit var memoryRepository: MemoryRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var speechRecognizerManager: SpeechRecognizerManager
        private set

    lateinit var textToSpeechManager: TextToSpeechManager
        private set

    lateinit var wakeActivationManager: WakeActivationManager
        private set

    lateinit var voiceSessionManager: VoiceSessionManager
        private set

    lateinit var assistantController: AssistantController
        private set

    lateinit var permissionManager: PermissionManager
        private set

    lateinit var confirmationManager: com.astra.ai.security.ConfirmationManager
        private set

    lateinit var appManager: AppManager
        private set

    lateinit var networkMonitor: NetworkMonitor
        private set

    lateinit var taskManager: TaskManager
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()

    override fun onCreate() {
        super.onCreate()
        instance = this
        AstraLogger.i("AstraApplication", "Initializing Astra Voice Assistant Engine")

        // 1. Database & Repositories
        database = AstraDatabase.getInstance(this)
        memoryRepository = DefaultMemoryRepository(database.memoryDao())
        historyRepository = DefaultHistoryRepository(database.historyDao())

        // 2. Settings & System Managers
        settingsRepository = DefaultSettingsRepository()
        permissionManager = PermissionManager(this)
        confirmationManager = com.astra.ai.security.ConfirmationManager()
        appManager = AndroidAppManager(this)
        networkMonitor = AndroidNetworkMonitor(this)
        taskManager = DefaultTaskManager()

        // 3. Tool Registry & Complete Part 6 Android Action Toolset
        toolRegistry = ToolRegistry()
        toolRegistry.register(com.astra.ai.tools.builtin.AppLaunchTool(this, appManager))
        toolRegistry.register(com.astra.ai.tools.builtin.OpenAppTool(this, appManager))
        toolRegistry.register(com.astra.ai.tools.builtin.ContactTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.CallTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.CallContactTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.SmsTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.SendMessageTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.AlarmTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.BrowserTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.YouTubeTool(this, appManager))
        toolRegistry.register(com.astra.ai.tools.builtin.WhatsAppTool(this, appManager))
        toolRegistry.register(com.astra.ai.tools.builtin.CameraTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.GalleryTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.SettingsTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.DeviceInfoTool(this))
        toolRegistry.register(DeviceStatusTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.NotificationTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.ReadNotificationsTool(this))
        toolRegistry.register(com.astra.ai.tools.builtin.RefreshAppsTool(appManager))
        toolRegistry.register(com.astra.ai.tools.builtin.SearchAppTool(this))
        toolRegistry.register(QuickNoteTool(memoryRepository))
        toolRegistry.register(com.astra.ai.tools.builtin.DeviceUnlockTool(this))
        val automationEngine = com.astra.ai.automation.AndroidAutomationEngine(this, appManager)
        toolRegistry.register(com.astra.ai.tools.builtin.AccessibilityTool(this, appManager, automationEngine))

        // 4. Voice Subsystems
        speechRecognizerManager = SpeechRecognizerManager(this)
        textToSpeechManager = TextToSpeechManager(this)
        wakeActivationManager = WakeActivationManager(this)

        // 5. AI Reasoning & Response Systems
        val backendClient = com.astra.ai.backend.AstraBackendClient(
            toolRegistry = toolRegistry,
            initialBaseUrl = AstraConstants.LOCAL_DEV_BACKEND_HTTP_URL
        )
        val aiEngine = DefaultAiEngine(
            toolRegistry = toolRegistry,
            appManager = appManager,
            backendClient = backendClient,
            settingsRepository = settingsRepository
        )
        val resultVerifier = ResultVerifier()
        val responseGenerator = ResponseGenerator()

        // 6. Central Assistant Controller
        assistantController = AssistantController(
            speechRecognizerManager = speechRecognizerManager,
            textToSpeechManager = textToSpeechManager,
            aiEngine = aiEngine,
            toolRegistry = toolRegistry,
            resultVerifier = resultVerifier,
            responseGenerator = responseGenerator,
            memoryRepository = memoryRepository,
            historyRepository = historyRepository,
            dispatchers = dispatchers,
            confirmationManager = confirmationManager,
            settingsRepository = settingsRepository
        )

        // 7. Voice Session Manager (continuous loop & wake coordination)
        voiceSessionManager = VoiceSessionManager(
            speechRecognizerManager = speechRecognizerManager,
            textToSpeechManager = textToSpeechManager,
            wakeActivationManager = wakeActivationManager,
            assistantController = assistantController
        )

        AstraLogger.i("AstraApplication", "Astra Voice Assistant initialized successfully with ${toolRegistry.getAllTools().size} tools")
    }

    override fun onTerminate() {
        super.onTerminate()
        voiceSessionManager.release()
        assistantController.release()
    }

    companion object {
        lateinit var instance: AstraApplication
            private set
    }
}
