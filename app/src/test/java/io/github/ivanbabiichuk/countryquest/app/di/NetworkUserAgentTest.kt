package io.github.ivanbabiichuk.countryquest.app.di

import io.github.ivanbabiichuk.countryquest.BuildConfig
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class NetworkUserAgentTest {
    @Test fun requestsIdentifyAppWithoutCallerSupplyingHeader() = checkUserAgent(false)
    @Test fun genericUserAgentIsReplacedWithoutDuplicateHeaders() = checkUserAgent(true)

    private fun checkUserAgent(withGenericHeader: Boolean) {
        val server = MockWebServer()
        val client = NetworkModule.provideOkHttpClient()
        try {
            server.start()
            server.enqueue(MockResponse().setBody("ok"))
            val request = Request.Builder().url(server.url("/flag.svg"))
                .apply { if (withGenericHeader) header("User-Agent", "okhttp/4.12.0") }
                .build()
            client.newCall(request).execute().use { assertEquals(200, it.code) }
            val recorded = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals(
                listOf("CountryQuest/${BuildConfig.VERSION_NAME} (+https://github.com/ivanBabiichuk/countryquest-android)"),
                recorded.headers.values("User-Agent")
            )
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            server.shutdown()
        }
    }
}
