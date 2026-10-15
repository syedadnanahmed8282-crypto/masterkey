package com.example

import com.example.data.security.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `test password generation length and character sets`() {
        val config = PasswordGenerator.GeneratorConfig(
            length = 20,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true
        )
        val password = PasswordGenerator.generate(config)

        assertEquals(20, password.length)
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isLowerCase() })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) })
    }

    @Test
    fun `test password randomness`() {
        val p1 = PasswordGenerator.generate()
        val p2 = PasswordGenerator.generate()
        assertNotEquals(p1, p2)
    }

    @Test
    fun `test password strength calculation`() {
        val weak = PasswordGenerator.calculateStrength("12345")
        val strong = PasswordGenerator.calculateStrength("Ab!9#kL82\$zQ0wP~")

        assertEquals(PasswordGenerator.Strength.VERY_WEAK, weak)
        assertEquals(PasswordGenerator.Strength.VERY_STRONG, strong)
    }
}
