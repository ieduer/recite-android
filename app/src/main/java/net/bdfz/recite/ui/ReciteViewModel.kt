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
            withContext(Dispatchers.Main) {
                repository.mergeRemote(remote)
                _uiState.value = _uiState.value.copy(
                    session = session,
                    notice = "已登入並合併雲端進度。",
                )
            }
        }
    }

    fun register(
        username: String,
        displayName: String,
        password: String,
        inviteCode: String,
    ) {
        if (!Regex("""(?=.{2,30}$)[a-z0-9]+(?:-[a-z0-9]+)*""").matches(username)) {
            _uiState.value = _uiState.value.copy(notice = "用戶名需為 2–30 位小寫字母、數字或連字號。")
            return
        }
        if (password.length < 8 || inviteCode.isBlank()) {
            _uiState.value = _uiState.value.copy(notice = "密碼至少 8 位，並需填寫邀請碼。")
            return
        }
        runAccountAction {
            container.apiClient.registerUsername(username, displayName, password, inviteCode)
            val session = container.apiClient.login(username, password)
            container.sessionStore.write(session)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    session = session,
                    notice = "帳號已建立，離線進度會在有網路時同步。",
                )
            }
        }
    }

    fun logout() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { container.apiClient.logout(session) }
                container.sessionStore.clear()
            }
            _uiState.value = _uiState.value.copy(session = null, notice = "已退出帳號；本機進度仍保留。")
        }
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

    fun installUpdate(file: File) {
        updateManager.install(file)
    }

    fun onForeground() {
        checkForUpdate(automatic = true)
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
