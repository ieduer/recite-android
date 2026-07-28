package net.bdfz.recite.model

data class ReciteCorpus(
    val title: String,
    val pieces: List<Piece>,
)

data class Piece(
    val id: String,
    val title: String,
    val kind: String,
    val author: String,
    val dynasty: String,
    val paragraphs: List<Paragraph>,
    val words: List<WordNote>,
    val questions: List<Question>,
) {
    val body: String
        get() = paragraphs.joinToString("\n") { it.text }

    val categoryLabel: String
        get() = if (kind == "wen") "古文" else "诗词"
}

data class Paragraph(
    val text: String,
    val note: String = "",
)

data class WordNote(
    val label: String,
    val note: String,
)

data class Question(
    val prompt: String,
    val answer: String,
    val year: Int?,
)
