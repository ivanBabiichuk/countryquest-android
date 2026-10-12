package io.github.ivanbabiichuk.countryquest.domain.quiz

import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.QuizMode
import org.junit.Assert
import org.junit.Test
import kotlin.random.Random

class QuizRoundGeneratorTest {

    @Test
    fun `all modes generate ten questions with four valid options`() {
        for (mode in QuizMode.entries) {
            val result = QuizRoundGenerator(Random(42)).generate(countries(), mode)

            Assert.assertTrue(result is QuizGenerationResult.Success)

            val questions = (result as QuizGenerationResult.Success).questions

            Assert.assertEquals(10, questions.size)
            Assert.assertEquals(10, questions.map { it.country.code }.distinct().size)

            for (question in questions){
                Assert.assertEquals(mode, question.mode)
                Assert.assertEquals(4, question.options.size)
                Assert.assertEquals(
                    4,
                    question.options.map { it.id }.distinct().size,
                )
                Assert.assertEquals(
                    4,
                    question.options.map { it.text }.distinct().size,
                )
                Assert.assertEquals(
                    1,
                    question.options.count {
                        it.id == question.correctOptionId
                    },
                )

                val correctOption = question.options.single {
                    it.id == question.correctOptionId
                }

                val expectedText = when (mode) {
                    QuizMode.CAPITAL -> question.country.capitals.single()
                    QuizMode.FLAG,
                    QuizMode.COUNTRY -> question.country.name
                }

                Assert.assertEquals(expectedText, correctOption.text)
            }
        }
    }

    @Test
    fun `fewer than ten countries returns insufficient countries`() {
        val result = QuizRoundGenerator(Random(42))
            .generate(countries().take(9), QuizMode.FLAG)

        Assert.assertEquals(
            QuizGenerationResult.InsufficientCountries(
                available = 9,
                required = 10,
            ),
            result,
        )
    }

    @Test
    fun `duplicate countries cannot fill a round`() {
        val nineCountries = countries().take(9)

        val result = QuizRoundGenerator(Random(42))
            .generate(nineCountries + nineCountries, QuizMode.FLAG)

        Assert.assertEquals(
            QuizGenerationResult.InsufficientCountries(9, 10),
            result,
        )
    }

    @Test
    fun `flag mode excludes countries without flag images`() {
        val input = countries().take(10).mapIndexed { index, country ->
            if (index == 0) country.copy(flagImage = null) else country
        }

        val result = QuizRoundGenerator(Random(42))
            .generate(input, QuizMode.FLAG)

        Assert.assertEquals(
            QuizGenerationResult.InsufficientCountries(9, 10),
            result,
        )
    }

    @Test
    fun `capital mode excludes countries with multiple capitals`() {
        val input = countries().take(10).mapIndexed { index, country ->
            if (index == 0) {
                country.copy(capitals = listOf("Capital A", "Capital B"))
            } else {
                country
            }
        }

        val result = QuizRoundGenerator(Random(42))
            .generate(input, QuizMode.CAPITAL)

        Assert.assertEquals(
            QuizGenerationResult.InsufficientCountries(9, 10),
            result,
        )
    }

    @Test
    fun `countries sharing a capital are excluded`() {
        val input = countries().take(11).mapIndexed { index, country ->
            if (index < 2) {
                country.copy(capitals = listOf("Shared capital"))
            } else {
                country
            }
        }

        for (mode in listOf(QuizMode.CAPITAL, QuizMode.COUNTRY)) {
            val result = QuizRoundGenerator(Random(42))
                .generate(input, mode)

            Assert.assertEquals(
                QuizGenerationResult.InsufficientCountries(9, 10),
                result,
            )
        }
    }

    private fun countries(): List<Country> =
        (1..12).map { index ->
            Country(
                code = "TEST_$index",
                name = "Country $index",
                capitals = listOf("Capital $index"),
                region = "Test region",
                languages = listOf("Test language"),
                flagImage = "file:///android_asset/flags/test_$index.png",
            )
        }
}