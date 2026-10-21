package io.github.ivanbabiichuk.countryquest.data.mapper

import io.github.ivanbabiichuk.countryquest.domain.model.Country
import java.util.Locale

/**
 * Verified source-data corrections.
 * Reviewed on 2026-10-21.
 */
internal fun Country.withKnownDataCorrections(): Country =
    when (code) {
        "KZ" -> {
            if (capital.equals("Nur-Sultan", ignoreCase = true)) {
                copy(capital = "Astana")
            } else {
                this
            }
        }

        "SZ" -> {
            if (name.equals("Swaziland", ignoreCase = true)) {
                copy(name = "Eswatini")
            } else {
                this
            }
        }

        "CW" -> {
            copy(
                languages = languages
                    .map { language ->
                        if (language.equals(
                                "(Eastern) Punjabi",
                                ignoreCase = true,
                            )
                        ) {
                            "Papiamentu"
                        } else {
                            language
                        }
                    }
                    .distinctBy { it.lowercase(Locale.ROOT) }
            )
        }

        "GQ" -> {
            if (capital.equals("Malabo", ignoreCase = true)) {
                copy(capital = "Ciudad de la Paz")
            } else {
                this
            }
        }

        else -> this
    }