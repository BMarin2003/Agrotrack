package com.corall.agrotrack.presentation.gateways

import com.corall.agrotrack.domain.model.SensorStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * HU4: Filtrar sensores por estado (activos, inactivos, sospechosos)
 */
class SensorStatusFilterTest {

    private enum class Filter { ALL, ACTIVE, INACTIVE, SUSPICIOUS }

    private fun matchesFilter(status: SensorStatus, filter: Filter): Boolean = when (filter) {
        Filter.ALL        -> true
        Filter.ACTIVE     -> status == SensorStatus.Normal
        Filter.INACTIVE   -> status == SensorStatus.Offline
        Filter.SUSPICIOUS -> status == SensorStatus.Warning || status == SensorStatus.Critical
    }

    @Test
    fun `filtro Todos acepta cualquier estado`() {
        for (status in SensorStatus.entries) {
            assertTrue(matchesFilter(status, Filter.ALL))
        }
    }

    @Test
    fun `filtro Activos solo acepta Normal`() {
        assertTrue(matchesFilter(SensorStatus.Normal, Filter.ACTIVE))
        assertFalse(matchesFilter(SensorStatus.Warning, Filter.ACTIVE))
        assertFalse(matchesFilter(SensorStatus.Critical, Filter.ACTIVE))
        assertFalse(matchesFilter(SensorStatus.Offline, Filter.ACTIVE))
    }

    @Test
    fun `filtro Inactivos solo acepta Offline`() {
        assertTrue(matchesFilter(SensorStatus.Offline, Filter.INACTIVE))
        assertFalse(matchesFilter(SensorStatus.Normal, Filter.INACTIVE))
        assertFalse(matchesFilter(SensorStatus.Warning, Filter.INACTIVE))
        assertFalse(matchesFilter(SensorStatus.Critical, Filter.INACTIVE))
    }

    @Test
    fun `filtro Sospechosos acepta Warning y Critical, no Normal ni Offline`() {
        assertTrue(matchesFilter(SensorStatus.Warning, Filter.SUSPICIOUS))
        assertTrue(matchesFilter(SensorStatus.Critical, Filter.SUSPICIOUS))
        assertFalse(matchesFilter(SensorStatus.Normal, Filter.SUSPICIOUS))
        assertFalse(matchesFilter(SensorStatus.Offline, Filter.SUSPICIOUS))
    }
}
