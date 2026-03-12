package me.nasukhov.intrakill.storage.dao

import android.database.Cursor
import kotlinx.datetime.LocalDateTime

internal fun Collection<*>.placeholders() = joinToString(",") { "?" }

internal fun Cursor.getCreatedAt() =
    LocalDateTime.parse(
        this.getString(this.getColumnIndexOrThrow("created_at")).replace(" ", "T"),
    )

internal fun LocalDateTime.toDbFormat() = this.toString().replace("T", " ")
