package io.github.ivanbabiichuk.countryquest.domain.model

data class GameResult(
    val id: String,
    val mode: QuizMode,
    val completedAtEpochMillis: Long,
    val answers: List<GameAnswer>,
) {
    init {
        require(id.isNotBlank())
        require(completedAtEpochMillis >= 0)
        require(answers.isNotEmpty())
        require(
            answers.map { it.countryCode }.distinct().size == answers.size
        ) {
            "A completed round must not contain repeated countries."
        }
    }

    val score: Int
        get() = answers.count { it.isCorrect }

    val totalQuestions: Int
        get() = answers.size
}