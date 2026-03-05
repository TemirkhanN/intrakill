package me.nasukhov.intrakill

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecurityTest {

    @Test
    fun `hash returns non-empty string different from input`() {
        val password = "mypassword"
        val hash = Security.hash(password)
        assertTrue(hash.isNotEmpty())
        assertNotEquals(password, hash)
    }

    @Test
    fun `hash produces unique salted hashes for same password`() {
        val password = "mypassword"
        val hash1 = Security.hash(password)
        val hash2 = Security.hash(password)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `verify returns true for correct password`() {
        val password = "correctpassword"
        val hash = Security.hash(password)
        assertTrue(Security.verify(password, hash))
    }

    @Test
    fun `verify returns false for wrong password`() {
        val hash = Security.hash("correctpassword")
        assertFalse(Security.verify("wrongpassword", hash))
    }

    @Test
    fun `verify returns false for empty hash`() {
        assertFalse(Security.verify("anypassword", ""))
    }

    @Test
    fun `verify returns false for malformed hash`() {
        assertFalse(Security.verify("anypassword", "not-a-valid-bcrypt-hash"))
    }

    @Test
    fun `validatePassword returns empty violations for valid password`() {
        val violations = "validpassword".validatePassword()
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `validatePassword returns empty violations for exactly 6 char password`() {
        val violations = "sixchr".validatePassword()
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `validatePassword returns violation for password shorter than 6 chars`() {
        val violations = "short".validatePassword()
        assertEquals(1, violations.size)
        assertTrue(violations[0].contains("6"))
    }

    @Test
    fun `validatePassword returns violation for empty password`() {
        val violations = "".validatePassword()
        assertEquals(1, violations.size)
    }
}
