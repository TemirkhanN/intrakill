package me.nasukhov.intrakill.storage.dao

import java.sql.PreparedStatement

internal fun Collection<*>.placeholders() = joinToString(",") { "?" }

internal fun Collection<String>.bind(
    statement: PreparedStatement,
    startIndex: Int = 1
): Int {
    var i = startIndex
    forEach { statement.setString(i++, it) }

    return i
}
