package com.example.assignment_3.ui.util

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

/** A transient message for a Snackbar. A unique [id] lets the UI show each message exactly once. */
data class UserMessage(val id: Long, val text: String) {
    companion object {
        private val nextId = AtomicLong(1)
        fun of(text: String) = UserMessage(nextId.getAndIncrement(), text)
    }
}

/**
 * A form draft mirrored into [SavedStateHandle] as JSON, so half-typed input survives both
 * rotation (the ViewModel survives) and process death (the handle is restored from the bundle).
 */
class SavedDraft<T>(
    private val handle: SavedStateHandle,
    private val key: String,
    private val serializer: KSerializer<T>,
    initial: T
) {
    /** True when the draft came back from a previous process, so it must not be overwritten by a DB load. */
    val wasRestored: Boolean = handle.contains(key)

    private val _state = MutableStateFlow(
        handle.get<String>(key)?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() } ?: initial
    )
    val state: StateFlow<T> = _state.asStateFlow()

    val value: T get() = _state.value

    fun update(transform: (T) -> T) {
        val next = transform(_state.value)
        _state.value = next
        handle[key] = Json.encodeToString(serializer, next)
    }
}
