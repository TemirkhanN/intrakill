package me.nasukhov.intrakill.storage.dao

internal fun Collection<*>.placeholders() = joinToString(",") { "?" }
