package net.bdfz.recite.ui

import net.bdfz.recite.data.PieceProgressEntity
import net.bdfz.recite.model.Paragraph
import net.bdfz.recite.model.Piece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeScoringTest {
    @Test
    fun exactAnswerIgnoresWhitespaceAndPunctuation() {
        assertEquals(100, textScore("己所不欲，勿施于人。", "己所不欲 勿施于人"))
    }

    @Test
    fun partialAnswerProducesBoundedScore() {
        val score = textScore("锲而不舍，金石可镂", "锲而不舍")
        assertTrue(score in 40..70)
    }

    @Test
    fun clozeMasksHanCharactersButPreservesPunctuation() {
        val result = buildCloze("君子曰：学不可以已。")
        assertTrue(result.contains('＿'))
        assertTrue(result.contains('：'))
        assertTrue(result.contains('。'))
    }

    @Test
    fun completedProgressIsOneHundredPercent() {
        assertEquals(100, PieceProgressEntity(pieceId = "p1", stage = 5).progressPercent)
    }

    @Test
    fun dictationSelectionStaysInsidePiece() {
        val piece = Piece(
            id = "p2",
            title = "劝学",
            kind = "wen",
            author = "荀子",
            dynasty = "战国",
            paragraphs = listOf(Paragraph("一"), Paragraph("二"), Paragraph("三")),
            words = emptyList(),
            questions = emptyList(),
        )
        assertTrue(dictationParagraphIndex(piece) in piece.paragraphs.indices)
    }
}
