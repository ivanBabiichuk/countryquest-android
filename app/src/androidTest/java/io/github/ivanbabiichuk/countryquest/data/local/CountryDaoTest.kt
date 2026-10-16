package io.github.ivanbabiichuk.countryquest.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ivanbabiichuk.countryquest.data.local.dao.CountryDao
import io.github.ivanbabiichuk.countryquest.data.local.entity.CountryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountryDaoTest {

    private lateinit var database: CountryQuestDatabase
    private lateinit var dao: CountryDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        database = Room.inMemoryDatabaseBuilder(
            context,
            CountryQuestDatabase::class.java
        ).build()

        dao = database.countryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storesUnicodeLanguagesAndBothFlagFormats() = runBlocking {
        val country = moldova()

        dao.upsertCountries(listOf(country))

        assertEquals(listOf(country), dao.getCountries())
        assertEquals(listOf(country), dao.observeCountries().first())
    }

    @Test
    fun upsertUpdatesExistingCountryWithoutDuplicates() = runBlocking {
        val original = moldova()
        dao.upsertCountries(listOf(original))

        val updated = original.copy(
            capital = null,
            languages = emptyList(),
            flagPngUrl = null
        )

        dao.upsertCountries(listOf(updated))

        assertEquals(listOf(updated), dao.getCountries())
    }

    @Test
    fun newDatabaseHasEmptyCatalog() = runBlocking {
        assertTrue(dao.getCountries().isEmpty())
        assertTrue(dao.observeCountries().first().isEmpty())
    }

    private fun moldova() = CountryEntity(
        code = "MD",
        name = "Moldova",
        capital = "Chișinău",
        region = "Europe",
        languages = listOf("Română", "Language, with comma", "Quoted \"name\""),
        flagSvgUrl = "https://flagcdn.com/md.svg",
        flagPngUrl = "https://flagcdn.com/w320/md.png"
    )

}