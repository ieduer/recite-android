@file:OptIn(ExperimentalMaterial3Api::class)

package net.bdfz.recite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bdfz.recite.BuildConfig
import net.bdfz.recite.data.PieceProgressEntity
import net.bdfz.recite.model.Piece
import net.bdfz.recite.update.UpdateState

private data class Destination(
    val screen: AppScreen,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    Destination(AppScreen.LIBRARY, "篇目", Icons.AutoMirrored.Rounded.LibraryBooks),
    Destination(AppScreen.TODAY, "今日", Icons.Rounded.Today),
    Destination(AppScreen.PROGRESS, "進度", Icons.Rounded.Home),
    Destination(AppScreen.ACCOUNT, "我的", Icons.Rounded.AccountCircle),
)

@Composable
fun ReciteApp(
    viewModel: ReciteViewModel,
    windowSizeClass: WindowSizeClass,
) {
    val state by viewModel.uiState.collectAsState()
    val compact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val detailPiece = state.selectedPieceId?.let { id -> state.pieces.find { it.id == id } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (compact && detailPiece == null) {
                AppNavigationBar(state.screen, viewModel::navigate)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            if (!compact) {
                AppNavigationRail(state.screen, viewModel::navigate)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (compact && detailPiece != null) {
                    PieceDetail(
                        piece = detailPiece,
                        progress = state.progress[detailPiece.id] ?: PieceProgressEntity(detailPiece.id),
                        selectedStage = state.selectedStage,
                        notice = state.notice,
                        onBack = { viewModel.selectPiece(null) },
                        onSelectStage = viewModel::selectStage,
                        onReading = { viewModel.markReading(detailPiece, it) },
                        onSubmit = { stage, answer, revealed ->
                            viewModel.submitStage(detailPiece, stage, answer, revealed)
                        },
                    )
                } else {
                    MainContent(
                        state = state,
                        compact = compact,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainContent(
    state: ReciteUiState,
    compact: Boolean,
    viewModel: ReciteViewModel,
) {
    when (state.screen) {
        AppScreen.LIBRARY -> {
            if (compact) {
                LibraryPane(state, viewModel::setQuery, viewModel::setFilter, viewModel::selectPiece)
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 340.dp, max = 480.dp)
                            .fillMaxHeight(),
                    ) {
                        LibraryPane(state, viewModel::setQuery, viewModel::setFilter, viewModel::selectPiece)
                    }
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp),
                    )
                    val selected = state.selectedPieceId?.let { id -> state.pieces.find { it.id == id } }
                        ?: state.pieces.firstOrNull()
                    if (selected != null) {
                        PieceDetail(
                            piece = selected,
                            progress = state.progress[selected.id] ?: PieceProgressEntity(selected.id),
                            selectedStage = if (state.selectedPieceId == null) {
                                ((state.progress[selected.id]?.stage ?: 0) + 1).coerceIn(1, 5)
                            } else {
                                state.selectedStage
                            },
                            notice = state.notice,
                            onBack = null,
                            onSelectStage = viewModel::selectStage,
                            onReading = { viewModel.markReading(selected, it) },
                            onSubmit = { stage, answer, revealed ->
                                viewModel.submitStage(selected, stage, answer, revealed)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        AppScreen.TODAY -> TodayScreen(state) {
            viewModel.navigate(AppScreen.LIBRARY)
            viewModel.selectPiece(it)
        }
        AppScreen.PROGRESS -> ProgressScreen(state)
        AppScreen.ACCOUNT -> AccountScreen(state, viewModel)
    }
}

@Composable
private fun AppNavigationBar(selected: AppScreen, onSelect: (AppScreen) -> Unit) {
    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination.screen,
                onClick = { onSelect(destination.screen) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(selected: AppScreen, onSelect: (AppScreen) -> Unit) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        Spacer(Modifier.statusBarsPadding())
        Text(
            text = "琅",
            modifier = Modifier.padding(vertical = 18.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
        )
        destinations.forEach { destination ->
            NavigationRailItem(
                selected = selected == destination.screen,
                onClick = { onSelect(destination.screen) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun LibraryPane(
    state: ReciteUiState,
    onQuery: (String) -> Unit,
    onFilter: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val filtered = remember(state.pieces, state.query, state.filter) {
        state.pieces.filter { piece ->
            val matchesFilter = state.filter == "all" || piece.kind == state.filter
            val needle = state.query.trim()
            matchesFilter && (
                needle.isBlank() ||
                    piece.title.contains(needle, ignoreCase = true) ||
                    piece.author.contains(needle, ignoreCase = true) ||
                    piece.dynasty.contains(needle, ignoreCase = true)
                )
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeroCard(state)
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("搜尋篇名、作者或朝代") },
                shape = RoundedCornerShape(18.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "全部 78", "wen" to "古文 37", "shi" to "詩詞 41").forEach { (key, label) ->
                    FilterChip(
                        selected = state.filter == key,
                        onClick = { onFilter(key) },
                        label = { Text(label) },
                    )
                }
            }
        }
        items(filtered, key = { it.id }) { piece ->
            PieceCard(
                piece = piece,
                progress = state.progress[piece.id] ?: PieceProgressEntity(piece.id),
                selected = state.selectedPieceId == piece.id,
                onClick = { onOpen(piece.id) },
            )
        }
    }
}

@Composable
private fun HeroCard(state: ReciteUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "琅琅",
                color = MaterialTheme.colorScheme.onPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "高考古詩文 · 原生離線背誦",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                style = MaterialTheme.typography.titleMedium,
            )
            LinearProgressIndicator(
                progress = { state.overallPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${state.overallPercent}% 總進度",
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    "${state.completedCount} 完成 · ${state.inProgressCount} 進行中",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun PieceCard(
    piece: Piece,
    progress: PieceProgressEntity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (progress.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (progress.completed) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                } else {
                    Text(
                        text = piece.id.removePrefix("p"),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    piece.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(piece.dynasty, piece.author, piece.categoryLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(9.dp))
                LinearProgressIndicator(
                    progress = { progress.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "${progress.progressPercent}%",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PieceDetail(
    piece: Piece,
    progress: PieceProgressEntity,
    selectedStage: Int,
    notice: String,
    onBack: (() -> Unit)?,
    onSelectStage: (Int) -> Unit,
    onReading: (Int) -> Unit,
    onSubmit: (Int, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(piece.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${piece.dynasty} · ${piece.author}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LinearProgressIndicator(
            progress = { progress.progressPercent / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        StageChooser(
            progress = progress,
            selected = selectedStage,
            onSelect = onSelectStage,
        )
        if (notice.isNotBlank()) {
            NoticeBanner(notice)
        }
        StageContent(
            piece = piece,
            progress = progress,
            stage = selectedStage,
            onReading = onReading,
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun StageChooser(
    progress: PieceProgressEntity,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val labels = listOf("通讀", "填空", "理解", "默寫", "測驗")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(labels.size) { index ->
            val stage = index + 1
            val completed = progress.stage >= stage
            FilterChip(
                selected = selected == stage,
                onClick = { onSelect(stage) },
                enabled = stage <= progress.stage + 1 || completed,
                leadingIcon = if (completed) {
                    { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else if (stage > progress.stage + 1) {
                    { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
                label = { Text("$stage ${labels[index]}") },
            )
        }
    }
}

@Composable
private fun StageContent(
    piece: Piece,
    progress: PieceProgressEntity,
    stage: Int,
    onReading: (Int) -> Unit,
    onSubmit: (Int, String, Boolean) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (stage) {
            1 -> ReadingStage(piece, progress, onReading)
            2 -> ClozeStage(piece) { revealed -> onSubmit(2, "", revealed) }
            3 -> QuestionStage(piece, 3, onSubmit)
            4 -> DictationStage(piece, onSubmit)
            else -> QuestionStage(piece, 5, onSubmit)
        }
    }
}

@Composable
private fun ReadingStage(
    piece: Piece,
    progress: PieceProgressEntity,
    onReading: (Int) -> Unit,
) {
    Text(
        "先完整通讀，離線也可使用。原文、篇名與註記來自現有 78 篇語料。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    piece.paragraphs.forEachIndexed { index, paragraph ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                paragraph.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                    lineHeight = 31.sp,
                ),
            )
            if (paragraph.note.isNotBlank()) {
                Text(
                    paragraph.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (index < piece.paragraphs.lastIndex) HorizontalDivider()
        }
    }
    Button(
        onClick = { onReading(100) },
        modifier = Modifier.fillMaxWidth(),
        enabled = progress.stage < 1,
    ) {
        Icon(Icons.Rounded.AutoStories, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(if (progress.stage >= 1) "已完成通讀" else "完成通讀")
    }
}

@Composable
private fun ClozeStage(piece: Piece, onComplete: (Boolean) -> Unit) {
    var revealed by rememberSaveable(piece.id) { mutableStateOf(false) }
    val practiceText = piece.paragraphs.firstOrNull()?.text.orEmpty().ifBlank { piece.body.take(240) }
    Text("讀出被遮住的字句，再核對原文。")
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = if (revealed) practiceText else buildCloze(practiceText),
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                lineHeight = 31.sp,
            ),
        )
    }
    OutlinedButton(
        onClick = { revealed = !revealed },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (revealed) "重新遮擋" else "核對原文")
    }
    Button(
        onClick = { onComplete(revealed) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("完成填空")
    }
}

@Composable
private fun QuestionStage(
    piece: Piece,
    stage: Int,
    onSubmit: (Int, String, Boolean) -> Unit,
) {
    val question = piece.questions.firstOrNull()
    var answer by rememberSaveable(piece.id, stage) { mutableStateOf("") }
    val threshold = if (stage == 5) 80 else 60
    Text(if (stage == 5) "綜合測驗 · $threshold 分過關" else "高考真題語境 · $threshold 分過關")
    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                question?.year?.let { "$it 年" } ?: "理解題",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                question?.prompt ?: "請默寫本篇的核心句。",
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 27.sp,
            )
        }
    }
    OutlinedTextField(
        value = answer,
        onValueChange = { answer = it },
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        label = { Text("輸入答案") },
        placeholder = { Text("標點與空格不影響評分") },
    )
    Button(
        onClick = { onSubmit(stage, answer, false) },
        modifier = Modifier.fillMaxWidth(),
        enabled = answer.isNotBlank(),
    ) {
        Text(if (stage == 5) "提交測驗" else "提交理解題")
    }
}

@Composable
private fun DictationStage(
    piece: Piece,
    onSubmit: (Int, String, Boolean) -> Unit,
) {
    val paragraphIndex = dictationParagraphIndex(piece)
    var answer by rememberSaveable(piece.id, "dictation") { mutableStateOf("") }
    Text("不看原文，默寫指定段落；60 分過關。")
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(piece.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Text(
                "第 ${paragraphIndex + 1} 段 · 約 ${piece.paragraphs.getOrNull(paragraphIndex)?.text?.length ?: 0} 字",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    OutlinedTextField(
        value = answer,
        onValueChange = { answer = it },
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        label = { Text("默寫內容") },
    )
    Button(
        onClick = { onSubmit(4, answer, false) },
        modifier = Modifier.fillMaxWidth(),
        enabled = answer.isNotBlank(),
    ) {
        Text("提交默寫")
    }
}

@Composable
private fun NoticeBanner(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun TodayScreen(state: ReciteUiState, onOpen: (String) -> Unit) {
    val index = ((System.currentTimeMillis() / 86_400_000L) % state.pieces.size.coerceAtLeast(1)).toInt()
    val todayPiece = state.pieces.getOrNull(index)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeading("今日一篇", "每天固定一篇，先完成五階中的下一階。")
        if (todayPiece != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        todayPiece.title,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontFamily = FontFamily.Serif,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "${todayPiece.dynasty} · ${todayPiece.author}",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                    )
                    val progress = state.progress[todayPiece.id] ?: PieceProgressEntity(todayPiece.id)
                    Text(
                        "目前 ${progress.progressPercent}% · 下一階 ${((progress.stage + 1).coerceAtMost(5))}",
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Button(onClick = { onOpen(todayPiece.id) }) {
                        Text("開始今日背誦")
                    }
                }
            }
        }
        Text(
            "所有原文與本機進度均可離線使用；登入只影響跨裝置同步，不影響學習。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressScreen(state: ReciteUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeading("學習進度", "本機即時保存；登入後再與 User Center 合併。")
        MetricCard("總進度", "${state.overallPercent}%", "${state.completedCount} / ${state.pieces.size} 篇完成")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                MetricCard("進行中", "${state.inProgressCount}", "篇")
            }
            Box(Modifier.weight(1f)) {
                MetricCard("待同步", "${state.pendingSyncCount}", if (state.session == null) "登入後同步" else "有網路即同步")
            }
        }
        val stages = (1..5).map { stage ->
            stage to state.pieces.count { (state.progress[it.id]?.stage ?: 0) >= stage }
        }
        Text("五階覆蓋", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        stages.forEach { (stage, count) ->
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(listOf("通讀", "填空", "理解", "默寫", "測驗")[stage - 1])
                    Text("$count / ${state.pieces.size}")
                }
                LinearProgressIndicator(
                    progress = { count / state.pieces.size.coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AccountScreen(state: ReciteUiState, viewModel: ReciteViewModel) {
    val context = LocalContext.current
    var registering by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeading("我的帳號", "離線優先；帳號用於跨設備同步。")
        if (state.session != null) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.session.displayName, style = MaterialTheme.typography.titleLarge)
                        Text("@${state.session.slug}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "退出")
                    }
                }
            }
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        if (state.pendingSyncCount == 0) "雲端進度已排空" else "${state.pendingSyncCount} 項等待同步",
                    )
                },
                leadingIcon = {
                    Icon(
                        if (state.pendingSyncCount == 0) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                        contentDescription = null,
                    )
                },
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !registering,
                    onClick = { registering = false },
                    label = { Text("登入") },
                    leadingIcon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null) },
                )
                FilterChip(
                    selected = registering,
                    onClick = { registering = true },
                    label = { Text("用戶名註冊") },
                    leadingIcon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                )
            }
            if (registering) {
                Text(
                    "直接用戶名沿用 BDFZ 現有身份系統，需邀請碼；不另建孤立 App 帳號庫。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("顯示名稱") },
                    singleLine = true,
                )
            } else {
                Text(
                    "Seiue 用戶名或既有 BDFZ 用戶名均可在此登入。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.lowercase() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (registering) "用戶名" else "Seiue / BDFZ 用戶名") },
                singleLine = true,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密碼") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            if (registering) {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("邀請碼") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
            Button(
                onClick = {
                    if (registering) {
                        viewModel.register(username, displayName, password, inviteCode)
                    } else {
                        viewModel.login(username, password)
                    }
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (registering) "建立帳號" else "登入")
            }
        }
        if (state.notice.isNotBlank()) NoticeBanner(state.notice)
        UpdateCard(state, viewModel) { viewModel.installUpdate(it) }
        Text(
            "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${if (BuildConfig.SELF_UPDATE_ENABLED) "R2 直裝通道" else "Google Play 通道"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdateCard(
    state: ReciteUiState,
    viewModel: ReciteViewModel,
    onInstall: (java.io.File) -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Update, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("App 更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            when (val update = state.updateState) {
                UpdateState.Idle -> Text("尚未檢查。")
                UpdateState.Checking -> Text("正在檢查第一方更新清單…")
                UpdateState.Current -> Text(if (BuildConfig.SELF_UPDATE_ENABLED) "目前已是最新版。" else "Play 版由商店管理更新。")
                is UpdateState.Available -> {
                    Text("可更新至 ${update.info.version} (${update.info.versionCode})")
                    update.info.notes.take(3).forEach { Text("• $it") }
                    Button(onClick = { viewModel.downloadUpdate(update.info) }) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (update.info.required) "下載必要更新" else "下載更新")
                    }
                }
                is UpdateState.Downloading -> {
                    Text("正在下載 ${update.info.version} 並驗證 SHA-256…")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Ready -> {
                    Text("${update.info.version} 已校驗完成，安裝由 Android 系統確認。")
                    Button(onClick = { onInstall(update.apk) }) {
                        Text("安裝更新")
                    }
                }
                is UpdateState.Error -> Text(update.message, color = MaterialTheme.colorScheme.error)
            }
            if (state.updateState !is UpdateState.Checking && state.updateState !is UpdateState.Downloading) {
                OutlinedButton(onClick = { viewModel.checkForUpdate() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重新檢查")
                }
            }
        }
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String) {
    Column(
        modifier = Modifier.statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
