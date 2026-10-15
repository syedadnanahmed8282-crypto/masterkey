package com.example.data.security

import java.security.SecureRandom

object PasswordGenerator {

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"

    private val secureRandom = SecureRandom()

    enum class Strength(val label: String, val score: Int) {
        VERY_WEAK("Very Weak", 1),
        WEAK("Weak", 2),
        FAIR("Fair", 3),
        STRONG("Strong", 4),
        VERY_STRONG("Very Strong", 5)
    }

    data class GeneratorConfig(
        val length: Int = 16,
        val includeUppercase: Boolean = true,
        val includeLowercase: Boolean = true,
        val includeNumbers: Boolean = true,
        val includeSymbols: Boolean = true
    )

    fun generate(config: GeneratorConfig = GeneratorConfig()): String {
        val charPool = StringBuilder()
        val guaranteedChars = mutableListOf<Char>()

        if (config.includeUppercase) {
            charPool.append(UPPERCASE)
            guaranteedChars.add(UPPERCASE[secureRandom.nextInt(UPPERCASE.length)])
        }
        if (config.includeLowercase) {
            charPool.append(LOWERCASE)
            guaranteedChars.add(LOWERCASE[secureRandom.nextInt(LOWERCASE.length)])
        }
        if (config.includeNumbers) {
            charPool.append(NUMBERS)
            guaranteedChars.add(NUMBERS[secureRandom.nextInt(NUMBERS.length)])
        }
        if (config.includeSymbols) {
            charPool.append(SYMBOLS)
            guaranteedChars.add(SYMBOLS[secureRandom.nextInt(SYMBOLS.length)])
        }

        if (charPool.isEmpty()) {
            // Fallback to lowercase + numbers
            charPool.append(LOWERCASE).append(NUMBERS)
            guaranteedChars.add(LOWERCASE[secureRandom.nextInt(LOWERCASE.length)])
            guaranteedChars.add(NUMBERS[secureRandom.nextInt(NUMBERS.length)])
        }

        val poolString = charPool.toString()
        val result = ArrayList<Char>(config.length)

        // Add guaranteed characters first to ensure all selected character sets are represented
        result.addAll(guaranteedChars.take(config.length))

        // Fill remaining length
        while (result.size < config.length) {
            result.add(poolString[secureRandom.nextInt(poolString.length)])
        }

        // Shuffle securely
        for (i in result.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = result[i]
            result[i] = result[j]
            result[j] = temp
        }

        return result.joinToString("")
    }

    fun calculateStrength(password: String): Strength {
        if (password.isEmpty()) return Strength.VERY_WEAK
        var score = 0

        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.length >= 16) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when {
            score <= 2 -> Strength.VERY_WEAK
            score == 3 -> Strength.WEAK
            score == 4 -> Strength.FAIR
            score == 5 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }
    }
}
