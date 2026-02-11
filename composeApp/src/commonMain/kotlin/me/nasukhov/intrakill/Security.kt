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