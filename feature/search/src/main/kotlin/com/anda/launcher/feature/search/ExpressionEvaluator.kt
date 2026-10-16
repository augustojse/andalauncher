package com.anda.launcher.feature.search

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Small recursive-descent calculator for search: + − × ÷ % ^, parentheses, unary minus,
 * sqrt/sin/cos/tan/log/ln, pi and e. No reflection, no scripting engine.
 */
object ExpressionEvaluator {

    /** Only evaluate things that look like math, so "2048 game" doesn't produce a result. */
    fun looksLikeMath(text: String): Boolean {
        val t = text.trim()
        if (t.length < 3) return false
        if (!t.any { it.isDigit() } && !t.contains("pi")) return false
        return t.any { it in "+-*/×÷^%()" } || FUNCTIONS.any { t.startsWith(it) }
    }

    fun evaluate(text: String): Double? = runCatching {
        val parser = Parser(text.replace('×', '*').replace('÷', '/').replace(',', '.').lowercase())
        val value = parser.parseExpression()
        parser.skipSpaces()
        if (!parser.atEnd()) null else value.takeIf { it.isFinite() }
    }.getOrNull()

    fun format(value: Double): String {
        if (value == value.toLong().toDouble() && abs(value) < 1e15) return value.toLong().toString()
        return BigDecimal(value).round(MathContext(12)).stripTrailingZeros().toPlainString()
    }

    private val FUNCTIONS = listOf("sqrt", "sin", "cos", "tan", "log", "ln")

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length
        fun skipSpaces() { while (i < s.length && s[i] == ' ') i++ }

        private fun eat(c: Char): Boolean {
            skipSpaces()
            if (i < s.length && s[i] == c) { i++; return true }
            return false
        }

        fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                v = when {
                    eat('+') -> v + parseTerm()
                    eat('-') -> v - parseTerm()
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                v = when {
                    eat('*') -> v * parseFactor()
                    eat('/') -> v / parseFactor()
                    eat('%') -> v % parseFactor()
                    else -> return v
                }
            }
        }

        private fun parseFactor(): Double {
            val base = parseUnary()
            return if (eat('^')) base.pow(parseFactor()) else base
        }

        private fun parseUnary(): Double = when {
            eat('-') -> -parseUnary()
            eat('+') -> parseUnary()
            else -> parsePrimary()
        }

        private fun parsePrimary(): Double {
            skipSpaces()
            if (eat('(')) {
                val v = parseExpression()
                require(eat(')'))
                return v
            }
            val start = i
            if (i < s.length && s[i].isLetter()) {
                while (i < s.length && s[i].isLetter()) i++
                return when (val name = s.substring(start, i)) {
                    "pi" -> PI
                    "e" -> E
                    else -> {
                        val arg = parsePrimary()
                        when (name) {
                            "sqrt" -> sqrt(arg)
                            "sin" -> sin(Math.toRadians(arg))
                            "cos" -> cos(Math.toRadians(arg))
                            "tan" -> tan(Math.toRadians(arg))
                            "log" -> log10(arg)
                            "ln" -> ln(arg)
                            else -> throw IllegalArgumentException(name)
                        }
                    }
                }
            }
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start)
            return s.substring(start, i).toDouble()
        }
    }
}
