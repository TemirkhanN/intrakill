package me.nasukhov.intrakill.content

data class Tag(
    val name: String,
    val frequency: Int
) {
    companion object {
        const val MAX_LENGTH = 32
    }

    init {
        check(name.length <= MAX_LENGTH) { "Max length for tag is $MAX_LENGTH characters" }
    }
}