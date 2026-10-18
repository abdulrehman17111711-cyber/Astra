package com.astra.ai.tools.builtin

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.astra.ai.apps.AppManager
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: search_app
 * Searches for content inside any application (YouTube, TikTok, Instagram, Spotify, Play Store, Browser, etc.).
 * Dynamically resolves deep links, app intents, and web fallbacks.
 */
class SearchAppTool(
    private val context: Context,
    private val appManager: AppManager? = null
) : Tool {

    override val name: String = "search_app"

    override val description: String =
        "Searches for content inside any target application such as YouTube, TikTok, Instagram, Spotify, Play Store, or Web Browser."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "application",
            type = ToolParamType.STRING,
            description = "Target application to search inside (e.g. YouTube, TikTok, Instagram, Spotify, PlayStore, Browser)",
            required = true
        ),
        ToolParameter(
            name = "query",
            type = ToolParamType.STRING,
            description = "Clean search query string without filler words or command verbs",
            required = true
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val app = (params["application"] as? String)?.trim() ?: "YouTube"
        val rawQuery = (params["query"] as? String)?.trim().orEmpty()

        if (rawQuery.isBlank()) {
            return ToolExecutionResult.failure("Search query cannot be empty.")
        }

        val lowerApp = app.lowercase()
        val encodedQuery = Uri.encode(rawQuery)

        return try {
            val intent = when {
                lowerApp.contains("youtube") -> {
                    val appIntent = Intent(Intent.ACTION_SEARCH).apply {
                        setPackage("com.google.android.youtube")
                        putExtra("query", rawQuery)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (appIntent.resolveActivity(context.packageManager) != null) {
                        appIntent
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                }

                lowerApp.contains("tiktok") -> {
                    val isAccountQuery = rawQuery.startsWith("@") || rawQuery.contains("account", ignoreCase = true) || rawQuery.contains("user", ignoreCase = true)
                    val cleanAccount = rawQuery.replace(Regex("(?i)\\b(account|user|profile|is|ki|latest|video|dhoondo|dekho)\\b"), "").trim().removePrefix("@").trim()
                    
                    val (primaryUri, webUri) = if (isAccountQuery && cleanAccount.isNotBlank()) {
                        Pair(
                            Uri.parse("snssdk1233://user/profile?unique_id=$cleanAccount"),
                            Uri.parse("https://www.tiktok.com/@$cleanAccount")
                        )
                    } else {
                        Pair(
                            Uri.parse("snssdk1233://search/result?keyword=$encodedQuery"),
                            Uri.parse("https://www.tiktok.com/search?q=$encodedQuery")
                        )
                    }

                    val appIntent = Intent(Intent.ACTION_VIEW, primaryUri).apply {
                        setPackage("com.zhiliaoapp.musically")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (appIntent.resolveActivity(context.packageManager) != null) {
                        appIntent
                    } else {
                        Intent(Intent.ACTION_VIEW, webUri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                }

                lowerApp.contains("instagram") || lowerApp.contains("insta") -> {
                    val isAccountQuery = rawQuery.startsWith("@") || rawQuery.contains("account", ignoreCase = true) || rawQuery.contains("user", ignoreCase = true) || rawQuery.contains("profile", ignoreCase = true)
                    val cleanAccount = rawQuery.replace(Regex("(?i)\\b(account|user|profile|is|ki|latest|post|page|dhoondo|dekho)\\b"), "").trim().removePrefix("@").trim()

                    val (primaryUri, webUri) = if (isAccountQuery && cleanAccount.isNotBlank()) {
                        Pair(
                            Uri.parse("instagram://user?username=$cleanAccount"),
                            Uri.parse("https://www.instagram.com/$cleanAccount/")
                        )
                    } else {
                        Pair(
                            Uri.parse("https://www.instagram.com/explore/tags/${encodedQuery.replace(" ", "")}/"),
                            Uri.parse("https://www.instagram.com/explore/tags/${encodedQuery.replace(" ", "")}/")
                        )
                    }

                    val appIntent = Intent(Intent.ACTION_VIEW, primaryUri).apply {
                        setPackage("com.instagram.android")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (appIntent.resolveActivity(context.packageManager) != null) {
                        appIntent
                    } else {
                        Intent(Intent.ACTION_VIEW, webUri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                }

                lowerApp.contains("spotify") -> {
                    val spotifyUri = Uri.parse("spotify:search:$encodedQuery")
                    val appIntent = Intent(Intent.ACTION_VIEW, spotifyUri).apply {
                        setPackage("com.spotify.music")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (appIntent.resolveActivity(context.packageManager) != null) {
                        appIntent
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encodedQuery")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                }

                lowerApp.contains("playstore") || lowerApp.contains("play store") || lowerApp.contains("market") -> {
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encodedQuery")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }

                lowerApp.contains("twitter") || lowerApp.contains("x") -> {
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/search?q=$encodedQuery")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }

                else -> {
                    // Check if app is an installed custom app on the device
                    val installedApp = appManager?.findAppByName(app)
                    if (installedApp != null) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(installedApp.packageName)
                        if (launchIntent != null) {
                            launchIntent.putExtra(SearchManager.QUERY, rawQuery)
                            launchIntent.putExtra("query", rawQuery)
                            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            launchIntent
                        } else {
                            Intent(Intent.ACTION_WEB_SEARCH).apply {
                                putExtra(SearchManager.QUERY, "$app $rawQuery")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        }
                    } else {
                        // Standard Google / Web Search fallback
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, "$app $rawQuery")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
                }
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val fallbackWeb = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=${Uri.encode("$app $rawQuery")}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackWeb)
            }

            val elapsed = System.currentTimeMillis() - startTime
            AstraLogger.i("SearchAppTool", "Successfully executed search for '$rawQuery' in $app")

            val isSocial = lowerApp.contains("tiktok") || lowerApp.contains("instagram") || lowerApp.contains("twitter") || lowerApp.contains("facebook")
            val policyNote = if (isSocial) " Note: Social platforms restrict automated comments and likes; content opened for direct review." else ""

            ToolExecutionResult.success(
                displayText = "Searching $app for \"$rawQuery\"...$policyNote",
                output = mapOf(
                    "application" to app,
                    "query" to rawQuery,
                    "executed" to true,
                    "commentPosted" to false
                ),
                executionTimeMs = elapsed
            )
        } catch (e: Exception) {
            AstraLogger.e("SearchAppTool", "Failed to search $app: ${e.message}", e)
            ToolExecutionResult.failure("Failed to search $app: ${e.message}")
        }
    }
}
