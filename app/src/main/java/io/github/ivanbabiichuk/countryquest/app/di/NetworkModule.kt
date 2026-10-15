package io.github.ivanbabiichuk.countryquest.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ivanbabiichuk.countryquest.BuildConfig
import io.github.ivanbabiichuk.countryquest.data.remote.CountriesApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    val loggingInterceptor = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY

                        redactHeader("Authorization")
                        redactHeader("Cookie")
                        redactHeader("Set-Cookie")
                    }

                    addInterceptor(loggingInterceptor)
                }
            }
            .build()
    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://countries.dev/")
            .client(client)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json".toMediaType(),
                ),
            )
            .build()

    @Provides
    @Singleton
    fun provideCountriesApi(retrofit: Retrofit): CountriesApi =
        retrofit.create(CountriesApi::class.java)
}