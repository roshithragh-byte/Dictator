package com.fieldflow.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SileroVadSegmenterTest {

    private val segmenter = SileroVadSegmenter()

    @Test
    fun testSilentAudioProducesNoSegments() {
        // 2 seconds of total silence (16000 samples/sec * 2 = 32000 shorts)
        val silence = ShortArray(32_000) { 0 }
        val segments = segmenter.segment("test_rec", silence)
        assertTrue(segments.isEmpty())
    }

    @Test
    fun testAudioWithSpeechProducesSegments() {
        // 1 second of silence, 1 second of loud sine wave, 1 second of silence
        val sampleRate = 16_000
        val totalSamples = sampleRate * 3
        val pcm = ShortArray(totalSamples)

        val speechStart = sampleRate
        val speechEnd = sampleRate * 2

        for (i in speechStart until speechEnd) {
            val angle = 2.0 * Math.PI * 440.0 * (i - speechStart) / sampleRate
            pcm[i] = (Math.sin(angle) * 16000).toInt().toShort()
        }

        val segments = segmenter.segment("test_rec", pcm)
        assertTrue(segments.isNotEmpty())
        val first = segments.first()
        assertEquals("test_rec", first.recordingId)
        assertTrue(first.pcmSamples.isNotEmpty())
    }

    @Test
    fun testTooShortBufferProducesNoSegments() {
        val shortBuffer = ShortArray(100)
        val segments = segmenter.segment("short", shortBuffer)
        assertTrue(segments.isEmpty())
    }
}
