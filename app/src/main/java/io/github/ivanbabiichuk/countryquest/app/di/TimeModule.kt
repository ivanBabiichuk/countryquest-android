package io.github.ivanbabiichuk.countryquest.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ivanbabiichuk.countryquest.core.time.SystemTimeProvider
import io.github.ivanbabiichuk.countryquest.core.time.TimeProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    abstract fun bindTimeProvider(implementation: SystemTimeProvider): TimeProvider
}