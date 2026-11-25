package com.fieldflow.transform

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTransformerTest {

    private val transformer = DocumentTransformer()

    @Test
    fun testEmptyTranscriptReturnsFailure() = runBlocking {
        val result = transformer.transform("", TransformTemplate.CLEAN_DICTATION)
        assertTrue(result.isFailure)
    }

    @Test
    fun testCleanDictationRemovesFillers() = runBlocking {
        val raw = "Um hello, uh we are testing like the dictaphone you know."
        val result = transformer.transform(raw, TransformTemplate.CLEAN_DICTATION)
        assertTrue(result.isSuccess)
        val text = result.getOrThrow()
        assertTrue(!text.contains("um", ignoreCase = true))
        assertTrue(!text.contains("uh", ignoreCase = true))
        assertTrue(text.contains("testing"))
    }

    @Test
    fun testStoryProseHasHeading() = runBlocking {
        val raw = "The rain beat down upon the neon pavement of Neo-Shinjuku."
        val result = transformer.transform(raw, TransformTemplate.STORY_PROSE)
        assertTrue(result.isSuccess)
        val text = result.getOrThrow()
        assertTrue(text.startsWith("## Story Prose Draft"))
        assertTrue(text.contains("Neo-Shinjuku"))
    }

    @Test
    fun testCodeAndSpecsStructure() = runBlocking {
        val raw = "Need a background audio recorder with circular buffer."
        val result = transformer.transform(raw, TransformTemplate.CODE_AND_SPECS)
        assertTrue(result.isSuccess)
        val text = result.getOrThrow()
        assertTrue(text.contains("### Goal"))
        assertTrue(text.contains("### Components & APIs"))
        assertTrue(text.contains("### Edge Cases & Next Steps"))
    }

    @Test
    fun testWorkLogStructure() = runBlocking {
        val raw = "Pump pressure was fluctuating around 32 psi on Line 4."
        val result = transformer.transform(raw, TransformTemplate.WORK_LOG)
        assertTrue(result.isSuccess)
        val text = result.getOrThrow()
        assertTrue(text.contains("## Field Work Log"))
        assertTrue(text.contains("32 psi"))
        assertTrue(text.contains("- [ ] Review field report"))
    }

    @Test
    fun testChecklistStructure() = runBlocking {
        val raw = "Buy AA batteries\nCalibrate microphone\nUpdate firmware"
        val result = transformer.transform(raw, TransformTemplate.CHECKLIST)
        assertTrue(result.isSuccess)
        val text = result.getOrThrow()
        assertTrue(text.contains("- [ ] Buy AA batteries"))
        assertTrue(text.contains("- [ ] Calibrate microphone"))
        assertTrue(text.contains("- [ ] Update firmware"))
    }
}
