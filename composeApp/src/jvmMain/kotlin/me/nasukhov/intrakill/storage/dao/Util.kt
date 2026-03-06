package me.nasukhov.intrakill.storage.dao

import kotlinx.datetime.LocalDateTime
import java.sql.PreparedStatement
import java.sql.ResultSet

internal fun Collection<*>.placeholders() = joinToString(",") { "?" }

internal fun Collection<String>.bind(
    statement: PreparedStatement,
    startIndex: Int = 1,
): Int {
    var i = startIndex
    forEach { statement.setString(i++, it) }

    return i
}

internal fun ResultSet.getCreatedAt() =
    LocalDateTime.parse(
        this.getString("created_at").replace(" ", "T"),
    )
