package com.anda.launcher.feature.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Alphabet fast scroller (Nova / Niagara). [labels] are normalized sort keys in display order.
 */
@Composable
internal fun FastScroller(labels: List<String>, onScrollTo: suspend (Int) -> Unit) {
    val sections = remember(labels) {
        val firstIndex = LinkedHashMap<Char, Int>()
        labels.forEachIndexed { i, label ->
            val c = label.firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) it else '#' } ?: '#'
            firstIndex.putIfAbsent(c, i)
        }
        firstIndex.entries.map { it.key to it.value }
    }
    if (sections.size < 2) return
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var height by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf<Int?>(null) }

    fun select(y: Float) {
        val index = ((y / height) * sections.size).toInt().coerceIn(0, sections.size - 1)
        if (index != active) {
            active = index
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { onScrollTo(sections[index].second) }
        }
    }

    Box(Modifier.fillMaxHeight().width(28.dp)) {
        Column(
            Modifier.fillMaxHeight().width(28.dp).padding(vertical = 8.dp)
                .onSizeChanged { height = it.height.coerceAtLeast(1) }
                .pointerInput(sections) {
                    detectVerticalDragGestures(
                        onDragStart = { select(it.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ ->
                        change.consume()
                        select(change.position.y)
                    }
                }
                .pointerInput(sections) {
                    detectTapGestures(onPress = { select(it.y); tryAwaitRelease(); active = null })
                },
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sections.forEachIndexed { i, (letter, _) ->
                Text(
                    letter.toString(),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = if (active == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val current = active
        if (current != null) {
            // Bubble showing the active letter, to the left of the strip.
            val y = (height * (current + 0.5f) / sections.size).roundToInt()
            Box(
                Modifier.offset { IntOffset(-64.dp.roundToPx(), y - 24.dp.roundToPx()) }.size(48.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(sections[current].first.toString(), fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
