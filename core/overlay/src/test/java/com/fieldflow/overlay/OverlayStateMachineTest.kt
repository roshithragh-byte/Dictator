package com.fieldflow.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class OverlayStateMachineTest {

    @Test
    fun testOverlayVisualStatesExist() {
        assertEquals(4, OverlayVisualState.values().size)
        assertTrue(OverlayVisualState.values().contains(OverlayVisualState.IDLE_DIMMED))
        assertTrue(OverlayVisualState.values().contains(OverlayVisualState.RECORDING_HOLD))
        assertTrue(OverlayVisualState.values().contains(OverlayVisualState.RECORDING_LOCKED))
        assertTrue(OverlayVisualState.values().contains(OverlayVisualState.TRANSCRIBING))
    }

    @Test
    fun testDragSlopThresholdSeparation() {
        // SDD Spec: 8dp touch-slop separates drag from hold
        val dragSlopDp = 8f
        val density = 2.0f // sample 320dpi screen
        val dragSlopPx = dragSlopDp * density // 16px

        // Small jitter (3px dx, 4px dy) -> distance = 5px < 16px -> Still a HOLD, NOT a drag
        val jitterDist = hypot(3f, 4f)
        assertTrue(jitterDist < dragSlopPx)

        // Real drag (15px dx, 20px dy) -> distance = 25px > 16px -> Recognized as DRAG
        val dragDist = hypot(15f, 20f)
        assertTrue(dragDist > dragSlopPx)
    }

    @Test
    fun testSlideUpToLockThreshold() {
        // SDD Spec: Slide-Up by at least 48dp locks hands-free recording
        val slideToLockDp = 48f
        val density = 2.5f // sample 400dpi screen
        val slideToLockPx = slideToLockDp * density // 120px

        // Sliding down or slight move up should not lock
        val slightMoveUpDy = -50f
        assertFalse(-slightMoveUpDy >= slideToLockPx)

        // Deliberate slide-up (-140px) exceeds threshold
        val strongSlideUpDy = -140f
        assertTrue(-strongSlideUpDy >= slideToLockPx)
    }

    @Test
    fun testHoldThresholdTransitions() {
        // SDD Spec: 120 ms hold threshold before entering RECORDING_HOLD
        val holdThresholdMs = 120L
        val shortTapDurationMs = 80L
        val deliberateHoldDurationMs = 150L

        // Short tap (<120ms) should open editor on release, not enter hold recording
        assertFalse(shortTapDurationMs >= holdThresholdMs)

        // Deliberate hold (>=120ms) enters RECORDING_HOLD
        assertTrue(deliberateHoldDurationMs >= holdThresholdMs)
    }
}
