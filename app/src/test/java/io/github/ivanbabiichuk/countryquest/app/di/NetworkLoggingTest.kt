package io.github.ivanbabiichuk.countryquest.app.di

import io.github.ivanbabiichuk.countryquest.BuildConfig
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NetworkLoggingTest {
    @Test
    fun `HTTP logging is BODY in debug and absent in release`() {
        val client = NetworkModule.provideOkHttpClient()
        val logging = client.interceptors.filterIsInstance<HttpLoggingInterceptor>()
        if (BuildConfig.DEBUG) {
            assertEquals(1, logging.size)
            assertEquals(HttpLoggingInterceptor.Level.BODY, logging.single().level)
        } else {
            assertTrue(logging.isEmpty())
        }
        assertTrue(client.networkInterceptors.filterIsInstance<HttpLoggingInterceptor>().isEmpty())
    }

    @Test
    fun `actual request logs response body but redacts credentials in debug only`() {
        val messages = CopyOnWriteArrayList<String>()
        val logger = Logger.getLogger(OkHttpClient::class.java.name)
        val previousLevel = logger.level
        val previousParents = logger.useParentHandlers
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { messages.add(record.message) }
            override fun flush() = Unit
            override fun close() = Unit
        }.apply { level = Level.ALL }
        val server = MockWebServer()
        val client = NetworkModule.provideOkHttpClient()
        try {
            logger.level = Level.ALL
            logger.useParentHandlers = false
            logger.addHandler(handler)
            server.start()
            val body = "{\"country\":\"Ukraine\"}"
            server.enqueue(
                MockResponse().setBody(body)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Set-Cookie", "response-secret=hidden"),
            )
            val request = Request.Builder().url(server.url("/countries"))
                .header("Authorization", "Bearer request-secret")
                .header("Cookie", "session=cookie-secret")
                .build()
            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals(body, response.body?.string())
            }

            val log = messages.joinToString("\n")
            if (BuildConfig.DEBUG) {
                assertTrue(log.contains("--> GET"))
                assertTrue(log.contains("/countries"))
                assertTrue(log.contains("<-- 200"))
                assertTrue(log.contains(body))
                assertTrue(log.contains("Authorization:"))
                assertTrue(log.contains("Cookie:"))
                assertTrue(log.contains("Set-Cookie:"))
            } else {
                assertTrue("Release must not log requests or responses", messages.isEmpty())
            }
            assertFalse(log.contains("request-secret"))
            assertFalse(log.contains("cookie-secret"))
            assertFalse(log.contains("response-secret"))
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            server.shutdown()
            logger.removeHandler(handler)
            logger.level = previousLevel
            logger.useParentHandlers = previousParents
        }
    }
}
