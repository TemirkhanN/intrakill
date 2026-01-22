package me.nasukhov.intrakill

import androidx.compose.runtime.staticCompositionLocalOf

fun interface EventEmitter {
    fun emit(event: AppEvent)
}

val LocalEventEmitter = staticCompositionLocalOf<EventEmitter> {
    error("EventEmitter not provided")
}