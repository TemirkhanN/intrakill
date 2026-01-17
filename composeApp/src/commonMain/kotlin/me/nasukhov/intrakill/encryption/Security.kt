package me.nasukhov.intrakill.encryption

//import io.github.xxfast.kstore.KStore
data class EncryptedPayload(
    val salt: ByteArray,
    val iv: ByteArray,
    val ciphertext: ByteArray
)

interface CryptoManager {
    fun encrypt(data: ByteArray, password: CharArray): EncryptedPayload
    fun decrypt(payload: EncryptedPayload, password: CharArray): ByteArray
}

object SecurityManager {
    private var derivedKey: ByteArray? = null

    // We use PBKDF2 to stretch the password so it's resistant to brute force
    fun initializeKey(password: String) {
        val salt = "constant_salt_for_demo".encodeToByteArray() // In production, generate once and store
        derivedKey = pbkdf2(password.toCharArray(), salt, iterations = 10000, keyLength = 256)
    }

    fun getSecretKey(): ByteArray = derivedKey ?: throw IllegalStateException("Not logged in")

    // Placeholder for actual PBKDF2 implementation (use a library like Korlibs or Krypt)
    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        // Implementation logic here
        return password.toString().encodeToByteArray() // Simplified for structure
    }
}