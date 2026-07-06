package com.corall.agrotrack.domain.model

import org.junit.Assert.*
import org.junit.Test

class GatewaySyncStatusTest {

    private fun isSyncing(pendingSyncCount: Int) = pendingSyncCount > 0

    @Test
    fun `pendingSyncCount en 0 significa sincronizado`() {
        assertFalse(isSyncing(0))
    }

    @Test
    fun `pendingSyncCount positivo significa sincronizando`() {
        assertTrue(isSyncing(12))
    }

    @Test
    fun `pendingSyncCount de 1 tambien cuenta como sincronizando`() {
        assertTrue(isSyncing(1))
    }
}
