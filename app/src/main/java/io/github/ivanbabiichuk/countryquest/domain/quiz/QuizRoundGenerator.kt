package io.github.ivanbabiichuk.countryquest.domain.quiz

import io.github.ivanbabiichuk.countryquest.domain.model.AnswerOption
import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import io.github.ivanbabiichuk.countryquest.domain.model.QuizQuestion
import java.util.Locale
import kotlin.random.Random

class QuizRoundGenerator(
    private val random: Random = Random.Default
) {

    fun generate(
        countries: List<Country>,
        mode: QuizMode
    ): QuizGenerationResult {
        val eligibleCountries = prepareCountries(countries, mode)

        if (eligibleCountries.size < QUESTIONS_PER_ROUND) {
            return QuizGenerationResult.InsufficientCountries(
                available = eligibleCountries.size,
                required = QUESTIONS_PER_ROUND,
            )
        }

        val questions = eligibleCountries
            .shuffled(random)
            .take(QUESTIONS_PER_ROUND)
            .map { correctCountry ->
                val wrongCountries = eligibleCountries
                    .filter { it.code != correctCountry.code }
                    .shuffled(random)
                    .take(OPTIONS_PER_QUESTION - 1)

                val options = (wrongCountries + correctCountry)
                    .shuffled(random)
                    .map { country ->
                        AnswerOption(
                            id = country.code,
                            text = when (mode) {
                                QuizMode.CAPITAL -> requireNotNull(country.capital)
                                QuizMode.FLAG, QuizMode.COUNTRY -> country.name
                            }
                        )
                    }

                QuizQuestion(
                    mode = mode,
                    country = correctCountry,
                    options = options
                )
            }
        return QuizGenerationResult.Success(questions)
    }

    private fun prepareCountries(
        countries: List<Country>,
        mode: QuizMode
    ): List<Country> {
        val uniqueCountries = countries
            .filter { it.code.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { normalize(it.code) }
            .distinctBy { normalize(it.name) }

        if (mode == QuizMode.FLAG) {
            return uniqueCountries.filter { !it.flagImage.isNullOrBlank() }
        }

        val withCapital = uniqueCountries.filter {
            !it.capital.isNullOrBlank()
        }

        val withUniqueCapital = withCapital
            .groupBy { normalize(requireNotNull(it.capital)) }
            .values
            .filter { it.size == 1 }
            .flatten()

        return when (mode) {
            QuizMode.CAPITAL -> withUniqueCapital

            QuizMode.COUNTRY -> withUniqueCapital.filter {
                it.region.isNotBlank() &&
                        it.languages.isNotEmpty() &&
                        it.languages.all { language -> language.isNotBlank() }
            }

            QuizMode.FLAG -> error("FLAG is handled above")
        }
    }

    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

    companion object {
        const val QUESTIONS_PER_ROUND = 10
        const val OPTIONS_PER_QUESTION = 4
    }
}