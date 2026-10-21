package io.github.ivanbabiichuk.countryquest.app.sync

import io.github.ivanbabiichuk.countryquest.domain.model.CountryCatalogStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFreshnessPolicyTest {
    private val policy = CatalogFreshnessPolicy()

    @Test
    fun `nonempty catalog younger than 24 hours is fresh`() {
        assertFalse(policy.shouldRefresh(CountryCatalogStatus(1, NOW), NOW))
        assertFalse(policy.shouldRefresh(CountryCatalogStatus(1, NOW - DAY + 1), NOW))
    }

    @Test
    fun `exactly 24 hours or older requires refresh`() {
        assertTrue(policy.shouldRefresh(CountryCatalogStatus(1, NOW - DAY), NOW))
        assertTrue(policy.shouldRefresh(CountryCatalogStatus(1, NOW - DAY - 1), NOW))
    }

    @Test
    fun `empty catalog requires refresh despite recent timestamp`() {
        assertTrue(policy.shouldRefresh(CountryCatalogStatus(0, NOW), NOW))
    }

    @Test
    fun `unknown sync time requires refresh for existing catalog`() {
        assertTrue(policy.shouldRefresh(CountryCatalogStatus(10, null), NOW))
    }

    @Test
    fun `future sync time requires refresh after device clock moves back`() {
        assertTrue(policy.shouldRefresh(CountryCatalogStatus(10, NOW + 1), NOW))
    }

    @Test
    fun `invalid negative times require refresh`() {
        assertTrue(policy.shouldRefresh(CountryCatalogStatus(10, -1), NOW))
        assertTrue(policy.shouldRefresh(CountryCatalogStatus(10, 0), -1))
    }

    @Test
    fun `epoch zero is a valid sync time`() {
        assertFalse(policy.shouldRefresh(CountryCatalogStatus(1, 0), DAY - 1))
        assertTrue(policy.shouldRefresh(CountryCatalogStatus(1, 0), DAY))
    }

    @Test
    fun `extreme timestamps cannot overflow age comparison`() {
        assertTrue(policy.shouldRefresh(CountryCatalogStatus(1, 0), Long.MAX_VALUE))
        assertFalse(policy.shouldRefresh(CountryCatalogStatus(1, Long.MAX_VALUE - 1), Long.MAX_VALUE))
        assertTrue(policy.shouldRefresh(CountryCatalogStatus(1, Long.MIN_VALUE), Long.MAX_VALUE))
    }

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = DAY * 10
    }
}
