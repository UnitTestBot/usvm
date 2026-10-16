package org.usvm.machine.expr

import java.math.BigInteger

private val decimalStringNumber = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
private const val ASCII_RADIX_DIGITS = "0123456789abcdef"
private const val HEXADECIMAL_RADIX = 16
private const val OCTAL_RADIX = 8

/** StringNumericLiteral, excluding Java-only suffixes, hexadecimal floats and extra whitespace. */
internal fun ecmaScriptStringToNumber(value: String): Double {
    val text = value.trim(::isEcmaScriptWhitespace)
    if (text.isEmpty()) return 0.0
    when (text) {
        "Infinity", "+Infinity" -> return Double.POSITIVE_INFINITY
        "-Infinity" -> return Double.NEGATIVE_INFINITY
    }

    val radix = if (text.length >= 2 && text[0] == '0') {
        when (text[1]) {
            'x', 'X' -> HEXADECIMAL_RADIX
            'o', 'O' -> OCTAL_RADIX
            'b', 'B' -> 2
            else -> null
        }
    } else {
        null
    }
    if (radix != null) {
        val digits = text.substring(startIndex = 2)
        val validDigits = digits.isNotEmpty() && digits.all { character ->
            ASCII_RADIX_DIGITS.indexOf(character.lowercaseChar()) in 0 until radix
        }
        return if (validDigits) BigInteger(digits, radix).toDouble() else Double.NaN
    }

    if (!decimalStringNumber.matches(text)) return Double.NaN
    return text.toDouble()
}
