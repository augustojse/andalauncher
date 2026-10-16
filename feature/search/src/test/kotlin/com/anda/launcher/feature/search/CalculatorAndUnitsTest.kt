package com.anda.launcher.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorAndUnitsTest {
    @Test
    fun precedenceAndParentheses() {
        assertEquals(14.0, ExpressionEvaluator.evaluate("2 + 3 * 4")!!, 1e-9)
        assertEquals(20.0, ExpressionEvaluator.evaluate("(2 + 3) * 4")!!, 1e-9)
        assertEquals(512.0, ExpressionEvaluator.evaluate("2^3^2")!!, 1e-9)
        assertEquals(-4.0, ExpressionEvaluator.evaluate("-2*2")!!, 1e-9)
        assertEquals(3.0, ExpressionEvaluator.evaluate("sqrt 9")!!, 1e-9)
        assertEquals(6.0, ExpressionEvaluator.evaluate("12 ÷ 2")!!, 1e-9)
    }

    @Test
    fun invalidInputReturnsNull() {
        assertNull(ExpressionEvaluator.evaluate("2 +"))
        assertNull(ExpressionEvaluator.evaluate("1/0"))
        assertNull(ExpressionEvaluator.evaluate("hello"))
    }

    @Test
    fun onlyMathLikeQueriesTrigger() {
        assertTrue(ExpressionEvaluator.looksLikeMath("12*7"))
        assertFalse(ExpressionEvaluator.looksLikeMath("2048"))
        assertFalse(ExpressionEvaluator.looksLikeMath("maps"))
    }

    @Test
    fun formatting() {
        assertEquals("42", ExpressionEvaluator.format(42.0))
        assertEquals("0.333333333333", ExpressionEvaluator.format(1.0 / 3))
    }

    @Test
    fun unitConversions() {
        assertEquals(listOf("5 km = 3.106856 mi"), UnitConverter.convert("5 km to mi"))
        assertEquals(listOf("100 °C = 212 °F"), UnitConverter.convert("100c in f"))
        assertEquals(3, UnitConverter.convert("10 kg").size)
        assertTrue(UnitConverter.convert("5 km to kg").isEmpty())
        assertTrue(UnitConverter.convert("spotify").isEmpty())
    }
}
