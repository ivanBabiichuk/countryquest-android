package io.github.ivanbabiichuk.countryquest.data.remote

import io.github.ivanbabiichuk.countryquest.data.remote.dto.CountryDto
import retrofit2.http.GET

interface CountriesApi {

    @GET("countries")
    suspend fun getCountries(): List<CountryDto>
}