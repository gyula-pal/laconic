package cc.gyula.wave.core

import java.math.BigDecimal
import java.math.MathContext

/** Longer than anyone types by hand; also keeps deeply nested input from exhausting the parser's stack. */
private const val MAX_SUM_LENGTH = 64

/**
 * Sums typed into search: "12*7" → "84", "0,1+0,2" → "0.3", "200*15%" → "30". Null unless [input] is
 * plain arithmetic (+ − × ÷ ^ %, brackets, '.' or ',' decimals) with at least one operation in it.
 */
fun calculate(input: String): String? {
    val s = input.replace(" ", "").replace(',', '.')
        .replace('×', '*').replace('x', '*').replace('X', '*').replace('÷', '/').replace('−', '-')
    if (s.isEmpty() || s.length > MAX_SUM_LENGTH || s.any { it !in "0123456789.+-*/^%()" }) return null
    var i = 0
    var operations = 0

    fun fail(): Nothing = throw NumberFormatException()
    fun eat(c: Char): Boolean = (s.getOrNull(i) == c).also { if (it) i++ }

    // Late-bound, since these call back into each other: brackets hold a sum, a power's exponent is signed.
    lateinit var sum: () -> Double
    lateinit var unary: () -> Double
    fun atom(): Double {
        if (eat('(')) return sum().also { if (!eat(')')) fail() }
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        return s.substring(start, i).toDoubleOrNull() ?: fail()
    }
    fun percent(): Double {
        var v = atom()
        while (eat('%')) { v /= 100; operations++ }
        return v
    }
    fun power(): Double {
        val base = percent()
        return if (eat('^')) Math.pow(base, unary()).also { operations++ } else base
    }
    unary = {
        when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> power()
        }
    }
    fun product(): Double {
        var v = unary()
        while (true) {
            v = when {
                eat('*') -> v * unary()
                eat('/') -> v / unary()
                else -> return v
            }
            operations++
        }
    }
    sum = {
        var v = product()
        while (true) {
            v = when {
                eat('+') -> v + product()
                eat('-') -> v - product()
                else -> break
            }
            operations++
        }
        v
    }

    val result = try {
        sum().also { if (i != s.length) fail() }
    } catch (e: NumberFormatException) {
        return null
    }
    if (operations == 0 || result.isNaN() || result.isInfinite()) return null
    return BigDecimal(result).round(MathContext(10)).stripTrailingZeros().let {
        if (it.abs() >= BigDecimal("1e15")) it.toString() else it.toPlainString()
    }
}
