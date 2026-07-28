package net.bdfz.recite.data

import android.content.Context
import net.bdfz.recite.model.Paragraph
import net.bdfz.recite.model.Piece
import net.bdfz.recite.model.Question
import net.bdfz.recite.model.ReciteCorpus
import net.bdfz.recite.model.WordNote
import org.json.JSONObject

class CorpusRepository(private val context: Context) {
    val corpus: ReciteCorpus by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        parseCorpus(context.assets.open("corpus.json").bufferedReader().use { it.readText() })
    }

    val manifest: LearningManifest by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val root = JSONObject(
            context.assets.open("learning-manifest.json").bufferedReader().use { it.readText() },
        )
        LearningManifest(
            version = root.getString("manifestVersion"),
            resourceKeyHash = root.getString("resourceKeyHash"),
            itemCount = root.getInt("itemCount"),
            totalStages = root.getInt("totalStages"),
        )
    }

    private fun parseCorpus(raw: String): ReciteCorpus {
        val root = JSONObject(raw)
        val book = root.optJSONObject("book")
        val piecesJson = root.getJSONArray("pieces")
        val pieces = buildList(piecesJson.length()) {
            repeat(piecesJson.length()) { index ->
                val item = piecesJson.getJSONObject(index)
                val paragraphsJson = item.optJSONArray("paras")
                val wordsJson = item.optJSONArray("words")
                val questionsJson = item.optJSONArray("questions")
                add(
                    Piece(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        kind = item.optString("kind", "shi"),
                        author = item.optString("author"),
                        dynasty = item.optString("dynasty"),
                        paragraphs = buildList {
                            if (paragraphsJson != null) {
                                repeat(paragraphsJson.length()) { paragraphIndex ->
                                    val paragraph = paragraphsJson.getJSONObject(paragraphIndex)
                                    add(
                                        Paragraph(
                                            text = paragraph.getString("text"),
                                            note = paragraph.optString("note"),
                                        ),
                                    )
                                }
                            }
                        },
                        words = buildList {
                            if (wordsJson != null) {
                                repeat(wordsJson.length()) { wordIndex ->
                                    val word = wordsJson.getJSONObject(wordIndex)
                                    add(WordNote(word.optString("label"), word.optString("note")))
                                }
                            }
                        },
                        questions = buildList {
                            if (questionsJson != null) {
                                repeat(questionsJson.length()) { questionIndex ->
                                    val question = questionsJson.getJSONObject(questionIndex)
                                    add(
                                        Question(
                                            prompt = question.optString("q"),
                                            answer = question.optString("a"),
                                            year = question.optInt("year").takeIf { question.has("year") },
                                        ),
                                    )
                                }
                            }
                        },
                    ),
                )
            }
        }
        return ReciteCorpus(
            title = book?.optString("title").orEmpty().ifBlank { "琅琅 · 高考古诗文背诵" },
            pieces = pieces,
        )
    }
}

data class LearningManifest(
    val version: String,
    val resourceKeyHash: String,
    val itemCount: Int,
    val totalStages: Int,
)
