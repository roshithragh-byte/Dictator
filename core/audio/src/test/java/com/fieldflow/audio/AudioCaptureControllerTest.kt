package com.fieldflow.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCaptureControllerTest {

    @Test
    fun testRingBufferUnderCapacityDrainOrdering() {
        val ring = CircularPcmRingBuffer(capacity = 10)
        val input = shortArrayOf(1, 2, 3, 4, 5)

        ring.write(input)
        assertEquals(5, ring.currentSize)

        val drained = ring.drain()
        assertArrayEquals(input, drained)
        assertEquals(0, ring.currentSize)
        assertTrue(ring.drain().isEmpty())
    }

    @Test
    fun testRingBufferWrapAroundAndDrainOrdering() {
        // Capacity = 5.
        // Write 1, 2, 3 -> buffer: [1, 2, 3]
        // Write 4, 5, 6, 7 -> total 7 written to capacity 5.
        // Oldest 1, 2 dropped. Retained: 3, 4, 5, 6, 7 in chronological order.
        val ring = CircularPcmRingBuffer(capacity = 5)
        ring.write(shortArrayOf(1, 2, 3))
        ring.write(shortArrayOf(4, 5, 6, 7))

        assertEquals(5, ring.currentSize)
        val drained = ring.drain()
        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7), drained)
        assertEquals(0, ring.currentSize)
    }

    @Test
    fun testRingBufferExactCapacity() {
        val ring = CircularPcmRingBuffer(capacity = 4)
        ring.write(shortArrayOf(10, 20, 30, 40))

        assertEquals(4, ring.currentSize)
        val drained = ring.drain()
        assertArrayEquals(shortArrayOf(10, 20, 30, 40), drained)
    }

    @Test
    fun testRingBufferMultipleOverwrites() {
        val ring = CircularPcmRingBuffer(capacity = 3)
        // Write 1..9
        for (i in 1..9) {
            ring.write(shortArrayOf(i.toShort()))
        }
        assertEquals(3, ring.currentSize)
        val drained = ring.drain()
        assertArrayEquals(shortArrayOf(7, 8, 9), drained)
    }
}
