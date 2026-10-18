package com.astra.ai.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.ai.assistant.AssistantState
import com.astra.ai.assistant.AssistantStateType
import com.astra.ai.ui.theme.AstraAmber
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraEmerald
import com.astra.ai.ui.theme.AstraMagenta
import com.astra.ai.ui.theme.AstraRed
import com.astra.ai.ui.theme.AstraViolet

/**
 * Maps assistant operational states to distinct futuristic theme colors.
 */
fun getStateColor(type: AssistantStateType): Color {
    return when (type) {
        AssistantStateType.IDLE -> AstraCyan
        AssistantStateType.ACTIVATING -> AstraCyan
        AssistantStateType.LISTENING -> AstraCyan
        AssistantStateType.PROCESSING -> AstraViolet
        AssistantStateType.THINKING -> AstraViolet
        AssistantStateType.EXECUTING -> AstraMagenta
        AssistantStateType.SPEAKING -> AstraEmerald
        AssistantStateType.WAITING_FOR_CONFIRMATION -> AstraAmber
        AssistantStateType.SUCCESS -> AstraEmerald
        AssistantStateType.ERROR -> AstraRed
    }
}

/**
 * Minimal, elegant status readout positioned near the Astra Core.
 * Keeps the screen clean and focused without cluttered chat bubble feeds.
 */
@Composable
fun AstraStatusDisplay(
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val (title, subtitle) = when (state) {
            is AssistantState.Idle -> Pair(
                "Ready",
                "Tap Astra Core to speak or choose an action"
            )
            is AssistantState.Activating -> Pair(
                "Activating...",
                state.message
            )
            is AssistantState.Listening -> Pair(
                "Listening...",
                if (state.partialText.isNotBlank()) "\"${state.partialText}\"" else "Listening for your voice..."
            )
            is AssistantState.Processing -> Pair(
                "Processing...",
                "Analyzing \"${state.query}\""
            )
            is AssistantState.Thinking -> Pair(
                "Thinking...",
                "Analyzing \"${state.query}\""
            )
            is AssistantState.Executing -> Pair(
                "Executing ${state.toolName.replace('_', ' ').replaceFirstChar { it.uppercase() }}...",
                state.progressMessage
            )
            is AssistantState.Speaking -> Pair(
                "Speaking...",
                state.text
            )
            is AssistantState.WaitingForConfirmation -> Pair(
                "Confirmation Needed",
                state.request.actionTitle
            )
            is AssistantState.Success -> Pair(
                if (state.toolResult?.success == false) "Notice" else "Done",
                state.summary
            )
            is AssistantState.Error -> Pair(
                "Error",
                state.errorMessage
            )
        }

        val stateColor = getStateColor(state.type)

        AnimatedContent(
            targetState = title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "StatusTitleTransition"
        ) { targetTitle ->
            Text(
                text = targetTitle,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = stateColor,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        AnimatedContent(
            targetState = subtitle,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "StatusSubtitleTransition"
        ) { targetSubtitle ->
            Text(
                text = targetSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
