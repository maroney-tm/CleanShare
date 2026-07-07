package com.maroney.cleanshare.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailScreenUrlTest {

    private val removedColor = Color.Red

    @Test
    fun `no query string produces no spans`() {
        val original = "https://example.com/p"
        val annotated = buildOriginalUrlAnnotatedString(original, emptySet(), removedColor)
        assertEquals(original, annotated.text)
        assertEquals(0, annotated.spanStyles.size)
    }

    @Test
    fun `text is unchanged regardless of highlighting`() {
        val original = "https://example.com/p?a=1&utm_source=x&b=2"
        val annotated = buildOriginalUrlAnnotatedString(original, setOf("utm_source=x"), removedColor)
        assertEquals(original, annotated.text)
    }

    @Test
    fun `removed token gets a colored span over its exact range`() {
        val original = "https://example.com/p?utm_source=x"
        val annotated = buildOriginalUrlAnnotatedString(original, setOf("utm_source=x"), removedColor)

        val tokenStart = original.indexOf("utm_source=x")
        val tokenEnd = tokenStart + "utm_source=x".length
        val span = annotated.spanStyles.single()
        assertEquals(SpanStyle(color = removedColor), span.item)
        assertEquals(tokenStart, span.start)
        assertEquals(tokenEnd, span.end)
    }

    @Test
    fun `multiple non-adjacent removed tokens each get their own span`() {
        val original = "https://example.com/p?a=1&utm_source=x&b=2&fbclid=y"
        val annotated = buildOriginalUrlAnnotatedString(
            original, setOf("utm_source=x", "fbclid=y"), removedColor,
        )
        assertEquals(2, annotated.spanStyles.size)

        val utmStart = original.indexOf("utm_source=x")
        val fbclidStart = original.indexOf("fbclid=y")
        val ranges = annotated.spanStyles.map { it.start to it.end }.sortedBy { it.first }
        assertEquals(utmStart to utmStart + "utm_source=x".length, ranges[0])
        assertEquals(fbclidStart to fbclidStart + "fbclid=y".length, ranges[1])
    }

    @Test
    fun `kept params and fragment are not highlighted`() {
        val original = "https://example.com/p?a=1&utm_source=x#section"
        val annotated = buildOriginalUrlAnnotatedString(original, setOf("utm_source=x"), removedColor)
        assertEquals(original, annotated.text)
        assertEquals(1, annotated.spanStyles.size)
    }
}
