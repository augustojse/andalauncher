package com.anda.launcher.feature.settings.components.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anda.launcher.feature.settings.components.LocalSettingsHighlight
import kotlinx.coroutines.delay

/**
 * Shared row chrome: click, long-press-to-reset when [modified], the search highlight, and a
 * "Reset to default" accessibility action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingFrame(
    title: String,
    modified: Boolean = false,
    onReset: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val highlighted = LocalSettingsHighlight.current == title
    val requester = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            delay(300)
            requester.bringIntoView()
            flash.snapTo(1f)
            flash.animateTo(0f, tween(1600))
        }
    }
    val canReset = modified && onReset != null && enabled
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    Column(
        Modifier.fillMaxWidth()
            .bringIntoViewRequester(requester)
            .background(highlightColor.copy(alpha = flash.value * 0.7f))
            .then(
                if (onClick != null || canReset) {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = { onClick?.invoke() },
                        onLongClick = if (canReset) {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onReset?.invoke()
                            }
                        } else {
                            null
                        },
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                if (canReset) customActions = listOf(CustomAccessibilityAction("Reset to default") { onReset?.invoke(); true })
            }
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(contentPadding),
        content = content,
    )
}

/** Title with the "changed from default" dot. */
@Composable
internal fun SettingTitle(title: String, modified: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (modified) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
internal fun Summary(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
