package me.nasukhov.intrakill

import org.mindrot.jbcrypt.BCrypt

object Security {
    fun hash(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    fun verify(password: String, hash: String): Boolean = try {
        BCrypt.checkpw(password, hash)
    } catch (e: Exception) {
        false
    }
}

fun String.validatePassword(): List<String> {
    val minPasswordLength = 6
    val violations = mutableListOf<String>()
    if (this.length < minPasswordLength) {
        violations.add("Password must be at least $minPasswordLength characters.")
    }

    return violations
}