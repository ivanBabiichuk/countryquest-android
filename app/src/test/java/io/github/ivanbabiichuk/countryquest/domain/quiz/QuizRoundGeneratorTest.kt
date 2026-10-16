package io.github.ivanbabiichuk.countryquest.domain.quiz

import io.github.ivanbabiichuk.countryquest.domain.model.Country
import io.github.ivanbabiichuk.countryquest.domain.model.CountryFlag
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
                    QuizMode.CAPITAL -> question.country.capital
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
            if (index == 0) country.copy(flag = CountryFlag(svgUrl = null, pngUrl = null)) else country
        }

        val result = QuizRoundGenerator(Random(42))
            .generate(input, QuizMode.FLAG)

        Assert.assertEquals(
            QuizGenerationResult.InsufficientCountries(9, 10),
            result,
        )
    }

    @Test
    fun `capital and country modes exclude missing or blank capitals`() {
        for (capital in listOf(null, "", "   ")) {
            val input = countries().take(10).mapIndexed { index, country ->
                if (index == 0) country.copy(capital = capital) else country
            }

            for (mode in listOf(QuizMode.CAPITAL, QuizMode.COUNTRY)) {
                val result = QuizRoundGenerator(Random(42)).generate(input, mode)

                Assert.assertEquals(
                    QuizGenerationResult.InsufficientCountries(9, 10),
                    result,
                )
            }
        }
    }

    @Test
    fun `flag mode does not require a capital`() {
        val input = countries().map { it.copy(capital = null) }

        val result = QuizRoundGenerator(Random(42)).generate(input, QuizMode.FLAG)

        Assert.assertTrue(result is QuizGenerationResult.Success)
        Assert.assertEquals(10, (result as QuizGenerationResult.Success).questions.size)
    }

    @Test
    fun `countries sharing a capital are excluded`() {
        val input = countries().take(11).mapIndexed { index, country ->
            if (index < 2) {
                country.copy(capital = "Shared capital")
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

    @Test
    fun `flag mode accepts SVG without PNG`() {
        val input = countries().map { country ->
            country.copy(flag = CountryFlag(
                svgUrl = "https://example.com/flags/${country.code}.svg",
                pngUrl = null,
            ))
        }

        val result = QuizRoundGenerator(Random(42)).generate(input, QuizMode.FLAG)

        Assert.assertTrue(result is QuizGenerationResult.Success)
        Assert.assertEquals(10, (result as QuizGenerationResult.Success).questions.size)
        Assert.assertTrue(result.questions.all { it.country.flag.pngUrl == null })
    }

    @Test
    fun `flag mode accepts PNG without SVG`() {
        val result = QuizRoundGenerator(Random(42)).generate(countries(), QuizMode.FLAG)

        Assert.assertTrue(result is QuizGenerationResult.Success)
        Assert.assertEquals(10, (result as QuizGenerationResult.Success).questions.size)
        Assert.assertTrue(result.questions.all { it.country.flag.svgUrl == null })
    }

    @Test
    fun `flag mode excludes countries when both formats are missing or blank`() {
        val missingFlags = listOf(
            CountryFlag(null, null),
            CountryFlag("", null),
            CountryFlag(null, " "),
            CountryFlag(" ", ""),
        )
        for (flag in missingFlags) {
            val input = countries().take(10).mapIndexed { index, country ->
                if (index == 0) country.copy(flag = flag) else country
            }

            val result = QuizRoundGenerator(Random(42)).generate(input, QuizMode.FLAG)

            Assert.assertEquals(QuizGenerationResult.InsufficientCountries(9, 10), result)
        }
    }

    private fun countries(): List<Country> =
        (1..12).map { index ->
            Country(
                code = "TEST_$index",
                name = "Country $index",
                capital = "Capital $index",
                region = "Test region",
                languages = listOf("Test language"),
                flag = CountryFlag(
                    svgUrl = null,
                    pngUrl = "https://example.com/flags/test_$index.png",
                ),
            )
        }
}