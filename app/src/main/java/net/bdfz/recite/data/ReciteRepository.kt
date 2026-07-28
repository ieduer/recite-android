package net.bdfz.recite.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import net.bdfz.recite.model.Piece
import net.bdfz.recite.security.AppSession
import net.bdfz.recite.sync.ProgressSyncWorker
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class ReciteRepository(
    private val context: Context,
    private val database: ReciteDatabase,
    private val corpusRepository: CorpusRepository,
) {
    private val dao = database.progressDao()

    val pieces: List<Piece>
        get() = corpusRepository.corpus.pieces

    val progress: Flow<List<PieceProgressEntity>> = dao.observeAll()
    val pendingCount: Flow<Int> = dao.observePendingCount()

    suspend fun markReading(piece: Piece, readPercent: Int) {
        mutate(piece, reason = "reading") { current, now ->
            current.copy(
                stage = maxOf(current.stage, if (readPercent >= 100) 1 else 0),
                readPercent = maxOf(current.readPercent, readPercent.coerceIn(0, 100)),
                firstStartedAt = current.firstStartedAt.ifBlank { now },
                lastActivityAt = now,
            )
        }
    }

    suspend fun completeStage(piece: Piece, stage: Int, score: Int = 100, peeked: Boolean = false) {
        require(stage in 1..5)
        mutate(piece, reason = "stage_$stage") { current, now ->
            when (stage) {
                1 -> current.copy(
                    stage = maxOf(current.stage, 1),
                    readPercent = 100,
                    firstStartedAt = current.firstStartedAt.ifBlank { now },
                    lastActivityAt = now,
                )
                2 -> current.copy(
                    stage = maxOf(current.stage, 2),
                    peeks = current.peeks + if (peeked) 1 else 0,
                    clozeAttempts = current.clozeAttempts + 1,
                    firstStartedAt = current.firstStartedAt.ifBlank { now },
                    lastActivityAt = now,
                )
                3 -> current.copy(
                    stage = maxOf(current.stage, if (score >= 60) 3 else current.stage),
                    quizBest = maxOf(current.quizBest, score),
                    quizAttempts = current.quizAttempts + 1,
                    firstStartedAt = current.firstStartedAt.ifBlank { now },
                    lastActivityAt = now,
                )
                4 -> current.copy(
                    stage = maxOf(current.stage, if (score >= 60) 4 else current.stage),
                    dictBest = maxOf(current.dictBest, score),
                    dictationAttempts = current.dictationAttempts + 1,
                    firstStartedAt = current.firstStartedAt.ifBlank { now },
                    lastActivityAt = now,
                )
                else -> current.copy(
                    stage = maxOf(current.stage, if (score >= 80) 5 else current.stage),
                    examBest = maxOf(current.examBest, score),
                    examAttempts = current.examAttempts + 1,
                    firstStartedAt = current.firstStartedAt.ifBlank { now },
                    lastActivityAt = now,
                    lastReviewedAt = if (score >= 80) now else current.lastReviewedAt,
                )
            }
        }
    }

    suspend fun mergeRemote(remote: List<PieceProgressEntity>) {
        database.withTransaction {
            remote.forEach { incoming ->
                val local = dao.get(incoming.pieceId) ?: PieceProgressEntity(incoming.pieceId)
                dao.upsert(merge(local, incoming))
            }
        }
    }

    private suspend fun mutate(
        piece: Piece,
        reason: String,
        block: (PieceProgressEntity, String) -> PieceProgressEntity,
    ) {
        val now = isoNow()
        database.withTransaction {
            val current = dao.get(piece.id) ?: PieceProgressEntity(piece.id)
            val next = block(current, now)
            dao.upsert(next)
            val payload = buildProgressPayload(piece, next, reason)
            dao.enqueue(
                SyncOutboxEntity(
                    eventId = JSONObject(payload).getJSONObject("meta").getString("clientMutationId"),
                    pieceId = piece.id,
                    payloadJson = payload,
                    createdAtMs = System.currentTimeMillis(),
                ),
            )
        }
        scheduleSync()
    }

    private fun buildProgressPayload(
        piece: Piece,
        progress: PieceProgressEntity,
        reason: String,
    ): String {
        val manifest = corpusRepository.manifest
        val eventId = "recite-android-${piece.id}-${progress.lastActivityAt.hashCode().toUInt().toString(36)}"
        val score = maxOf(
            progress.progressPercent,
            progress.quizBest,
            progress.dictBest,
            progress.voiceBest,
            progress.examBest,
        )
        val meta = JSONObject()
            .put("schemaVersion", "recite-progress-v2")
            .put("source", "recite-android")
            .put("manifestVersion", manifest.version)
            .put("resourceKeyHash", manifest.resourceKeyHash)
            .put("resourceKey", piece.id)
            .put("completionKind", if (progress.completed) "five_stages_sealed" else "progress_checkpoint")
            .put("stage", progress.stage)
            .put("totalStages", manifest.totalStages)
            .put("readPercent", progress.readPercent)
            .put("peeks", progress.peeks)
            .put("quizBest", progress.quizBest)
            .put("dictBest", progress.dictBest)
            .put("voiceBest", progress.voiceBest)
            .put("examBest", progress.examBest)
            .put("cloze1Attempts", progress.clozeAttempts)
            .put("cloze2Attempts", 0)
            .put("cloze3Attempts", 0)
            .put("quizAttempts", progress.quizAttempts)
            .put("voiceAttempts", 0)
            .put("dictationAttempts", progress.dictationAttempts)
            .put("examAttempts", progress.examAttempts)
            .put("imageGradeAttempts", 0)
            .put("sealed", progress.completed)
            .put("firstStartedAt", progress.firstStartedAt)
            .put("lastActivityAt", progress.lastActivityAt)
            .put("lastReviewedAt", progress.lastReviewedAt)
            .put("clientUpdatedAt", progress.lastActivityAt)
            .put("clientMutationId", eventId)
            .put("reason", reason)

        return JSONObject()
            .put("siteKey", "recite")
            .put("itemKey", piece.id)
            .put("itemTitle", piece.title)
            .put("itemGroup", "gaokao-recitation")
            .put("itemType", "recitation")
            .put("state", if (progress.completed) "completed" else "in_progress")
            .put("progressPercent", progress.progressPercent)
            .put("score", score)
            .put("meta", meta)
            .toString()
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ProgressSyncWorker.UNIQUE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private fun merge(left: PieceProgressEntity, right: PieceProgressEntity): PieceProgressEntity {
        fun earliest(a: String, b: String) = listOf(a, b).filter { it.isNotBlank() }.minOrNull().orEmpty()
        fun latest(a: String, b: String) = maxOf(a, b)
        return left.copy(
            stage = maxOf(left.stage, right.stage),
            readPercent = maxOf(left.readPercent, right.readPercent),
            peeks = maxOf(left.peeks, right.peeks),
            quizBest = maxOf(left.quizBest, right.quizBest),
            dictBest = maxOf(left.dictBest, right.dictBest),
            voiceBest = maxOf(left.voiceBest, right.voiceBest),
            examBest = maxOf(left.examBest, right.examBest),
            clozeAttempts = maxOf(left.clozeAttempts, right.clozeAttempts),
            quizAttempts = maxOf(left.quizAttempts, right.quizAttempts),
            dictationAttempts = maxOf(left.dictationAttempts, right.dictationAttempts),
            examAttempts = maxOf(left.examAttempts, right.examAttempts),
            firstStartedAt = earliest(left.firstStartedAt, right.firstStartedAt),
            lastActivityAt = latest(left.lastActivityAt, right.lastActivityAt),
            lastReviewedAt = latest(left.lastReviewedAt, right.lastReviewedAt),
        )
    }

    private fun isoNow(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).run {
            timeZone = TimeZone.getTimeZone("UTC")
            format(Date())
        }
    }
}
