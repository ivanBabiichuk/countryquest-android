package io.github.ivanbabiichuk.countryquest.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ivanbabiichuk.countryquest.domain.quiz.QuizRoundGenerator
import io.github.ivanbabiichuk.countryquest.domain.statistics.QuizStatisticsCalculator

@Module
@InstallIn(SingletonComponent::class)
object QuizModule {

    @Provides
    fun provideQuizRoundGenerator(): QuizRoundGenerator = QuizRoundGenerator()

    @Provides
    fun provideQuizStatisticsCalculator(): QuizStatisticsCalculator = QuizStatisticsCalculator()

}