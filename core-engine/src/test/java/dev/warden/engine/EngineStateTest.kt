package dev.warden.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineStateTest {

    @Test
    fun engineRunningFlowUpdates() {
        Engine.setRunning(false)
        assertFalse(Engine.running.value)

        Engine.setRunning(true)
        assertTrue(Engine.running.value)

        Engine.setRunning(false)
        assertFalse(Engine.running.value)
    }

    @Test
    fun protectionActionConstants() {
        assertEquals("dev.warden.engine.ACTION_STOP", ProtectionActionReceiver.ACTION_STOP)
    }

    @Test
    fun protectionNotificationConstants() {
        assertEquals(1, ProtectionNotification.NOTIFICATION_ID_RUNNING)
    }
}
