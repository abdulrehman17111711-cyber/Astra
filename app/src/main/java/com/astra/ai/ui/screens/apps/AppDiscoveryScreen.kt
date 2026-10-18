package com.astra.ai.ui.screens.apps

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.astra.ai.apps.AppCapability
import com.astra.ai.apps.AppDiscoveryManager
import com.astra.ai.apps.AppLaunchResult
import com.astra.ai.apps.InstalledApp
import com.astra.ai.ui.components.AstraBackground
import com.astra.ai.ui.theme.AstraAmber
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraEmerald
import com.astra.ai.ui.theme.AstraGlassBorderDark
import com.astra.ai.ui.theme.AstraGlassSurfaceDark
import com.astra.ai.ui.theme.AstraViolet
import kotlinx.coroutines.launch

/**
 * AppDiscoveryScreen
 * Clean, modern settings/debug screen showcasing dynamically discovered Android applications,
 * versioning, verified launch capability, and real-time registry refresh.
 */
@Composable
fun AppDiscoveryScreen(
    discoveryManager: AppDiscoveryManager,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val apps by discoveryManager.installedApps.collectAsState()
    val isRefreshing by discoveryManager.isRefreshing.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter { it.matchesQuery(searchQuery) }
        }
    }

    AstraBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("app_discovery_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Discovered Applications",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${apps.size} launchable apps detected via PackageManager",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            discoveryManager.refreshInstalledApps {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Registry refreshed (${it.size} apps)")
                                }
                            }
                        },
                        enabled = !isRefreshing,
                        modifier = Modifier.testTag("refresh_apps_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AstraCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Applications",
                                tint = AstraCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter applications or packages...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = AstraCyan
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AstraCyan,
                        unfocusedBorderColor = AstraGlassBorderDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_installed_apps_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Application List
                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No launchable apps discovered yet" else "No matching applications found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppItemCard(
                                app = app,
                                onLaunch = {
                                    val result = discoveryManager.launchApp(app.packageName)
                                    scope.launch {
                                        when (result) {
                                            is AppLaunchResult.Success -> {
                                                snackbarHostState.showSnackbar(result.message)
                                            }
                                            is AppLaunchResult.CannotLaunch -> {
                                                snackbarHostState.showSnackbar(result.message)
                                            }
                                            is AppLaunchResult.NotInstalled -> {
                                                snackbarHostState.showSnackbar(result.message)
                                            }
                                            is AppLaunchResult.Error -> {
                                                snackbarHostState.showSnackbar(result.message)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun AppItemCard(
    app: InstalledApp,
    onLaunch: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_card_${app.packageName}"),
        colors = CardDefaults.cardColors(containerColor = AstraGlassSurfaceDark),
        border = BorderStroke(1.dp, AstraGlassBorderDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            AppIconImage(
                drawable = app.icon,
                appName = app.appName
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (app.versionName != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "v${app.versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Capabilities badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (cap in app.capabilities.take(3)) {
                        CapabilityBadge(capability = cap)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Test Launch Button
            IconButton(
                onClick = onLaunch,
                modifier = Modifier.testTag("launch_button_${app.packageName}")
            ) {
                Surface(
                    shape = CircleShape,
                    color = AstraCyan.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Launch ${app.appName}",
                            tint = AstraCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityBadge(capability: AppCapability) {
    val (bgColor, textColor) = when (capability) {
        AppCapability.LAUNCHABLE -> Pair(AstraEmerald.copy(alpha = 0.18f), AstraEmerald)
        AppCapability.KNOWN_INTEGRATION -> Pair(AstraCyan.copy(alpha = 0.18f), AstraCyan)
        AppCapability.SHARE_TARGET -> Pair(AstraViolet.copy(alpha = 0.18f), AstraViolet)
        AppCapability.DEEP_LINK -> Pair(AstraAmber.copy(alpha = 0.18f), AstraAmber)
        else -> Pair(Color.White.copy(alpha = 0.12f), Color.LightGray)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Text(
            text = capability.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun AppIconImage(
    drawable: Drawable?,
    appName: String
) {
    if (drawable != null) {
        val bitmap = remember(drawable) {
            try {
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    drawable.toBitmap(width = 96, height = 96)
                }
            } catch (e: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$appName icon",
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            return
        }
    }

    // Fallback Icon
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AstraCyan.copy(alpha = 0.18f),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = AstraCyan,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
