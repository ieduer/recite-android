package net.bdfz.recite.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bdfz.recite.LangLangApplication
import net.bdfz.recite.data.PieceProgressEntity
import net.bdfz.recite.model.Piece
import net.bdfz.recite.network.ApiException
import net.bdfz.recite.ranking.LeaderboardScope
import net.bdfz.recite.ranking.LeaderboardSnapshot
import net.bdfz.recite.ranking.RankStatus
import net.bdfz.recite.ranking.ReciteRanks
import net.bdfz.recite.security.AppSession
import net.bdfz.recite.update.AppUpdateManager
import net.bdfz.recite.update.UpdateInfo
import net.bdfz.recite.update.UpdateState
import java.io.File
import kotlin.math.roundToInt

enum class AppScreen {
    LIBRARY,
    TODAY,
    PROGRESS,
    LEADERBOARD,
    ACCOUNT,
}

data class ReciteUiState(
    val pieces: List<Piece> = emptyList(),
    val progress: Map<String, PieceProgressEntity> = emptyMap(),
    val pendingSyncCount: Int = 0,
    val screen: AppScreen = AppScreen.LIBRARY,
    val selectedPieceId: String? = null,
    val selectedStage: Int = 1,
    val query: String = "",
    val filter: String = "all",
    val session: AppSession? = null,
    val busy: Boolean = false,
    val notice: String = "",
    val feedbackBusy: Boolean = false,
    val feedbackNotice: String = "",
    val feedbackReceiptId: String = "",
    val leaderboard: LeaderboardSnapshot = LeaderboardSnapshot(),
    val leaderboardScope: LeaderboardScope = LeaderboardScope.DAILY,
    val leaderboardBusy: Boolean = false,
    val leaderboardNotice: String = "",
    val updateState: UpdateState = UpdateState.Idle,
) {
    val completedCount: Int
        get() = progress.values.count { it.completed }

    val inProgressCount: Int
        get() = progress.values.count { it.stage in 1..4 }

    val overallPercent: Int
        get() = if (pieces.isEmpty()) 0 else {
            pieces.sumOf { progress[it.id]?.progressPercent ?: 0 } / pieces.size
        }

    val rankPoints: Int
        get() = pieces.sumOf { progress[it.id]?.stage?.coerceIn(0, 5) ?: 0 }
            .coerceAtMost(ReciteRanks.MAX_POINTS)

    val rankStatus: RankStatus
        get() = ReciteRanks.status(rankPoints)
}

class ReciteViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LangLangApplication).container
    private val repository = container.reciteRepository
    private val updateManager = AppUpdateManager(application, container.apiClient)
    private val _uiState = MutableStateFlow(
        ReciteUiState(
            pieces = repository.pieces,
            session = container.sessionStore.read(),
        ),
    )
    val uiState: StateFlow<ReciteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.progress.collect { items ->
                _uiState.value = _uiState.value.copy(progress = items.associateBy { it.pieceId })
            }
        }
        viewModelScope.launch {
            repository.pendingCount.collect { count ->
                _uiState.value = _uiState.value.copy(pendingSyncCount = count)
            }
        }
        _uiState.value.session?.let { refreshRemoteProgress(it) }
        checkForUpdate(automatic = true)
    }

    fun navigate(screen: AppScreen) {
        _uiState.value = _uiState.value.copy(screen = screen, selectedPieceId = null, notice = "")
        if (screen == AppScreen.LEADERBOARD) {
            refreshLeaderboard(syncCurrentUser = _uiState.value.session != null)
        }
    }

    fun selectPiece(pieceId: String?) {
        val progress = pieceId?.let { _uiState.value.progress[it] }
        _uiState.value = _uiState.value.copy(
            selectedPieceId = pieceId,
            selectedStage = ((progress?.stage ?: 0) + 1).coerceIn(1, 5),
            notice = "",
        )
    }

    fun selectStage(stage: Int) {
        _uiState.value = _uiState.value.copy(selectedStage = stage.coerceIn(1, 5), notice = "")
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setFilter(filter: String) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun markReading(piece: Piece, percent: Int) {
        viewModelScope.launch {
            repository.markReading(piece, percent)
            if (percent >= 100) {
                _uiState.value = _uiState.value.copy(
                    selectedStage = 2,
                    notice = "通讀完成，進入填空練習。",
                )
            }
        }
    }

    fun submitStage(piece: Piece, stage: Int, answer: String, revealed: Boolean = false) {
        viewModelScope.launch {
            val expected = when (stage) {
                3, 5 -> piece.questions.firstOrNull()?.answer.orEmpty().ifBlank { piece.body.take(120) }
                4 -> piece.paragraphs.getOrNull(dictationParagraphIndex(piece))?.text.orEmpty()
                else -> piece.body
            }
            val score = when (stage) {
                1, 2 -> 100
                else -> textScore(expected, answer)
            }
            repository.completeStage(piece, stage, score, revealed)
            val passed = stage <= 2 || score >= if (stage == 5) 80 else 60
            _uiState.value = _uiState.value.copy(
                selectedStage = if (passed) (stage + 1).coerceAtMost(5) else stage,
                notice = when {
                    stage == 5 && passed -> "五階完成，這一篇已封印。"
                    passed -> "本階段完成，得分 $score。"
                    else -> "本次得分 $score，達到 ${if (stage == 5) 80 else 60} 分即可過關。"
                },
            )
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(notice = "請輸入用戶名和密碼。")
            return
        }
        runAccountAction {
            val session = container.apiClient.login(username, password)
            container.sessionStore.write(session)
            val remote = container.apiClient.pullProgress(session)
            repository.mergeRemote(remote)
            repository.requestSync()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    session = session,
                    notice = "登入成功。",
                )
            }
            refreshLeaderboard(syncCurrentUser = true)
        }
    }

    fun logout() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { container.apiClient.logout(session) }
                container.sessionStore.clear()
            }
            _uiState.value = _uiState.value.copy(session = null, notice = "已退出帳號。")
            if (_uiState.value.screen == AppScreen.LEADERBOARD) {
                refreshLeaderboard(syncCurrentUser = false)
            }
        }
    }

    fun setLeaderboardScope(scope: LeaderboardScope) {
        _uiState.value = _uiState.value.copy(leaderboardScope = scope)
    }

    fun refreshLeaderboard(syncCurrentUser: Boolean = true) {
        if (_uiState.value.leaderboardBusy) return
        val session = _uiState.value.session
        _uiState.value = _uiState.value.copy(
            leaderboardBusy = true,
            leaderboardNotice = "",
        )
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    container.apiClient.loadLeaderboard(
                        session = session,
                        syncCurrentUser = syncCurrentUser && session != null,
                    )
                }
                _uiState.value = _uiState.value.copy(
                    leaderboard = snapshot,
                    leaderboardNotice = "",
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    leaderboardNotice = "榜單暫時不可用。",
                )
            } finally {
                _uiState.value = _uiState.value.copy(leaderboardBusy = false)
            }
        }
    }

    fun submitFeedback(category: String, title: String, description: String) {
        if (title.isBlank() || description.isBlank()) {
            _uiState.value = _uiState.value.copy(feedbackNotice = "請填寫主題和反饋內容。")
            return
        }
        if (_uiState.value.feedbackBusy) return
        _uiState.value = _uiState.value.copy(
            feedbackBusy = true,
            feedbackNotice = "",
            feedbackReceiptId = "",
        )
        viewModelScope.launch {
            try {
                val receipt = withContext(Dispatchers.IO) {
                    container.apiClient.submitFeedback(
                        session = _uiState.value.session,
                        category = category,
                        title = title,
                        description = description,
                    )
                }
                _uiState.value = _uiState.value.copy(
                    feedbackNotice = if (receipt.notificationSent) {
                        "反饋已送出，謝謝你。"
                    } else {
                        "反饋已保存，通知暫未送達。"
                    },
                    feedbackReceiptId = receipt.feedbackId,
                )
            } catch (error: ApiException) {
                _uiState.value = _uiState.value.copy(feedbackNotice = error.message)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    feedbackNotice = error.message ?: "反饋送出失敗。",
                )
            } finally {
                _uiState.value = _uiState.value.copy(feedbackBusy = false)
            }
        }
    }

    fun clearFeedbackNotice() {
        _uiState.value = _uiState.value.copy(
            feedbackNotice = "",
            feedbackReceiptId = "",
        )
    }

    fun checkForUpdate(automatic: Boolean = false) {
        if (automatic && !updateManager.shouldCheckAutomatically()) return
        if (_uiState.value.updateState is UpdateState.Checking) return
        _uiState.value = _uiState.value.copy(updateState = UpdateState.Checking)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { updateManager.check() }
            _uiState.value = _uiState.value.copy(updateState = result)
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        _uiState.value = _uiState.value.copy(updateState = UpdateState.Downloading(info))
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { updateManager.download(info) }
            _uiState.value = _uiState.value.copy(updateState = result)
        }
    }

    fun installUpdate(info: UpdateInfo, file: File) {
        _uiState.value = _uiState.value.copy(updateState = updateManager.install(info, file))
    }

    fun onForeground() {
        checkForUpdate(automatic = true)
        if (_uiState.value.screen == AppScreen.LEADERBOARD) {
            refreshLeaderboard(syncCurrentUser = _uiState.value.session != null)
        }
    }

    private fun refreshRemoteProgress(session: AppSession) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { container.apiClient.pullProgress(session) }
            }.onSuccess { repository.mergeRemote(it) }
        }
    }

    private fun runAccountAction(block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true, notice = "")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (error: ApiException) {
                _uiState.value = _uiState.value.copy(notice = error.message)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(notice = error.message ?: "操作失敗。")
            } finally {
                _uiState.value = _uiState.value.copy(busy = false)
            }
        }
    }
}

fun dictationParagraphIndex(piece: Piece): Int {
    if (piece.paragraphs.isEmpty()) return 0
    return piece.id.hashCode().absoluteValueSafe() % piece.paragraphs.size
}

fun buildCloze(text: String): String {
    var hanIndex = 0
    return buildString {
        text.forEach { character ->
            val isHan = character.code in 0x3400..0x9FFF
            if (isHan) {
                hanIndex += 1
                append(if ((hanIndex / 5) % 3 == 1) '＿' else character)
            } else {
                append(character)
            }
        }
    }
}

fun textScore(expected: String, actual: String): Int {
    val left = normalizeAnswer(expected)
    val right = normalizeAnswer(actual)
    if (left.isEmpty()) return 0
    if (left == right) return 100
    val previous = IntArray(right.length + 1)
    val current = IntArray(right.length + 1)
    left.forEachIndexed { leftIndex, leftCharacter ->
        current[0] = 0
        right.forEachIndexed { rightIndex, rightCharacter ->
            current[rightIndex + 1] = if (leftCharacter == rightCharacter) {
                previous[rightIndex] + 1
            } else {
                maxOf(previous[rightIndex + 1], current[rightIndex])
            }
        }
        current.copyInto(previous)
    }
    return (previous[right.length] * 100.0 / left.length).roundToInt().coerceIn(0, 100)
}

private fun normalizeAnswer(value: String): String {
    return value.lowercase().filter { it.isLetterOrDigit() }
}

private fun Int.absoluteValueSafe(): Int = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)
