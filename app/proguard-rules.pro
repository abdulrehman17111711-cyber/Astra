# Astra AI Professional ProGuard & R8 Optimization Rules
# Prepared for Google Play Store release distribution by Abdul Rehman

# Preserve line numbers and source file names for production crash symbolication
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Jetpack Room Database entities, DAOs, and database classes
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Preserve Moshi JSON serialization data models and custom adapters
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonQualifier <fields>;
}

# Preserve OkHttp & Retrofit network client models
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Preserve Astra AI Accessibility Agent Service and Application entry points
-keep public class com.astra.ai.AstraApplication extends android.app.Application { *; }
-keep public class com.astra.ai.accessibility.AstraAccessibilityService extends android.accessibilityservice.AccessibilityService { *; }
-keep public class com.example.MainActivity extends androidx.activity.ComponentActivity { *; }

# Keep data models used in ToolRegistry and AgentPlanner
-keep class com.astra.ai.tools.** { *; }
-keep class com.astra.ai.assistant.** { *; }
-keep class com.astra.ai.security.** { *; }
-keep class com.astra.ai.memory.** { *; }
-keep class com.astra.ai.agent.** { *; }

# Strip verbose and debug logging in release binaries
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
