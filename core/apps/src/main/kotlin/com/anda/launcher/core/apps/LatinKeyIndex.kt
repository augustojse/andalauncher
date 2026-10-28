package com.anda.launcher.core.apps

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield

/**
 * Latin search keys by label, built in the background. Loading the ICU rules costs 70 ms to over
 * a second per script (docs/plans/0.6.0.md), so neither start-up nor a query ever waits for it:
 * search uses whatever [keys] holds, and a label without a key yet simply has no Latin match.
 */
class LatinKeyIndex(private val keyOf: (String) -> String? = LatinKeys::of) {
    private val _keys = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Label → normalized Latin key, only for labels with letters in another script. */
    val keys: StateFlow<Map<String, String>> = _keys

    private val requests = Channel<Collection<String>>(Channel.UNLIMITED)

    // Only touched by the collector in [run], one label set at a time.
    private val seen = HashSet<String>()

    /** Labels that aren't app names (shortcuts); keyed on the next background pass. */
    fun request(labels: Collection<String>) {
        requests.trySend(labels)
    }

    /**
     * Keys every label from [labels] and [request], waiting [startDelayMs] first so the work
     * stays out of start-up. Nothing runs, and ICU is never loaded, while [enabled] is false.
     */
    suspend fun run(labels: Flow<Collection<String>>, enabled: Flow<Boolean>, startDelayMs: Long) {
        enabled.distinctUntilChanged().collectLatest { on ->
            if (!on) return@collectLatest
            delay(startDelayMs)
            merge(labels, requests.receiveAsFlow()).collect(::add)
        }
    }

    private suspend fun add(labels: Collection<String>) {
        for (label in labels) {
            if (label in seen) continue
            // One label at a time, so turning the setting off stops a long first pass; a label
            // counts as seen only once its key is published.
            yield()
            val key = runCatching { keyOf(label) }.getOrNull()
            if (key != null) _keys.update { it + (label to key) }
            seen.add(label)
        }
    }
}
