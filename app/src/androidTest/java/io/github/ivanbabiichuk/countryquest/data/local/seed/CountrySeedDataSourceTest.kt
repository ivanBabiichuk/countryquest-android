package io.github.ivanbabiichuk.countryquest.data.local.seed

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.ivanbabiichuk.countryquest.app.CountryQuestApplication
import io.github.ivanbabiichuk.countryquest.app.di.NetworkModule
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizGenerationResult
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizRoundGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class CountrySeedDataSourceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val json: Json get() = NetworkModule.provideJson()
    private fun source(ctx: Context = context) = CountrySeedDataSource(ctx, SeedCatalogParser(json))

    @Test
    fun packagedCatalogLoadsFromMainCallerAndContainsExpectedDataAndAssets() = runBlocking {
        val result = withTimeout(10_000.milliseconds) {
            withContext(Dispatchers.Main) { source().load() }
        }
        assertEquals(1, result.seedVersion)
        assertEquals(250, result.countries.size)
        assertEquals(result.countries.map { it.code }.toSet(), result.bundledFlagAssets.keys)
        val countries = result.countries.associateBy { it.code }
        assertEquals("Kyiv", countries.getValue("UA").capital)
        assertEquals("Chișinău", countries.getValue("MD").capital)
        assertEquals("Astana", countries.getValue("KZ").capital)
        assertEquals("Eswatini", countries.getValue("SZ").name)
        assertEquals("Ciudad de la Paz", countries.getValue("GQ").capital)
        assertEquals(listOf("Dutch", "Papiamentu", "English"), countries.getValue("CW").languages)
        assertEquals(result, source().load())
    }

    @Test
    fun packagedCatalogSupportsFullRoundsUnderCurrentGeneratorRules() = runBlocking {
        val catalog = source().load()
        for (mode in QuizMode.entries) {
            val generated = QuizRoundGenerator().generate(catalog.countries, mode)
            assertTrue("Insufficient catalog for $mode", generated is QuizGenerationResult.Success)
            val questions = (generated as QuizGenerationResult.Success).questions
            assertEquals(10, questions.size)
            assertEquals(10, questions.map { it.country.code }.distinct().size)
            questions.forEach { question ->
                assertEquals(4, question.options.size)
                assertEquals(4, question.options.map { it.id }.distinct().size)
                assertEquals(4, question.options.map { it.text }.distinct().size)
                assertEquals(1, question.options.count { it.id == question.country.code })
            }
        }
    }

    @Test
    fun packagedCatalogAndEveryFlagMatchManifestChecksums() = runBlocking {
        val manifest = context.assets.open("catalog/manifest.json").bufferedReader().use { it.readText() }
        val root = json.parseToJsonElement(manifest).jsonObject
        val data = context.assets.open("catalog/countries.json").use { it.readBytes() }
        assertEquals(root.getValue("catalogSha256").jsonPrimitive.content, sha256(data))
        val flags = root.getValue("flags").jsonArray
        val catalog = source().load()
        assertEquals(catalog.bundledFlagAssets.size, flags.size)
        val expectedPaths = catalog.bundledFlagAssets.values.toSet()
        val actualPaths = flags.map { it.jsonObject.getValue("asset").jsonPrimitive.content }
        assertEquals(expectedPaths, actualPaths.toSet())
        for (record in flags) {
            val fields = record.jsonObject
            val path = fields.getValue("asset").jsonPrimitive.content
            val bytes = context.assets.open(path).use { it.readBytes() }
            assertEquals("Checksum for $path", fields.getValue("sha256").jsonPrimitive.content, sha256(bytes))
        }
    }

    @Test
    fun everyBundledSvgDecodesWithProductionCoilFactoryWithoutNetwork() = runBlocking {
        withTimeout(60_000.milliseconds) {
            val requests = AtomicInteger(0)
            val client = OkHttpClient.Builder().addInterceptor {
                requests.incrementAndGet()
                throw IOException("Network is forbidden in bundled asset test")
            }.build()
            val loader = CountryQuestApplication().apply { okHttpClient = client }
                .newImageLoader(context).newBuilder().memoryCache(null).diskCache(null).build()
            try {
                val catalog = source().load()
                for ((code, path) in catalog.bundledFlagAssets) {
                    val result = loader.execute(ImageRequest.Builder(context)
                        .data("file:///android_asset/$path")
                        .size(256, 160)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build())
                    val error = (result as? ErrorResult)?.throwable
                    assertTrue("Could not decode $code: $error", result is SuccessResult)
                    val image = (result as SuccessResult).image
                    assertTrue("Empty image for $code", image.width > 0 && image.height > 0)
                }
                assertEquals(0, requests.get())
            } finally {
                loader.shutdown()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test
    fun assetReadFailurePropagatesInsteadOfReturningEmptyCatalog() = runBlocking {
        val failure = IOException("Assets are unavailable")
        val brokenContext = object : ContextWrapper(context) {
            override fun getAssets(): AssetManager = throw failure
        }
        try {
            source(brokenContext).load()
            fail("Asset read failure must propagate")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
