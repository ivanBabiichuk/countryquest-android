package io.github.ivanbabiichuk.countryquest.feature.quiz.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.SingletonImageLoader
import io.github.ivanbabiichuk.countryquest.BuildConfig
import io.github.ivanbabiichuk.countryquest.app.CountryQuestApplication
import io.github.ivanbabiichuk.countryquest.app.di.NetworkModule
import io.github.ivanbabiichuk.countryquest.domain.model.*
import io.github.ivanbabiichuk.countryquest.feature.quiz.QuizScreen
import io.github.ivanbabiichuk.countryquest.feature.quiz.QuizUiState
import io.github.ivanbabiichuk.countryquest.ui.theme.CountryQuestTheme
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class CountryFlagImageTest {
    @get:Rule val compose = createComposeRule()
    private val requests = CopyOnWriteArrayList<Request>()
    private val readiness = CopyOnWriteArrayList<Boolean>()
    private lateinit var client: OkHttpClient
    private lateinit var loader: ImageLoader
    private lateinit var originalLoader: ImageLoader
    private val svgUrl = "https://flags.example.test/flag.svg"
    private val pngUrl = "https://flags.example.test/flag.png"
    private val both = CountryFlag(svgUrl = svgUrl, pngUrl = pngUrl)

    // The last interceptor supplies deterministic HTTP responses. The production
    // interceptors, Application image-loader factory and real decoders still run.
    private fun install(reply: (Request) -> Response) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        originalLoader = SingletonImageLoader.get(context)
        client = NetworkModule.provideOkHttpClient().newBuilder()
            .addInterceptor { chain ->
                requests.add(chain.request())
                reply(chain.request())
            }.build()
        loader = CountryQuestApplication().apply { okHttpClient = client }
            .newImageLoader(context).newBuilder()
            .memoryCache(null).diskCache(null).build()
        SingletonImageLoader.setUnsafe(loader)
    }

    @After fun cleanUp() {
        if (::loader.isInitialized) {
            SingletonImageLoader.setUnsafe(originalLoader)
            loader.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun validSvgUsesAppUserAgentAndDoesNotRequestPng() {
        install { request ->
            val expected = "CountryQuest/${BuildConfig.VERSION_NAME} (+https://github.com/ivanBabiichuk/countryquest-android)"
            if (request.headers.values("User-Agent") == listOf(expected)) svg(request)
            else response(request, 403)
        }
        show(both)
        awaitReady()
        assertEquals(listOf(svgUrl), urls())
        compose.onNodeWithContentDescription("Country flag").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        assertEquals(false, readiness.first())
    }

    @Test fun forbiddenSvgFallsBackToRealPngDecoder() {
        install { if (it.url.toString() == svgUrl) response(it, 403) else png(it) }
        show(both)
        awaitReady()
        assertEquals(listOf(svgUrl, pngUrl), urls())
        compose.onNodeWithText("Could not load the flag.").assertDoesNotExist()
    }

    @Test fun invalidSvgBodyAlsoFallsBackToPng() {
        install {
            if (it.url.toString() == svgUrl) response(it, 200, "not an image".toByteArray(), "image/svg+xml")
            else png(it)
        }
        show(both)
        awaitReady()
        assertEquals(listOf(svgUrl, pngUrl), urls())
    }

    @Test fun retryRestartsFromSvgAndCanRecoverAfterBothSourcesFail() {
        val recovered = AtomicBoolean(false)
        install { if (recovered.get()) svg(it) else response(it, 403) }
        show(both)
        awaitError()
        assertEquals(listOf(svgUrl, pngUrl), urls())
        assertFalse(readiness.contains(true))
        recovered.set(true)
        compose.onNodeWithText("Try again").performClick()
        awaitReady()
        assertEquals(listOf(svgUrl, pngUrl, svgUrl), urls())
        compose.onNodeWithText("Could not load the flag.").assertDoesNotExist()
    }

    @Test fun duplicateSourcesAreNotRequestedTwicePerAttempt() {
        install { response(it, 404) }
        show(CountryFlag(svgUrl, svgUrl))
        awaitError()
        assertEquals(listOf(svgUrl), urls())
        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(5_000) { requests.size == 2 }
        awaitError()
        assertEquals(listOf(svgUrl, svgUrl), urls())
    }

    @Test fun missingSourcesShowErrorWithoutUselessRetry() {
        install { error("No request expected") }
        show(CountryFlag("  ", null))
        awaitError()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        assertTrue(requests.isEmpty())
        assertEquals(listOf(false), readiness.toList())
    }

    @Test fun pngOnlyCountryLoadsWithoutSvg() {
        install { png(it) }
        show(CountryFlag(null, pngUrl))
        awaitReady()
        assertEquals(listOf(pngUrl), urls())
    }

    @Test fun changingCountryClearsPreviousReadyState() {
        val flag = mutableStateOf(both)
        install { if (it.url.toString() == svgUrl) svg(it) else response(it, 404) }
        compose.setContent {
            CountryQuestTheme { CountryFlagImage(flag.value, { readiness.add(it) }) }
        }
        awaitReady()
        compose.runOnIdle { flag.value = CountryFlag("https://flags.example.test/missing.svg", null) }
        awaitError()
        assertEquals(false, readiness.last())
        assertEquals(listOf(svgUrl, "https://flags.example.test/missing.svg"), urls())
    }

    @Test fun failedFlagDisablesAnswersAndRetryRecoveryEnablesThem() {
        val recovered = AtomicBoolean(false)
        val selected = mutableListOf<Pair<String, String>>()
        install { if (recovered.get()) svg(it) else response(it, 403) }
        val country = Country("UA", "Ukraine", "Kyiv", "Europe", listOf("Ukrainian"), both)
        val question = QuizQuestion(
            mode = QuizMode.FLAG, country = country,
            options = listOf(AnswerOption("UA", "Ukraine"), AnswerOption("FR", "France"),
                AnswerOption("PL", "Poland"), AnswerOption("DE", "Germany"))
        )
        compose.setContent {
            CountryQuestTheme { Surface {
                QuizScreen(QuizMode.FLAG, QuizUiState.Active(listOf(question)), {},
                    { code, id -> selected.add(code to id) }, {}, {})
            } }
        }
        awaitError()
        question.options.forEach { compose.onNodeWithText(it.text).assertIsNotEnabled() }
        compose.onNodeWithText("Score: 0").assertExists()
        recovered.set(true)
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Ukraine") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        question.options.forEach { compose.onNodeWithText(it.text).assertIsEnabled() }
        compose.onNodeWithText("Ukraine").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("UA" to "UA"), selected) }
    }

    private fun show(flag: CountryFlag) {
        compose.setContent {
            CountryQuestTheme { Surface { CountryFlagImage(flag, { readiness.add(it) }) } }
        }
    }
    private fun awaitReady() = compose.waitUntil(5_000) { readiness.lastOrNull() == true }
    private fun awaitError() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Could not load the flag.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }
    private fun urls() = requests.map { it.url.toString() }
    private fun svg(request: Request) = response(request, 200,
        """<svg xmlns="http://www.w3.org/2000/svg" width="40" height="20"><rect width="40" height="20" fill="blue"/></svg>""".toByteArray(), "image/svg+xml")
    private fun png(request: Request): Response {
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        return response(request, 200, bytes, "image/png")
    }
    private fun response(request: Request, code: Int, bytes: ByteArray = byteArrayOf(), type: String = "text/plain") =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(code).message("Test response").header("Content-Type", type)
            .header("Cache-Control", "no-store")
            .body(bytes.toResponseBody(type.toMediaType())).build()
}
