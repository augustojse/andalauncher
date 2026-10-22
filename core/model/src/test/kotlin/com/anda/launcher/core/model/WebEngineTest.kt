package com.anda.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebEngineTest {
    @Test
    fun queryIsEncodedIntoTheTemplate() {
        val engine = WebEngine("Kagi", "https://kagi.com/search?q=%s")
        assertEquals("https://kagi.com/search?q=caf%C3%A9%20%26%20bar", engine.urlFor("café & bar"))
    }

    @Test
    fun templatesNeedAWebSchemeAndAPlaceholder() {
        assertTrue(WebEngine.isValidTemplate("https://duckduckgo.com/?q=%s"))
        assertTrue(WebEngine.isValidTemplate("http://example.com/%s"))
        assertFalse(WebEngine.isValidTemplate("https://example.com/search"))
        assertFalse(WebEngine.isValidTemplate("intent://example.com/%s"))
        assertFalse(WebEngine.isValidTemplate("javascript:alert('%s')"))
        assertFalse(WebEngine.isValidTemplate("https://example.com/ %s"))
    }

    @Test
    fun sanitizeDropsBrokenEnginesAndCapsTheList() {
        val good = WebEngine("Wiki", "https://en.wikipedia.org/w/index.php?search=%s")
        val engines = listOf(WebEngine("", good.urlTemplate), WebEngine("Bad", "file:///%s")) + List(15) { good }
        val safe = LauncherSettings(search = SearchSettings(webEngines = engines)).sanitized().search.webEngines
        assertEquals(WebEngine.MAX_ENGINES, safe.size)
        assertTrue(safe.all { it == good })
    }
}
