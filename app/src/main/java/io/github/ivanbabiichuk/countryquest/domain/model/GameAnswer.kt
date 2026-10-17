package io.github.ivanbabiichuk.countryquest.domain.model

data class GameAnswer(
    val countryCode: String,
    val countryName: String,
    val selectedOptionId: String,
    val selectedOptionText: String,
    val correctOptionId: String,
    val correctOptionText: String
) {
    init {
        require(countryCode.isNotBlank())
        require(countryName.isNotBlank())
        require(selectedOptionId.isNotBlank())
        require(selectedOptionText.isNotBlank())
        require(correctOptionId.isNotBlank())
        require(correctOptionText.isNotBlank())
        require(correctOptionId == countryCode)
    }

    val isCorrect: Boolean
        get() = selectedOptionId == correctOptionId
}