package com.us.copilot.ai.nebians

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the Nebians catalog: unique slugs, resolvable defaults, honest flags. */
class NebiansCatalogTest {

    @Test
    fun `slugs are unique`() {
        val slugs = NebiansCatalog.providers.map { it.slug }
        assertEquals(slugs.size, slugs.toSet().size)
    }

    @Test
    fun `every non-custom provider resolves a default model`() {
        NebiansCatalog.providers.filter { it.slug != "custom" }.forEach { provider ->
            val model = NebiansCatalog.effectiveModel(provider.slug, "")
            assertTrue("${provider.slug} has no default model", model.isNotBlank())
        }
    }

    @Test
    fun `tryingopen is the flagship free fleet`() {
        val provider = NebiansCatalog.find("tryingopen")
        assertNotNull(provider)
        assertEquals(16, provider!!.models.size)
        assertEquals(ReasoningSupport.EFFORT, provider.reasoning)
        assertTrue(provider.supportsFiles)
        assertFalse(provider.keyRequired)
    }

    @Test
    fun `keyless pools need no key`() {
        listOf("llm7", "kilo", "zen").forEach { slug ->
            val provider = NebiansCatalog.find(slug)
            assertNotNull(slug, provider)
            assertFalse("$slug should be keyless", provider!!.keyRequired)
        }
    }

    @Test
    fun `official providers require keys`() {
        listOf("openai", "anthropic", "gemini", "deepseek", "agnes").forEach { slug ->
            assertTrue("$slug should require a key", NebiansCatalog.find(slug)!!.keyRequired)
        }
    }

    @Test
    fun `free providers cover the offline-first default`() {
        assertTrue(NebiansCatalog.freeProviders().size >= 8)
    }

    @Test
    fun `unknown slug returns null`() {
        assertEquals(null, NebiansCatalog.find("qwen"))
    }

    @Test
    fun `model lookup is case-insensitive`() {
        assertNotNull(NebiansCatalog.modelFor("k2think", "ifm/k2-horizon-375b-a23b"))
    }
}
