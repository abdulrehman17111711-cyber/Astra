package com.astra.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraGlassBorderDark
import com.astra.ai.voice.SpeechRecognizerManager
import com.astra.ai.voice.VoiceState

/**
 * Minimal frosted-glass keyboard input sheet providing typed command fallback
 * and Voice-to-Text (STT) live dictation directly into the prompt box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardInputSheet(
    onDismiss: () -> Unit,
    onSubmitQuery: (String) -> Unit,
    speechRecognizerManager: SpeechRecognizerManager? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var textQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val voiceState by speechRecognizerManager?.voiceState?.collectAsState() ?: remember { mutableStateOf(VoiceState.IDLE) }
    val audioLevel by speechRecognizerManager?.audioLevel?.collectAsState() ?: remember { mutableStateOf(0f) }
    val isDictating = voiceState == VoiceState.LISTENING || voiceState == VoiceState.PROCESSING

    // Stream real-time speech results directly into textQuery (Voice-to-Text)
    LaunchedEffect(speechRecognizerManager) {
        speechRecognizerManager?.partialResults?.collect { partial ->
            if (partial.isNotBlank()) {
                textQuery = partial
            }
        }
    }

    LaunchedEffect(speechRecognizerManager) {
        speechRecognizerManager?.speechResults?.collect { finalTranscript ->
            if (finalTranscript.isNotBlank()) {
                textQuery = finalTranscript
            }
        }
    }

    // Cancel dictation when sheet is dismissed
    DisposableEffect(Unit) {
        onDispose {
            if (isDictating) {
                speechRecognizerManager?.cancel()
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = AstraCyan.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Aa", color = AstraCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Type or Dictate to Astra",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isDictating) {
                            Text(
                                text = "Voice-to-Text active • Speak now...",
                                style = MaterialTheme.typography.bodySmall,
                                color = AstraCyan,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("keyboard_sheet_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Keyboard Input",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Natural Voice Prompt Suggestions Chips
            val naturalVoicePrompts = listOf(
                "منان کو سم ون سے کال کرو",
                "ماما کو سم ون سے کال کرو",
                "واٹس ایپ پہ منان کو میسج کرو کہ جلدی گھر ا جائے",
                "سلمان کو میسج کرو کہ گیم میں اجاؤ",
                "موبائل انلاک کرو",
                "TikTok pe Free Fire Pakistan official ki latest video search karo",
                "YouTube par CID search karo",
                "Ammi ko SIM 1 se call karo",
                "Check karo mere phone mein TikTok installed hai ya nahi",
                "Kal subah 7 baje alarm laga do",
                "Battery kitni hai?"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                naturalVoicePrompts.forEach { prompt ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            onSubmitQuery(prompt)
                            onDismiss()
                        },
                        label = { Text(prompt, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, AstraGlassBorderDark.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Waveform / Voice-to-Text Feedback Banner
            AnimatedVisibility(
                visible = isDictating,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AstraCyan.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AstraCyan.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Transcribing voice in real time...",
                            style = MaterialTheme.typography.bodySmall,
                            color = AstraCyan
                        )
                        AstraWaveform(
                            audioLevel = audioLevel,
                            baseColor = AstraCyan,
                            barCount = 7
                        )
                    }
                }
            }

            // Text Input Field with Voice to Text Mic Button & Send Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textQuery,
                    onValueChange = { textQuery = it },
                    placeholder = {
                        Text(
                            if (isDictating) "Listening... speak now" else "Type your command or question...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AstraCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        if (textQuery.isNotBlank()) {
                            IconButton(onClick = { textQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear text",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("keyboard_text_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Voice to Text (Dictation) Toggle Button
                if (speechRecognizerManager != null) {
                    IconButton(
                        onClick = {
                            if (isDictating) {
                                speechRecognizerManager.stopListening()
                            } else {
                                speechRecognizerManager.startListening()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isDictating) Color(0xFFE53935) else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("voice_to_text_dictate_button")
                    ) {
                        Icon(
                            imageVector = if (isDictating) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isDictating) "Stop Voice to Text" else "Start Voice to Text",
                            tint = if (isDictating) Color.White else AstraCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Send Button
                IconButton(
                    onClick = {
                        if (textQuery.isNotBlank()) {
                            if (isDictating) {
                                speechRecognizerManager?.stopListening()
                            }
                            onSubmitQuery(textQuery.trim())
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AstraCyan)
                        .testTag("keyboard_submit_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Query",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
