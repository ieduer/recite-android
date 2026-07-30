@file:OptIn(ExperimentalMaterial3Api::class)

package net.bdfz.recite.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bdfz.recite.BuildConfig
import net.bdfz.recite.R
import net.bdfz.recite.data.PieceProgressEntity
import net.bdfz.recite.model.Piece
import net.bdfz.recite.ranking.AvatarFrameKind
import net.bdfz.recite.ranking.LeaderboardEntry
import net.bdfz.recite.ranking.LeaderboardScope
import net.bdfz.recite.ranking.RankStatus
import net.bdfz.recite.ranking.ReciteRank
import net.bdfz.recite.update.UpdateInfo
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
    Destination(AppScreen.LEADERBOARD, "榜單", Icons.Rounded.Leaderboard),
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

    BackHandler(enabled = compact && detailPiece != null) {
        viewModel.selectPiece(null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (compact && detailPiece == null) {
                    AppNavigationBar(state.screen, viewModel::navigate)
                }
            },
            containerColor = Color.Transparent,
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
        AppScreen.LEADERBOARD -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LeaderboardScreen(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 820.dp),
                )
            }
        }
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
                val filters = listOf(
                    "all" to "全部 ${state.pieces.size}",
                    "wen" to "古文 ${state.pieces.count { it.kind == "wen" }}",
                    "shi" to "詩詞 ${state.pieces.count { it.kind == "shi" }}",
                )
                filters.forEach { (key, label) ->
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "琅琅",
                color = MaterialTheme.colorScheme.onPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "高考古詩文 · ${state.pieces.size} 篇",
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
                    maxLines = 1,
                )
                Text(
                    "${state.completedCount} 完成 · ${state.inProgressCount} 進行中",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    maxLines = 2,
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
                    Text(
                        piece.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (piece.title.length > 14) 18.sp else 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
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
    val stages = listOf(
        "通讀" to "熟悉原文、注釋與語氣",
        "填空" to "回想被遮住的關鍵字句",
        "理解" to "結合題目辨析文意",
        "默寫" to "脫離原文完成整段書寫",
        "測驗" to "綜合檢查，完成本篇",
    )
    val stageIndex = (selected - 1).coerceIn(stages.indices)
    val stage = stageIndex + 1
    val (label, description) = stages[stageIndex]
    val completed = progress.stage >= stage
    val availableStage = (progress.stage + 1).coerceAtMost(stages.size)
    var showStageSheet by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (completed) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "第 $stage / ${stages.size} 階",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = if (completed) Icons.Rounded.CheckCircle else Icons.Rounded.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            if (completed) "已完成" else "正在學習",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progress.stage / stages.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showStageSheet = true }) {
                    Icon(
                        Icons.Rounded.FormatListNumbered,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("查看五階路徑")
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { onSelect(stage - 1) },
                    enabled = stage > 1,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一階")
                }
                IconButton(
                    onClick = { onSelect(stage + 1) },
                    enabled = stage < availableStage,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一階")
                }
            }
        }
    }

    if (showStageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStageSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "五階學習路徑",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                stages.forEachIndexed { index, item ->
                    val itemStage = index + 1
                    val itemCompleted = progress.stage >= itemStage
                    val itemEnabled = itemStage <= availableStage
                    val itemSelected = itemStage == stage
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = itemEnabled) {
                                onSelect(itemStage)
                                showStageSheet = false
                            },
                        color = if (itemSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                color = when {
                                    itemCompleted -> MaterialTheme.colorScheme.primary
                                    itemSelected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = when {
                                    itemCompleted -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                shape = CircleShape,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (itemCompleted) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    } else {
                                        Text(
                                            "$itemStage",
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.first,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    item.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!itemEnabled) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = "尚未解鎖",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
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
    piece.paragraphs.forEachIndexed { index, paragraph ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                paragraph.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    lineHeight = 34.sp,
                ),
            )
            if (paragraph.note.isNotBlank()) {
                Text(
                    paragraph.note,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
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
                fontSize = 20.sp,
                lineHeight = 34.sp,
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
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (todayPiece != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        todayPiece.title.replace("·", "\n"),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontFamily = FontFamily.Serif,
                        fontSize = when {
                            todayPiece.title.length > 18 -> 21.sp
                            todayPiece.title.contains("·") -> 26.sp
                            todayPiece.title.length > 12 -> 22.sp
                            else -> 28.sp
                        },
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
                    LinearProgressIndicator(
                        progress = { progress.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                    )
                    Button(
                        onClick = { onOpen(todayPiece.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("開始今日背誦")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressScreen(state: ReciteUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        RankHeroCard(state.rankStatus)
        MetricCard("總進度", "${state.overallPercent}%", "${state.completedCount} / ${state.pieces.size} 篇完成")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) {
                MetricCard("進行中", "${state.inProgressCount}", "篇")
            }
            Box(Modifier.weight(1f)) {
                MetricCard("待同步", "${state.pendingSyncCount}", "項")
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
private fun RankHeroCard(
    status: RankStatus,
    modifier: Modifier = Modifier,
) {
    val foreground = Color.White
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier
                .background(rankBrush(status.rank.frameKind))
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            RankAvatar(
                rank = status.rank,
                size = 76.dp,
                frameWidth = 4.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    "段位 ${status.rank.index} / 8",
                    color = foreground.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    status.rank.name,
                    color = foreground,
                    fontFamily = FontFamily.Serif,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    status.rank.motto,
                    color = foreground.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { status.progressToNext / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp),
                    color = foreground,
                    trackColor = foreground.copy(alpha = 0.2f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${status.points} 段位值",
                        color = foreground,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        status.nextRank?.let { "距${it.name}還差 ${status.pointsToNext}" } ?: "最高段位",
                        color = foreground.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardScreen(
    state: ReciteUiState,
    viewModel: ReciteViewModel,
    modifier: Modifier = Modifier,
) {
    val scope = state.leaderboardScope
    val entries = if (scope == LeaderboardScope.DAILY) {
        state.leaderboard.daily
    } else {
        state.leaderboard.total
    }
    val me = if (scope == LeaderboardScope.DAILY) {
        state.leaderboard.meDaily
    } else {
        state.leaderboard.meTotal
    }
    val meOutsidePage = me?.takeIf { entry ->
        entries.none { it.isMe || it.position == entry.position && it.displayName == entry.displayName }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RankHeroCard(state.rankStatus)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    listOf(
                        LeaderboardScope.DAILY to "今日",
                        LeaderboardScope.TOTAL to "總榜",
                    ).forEachIndexed { index, (itemScope, label) ->
                        SegmentedButton(
                            selected = scope == itemScope,
                            onClick = { viewModel.setLeaderboardScope(itemScope) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            icon = {
                                Icon(
                                    if (itemScope == LeaderboardScope.DAILY) {
                                        Icons.Rounded.Bolt
                                    } else {
                                        Icons.Rounded.EmojiEvents
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = { Text(label) },
                        )
                    }
                }
                IconButton(
                    onClick = {
                        viewModel.refreshLeaderboard(syncCurrentUser = state.session != null)
                    },
                    enabled = !state.leaderboardBusy,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新榜單")
                }
            }
        }
        if (state.leaderboardBusy) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.leaderboardNotice.isNotBlank()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        state.leaderboardNotice,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        if (!state.leaderboardBusy && entries.isEmpty()) {
            item {
                EmptyLeaderboard(scope)
            }
        }
        items(
            items = entries,
            key = { "${scope.name}-${it.position}-${it.displayName}" },
        ) { entry ->
            LeaderboardEntryCard(entry, scope)
        }
        if (meOutsidePage != null) {
            item {
                Text(
                    "我的名次",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                LeaderboardEntryCard(meOutsidePage, scope)
            }
        }
    }
}

@Composable
private fun EmptyLeaderboard(scope: LeaderboardScope) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
            ) {
                Icon(
                    Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(30.dp),
                )
            }
            Text(
                if (scope == LeaderboardScope.DAILY) "今日榜等待第一筆進步" else "總榜正在集結",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LeaderboardEntryCard(
    entry: LeaderboardEntry,
    scope: LeaderboardScope,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isMe) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            },
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PositionBadge(entry.position)
            RankAvatar(entry.rank, size = 48.dp, frameWidth = 3.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (entry.isMe) FontWeight.Black else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.rank.name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (scope == LeaderboardScope.DAILY) {
                        "+${entry.todayPoints}"
                    } else {
                        "${entry.totalPoints}"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = if (entry.isMe) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    "段位值",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PositionBadge(position: Int) {
    val background = when (position) {
        1 -> Color(0xFFD5A93F)
        2 -> Color(0xFF8D99A8)
        3 -> Color(0xFFAF7148)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (position <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.size(36.dp),
        color = background,
        contentColor = foreground,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "$position",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun RankAvatar(
    rank: ReciteRank,
    size: androidx.compose.ui.unit.Dp,
    frameWidth: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(rankFrameBrush(rank.frameKind), CircleShape)
            .padding(frameWidth),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            shape = CircleShape,
        ) {
            Image(
                painter = painterResource(R.drawable.bdfz_icon),
                contentDescription = "${rank.name}頭像框",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun rankBrush(kind: AvatarFrameKind): Brush {
    return when (kind) {
        AvatarFrameKind.STANDARD -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
            ),
        )
        AvatarFrameKind.PALACE -> Brush.linearGradient(
            listOf(
                Color(0xFF6A4713),
                Color(0xFFD5A83C),
                Color(0xFFFFE19A),
            ),
        )
        AvatarFrameKind.PEAK -> Brush.linearGradient(
            listOf(
                Color(0xFF2A1D68),
                Color(0xFF6E63EE),
                Color(0xFF54D6C5),
            ),
        )
    }
}

@Composable
private fun rankFrameBrush(kind: AvatarFrameKind): Brush {
    return when (kind) {
        AvatarFrameKind.STANDARD -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.colorScheme.primary,
            ),
        )
        AvatarFrameKind.PALACE -> Brush.sweepGradient(
            listOf(
                Color(0xFFFFE5A5),
                Color(0xFFD09A2D),
                Color(0xFF744B12),
                Color(0xFFFFE5A5),
            ),
        )
        AvatarFrameKind.PEAK -> Brush.sweepGradient(
            listOf(
                Color(0xFF57DCCB),
                Color(0xFF7469F4),
                Color(0xFF311E78),
                Color(0xFF57DCCB),
            ),
        )
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AccountScreen(state: ReciteUiState, viewModel: ReciteViewModel) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var feedbackOpen by rememberSaveable { mutableStateOf(false) }
    var feedbackCategory by rememberSaveable { mutableStateOf("bug") }
    var feedbackTitle by rememberSaveable { mutableStateOf("") }
    var feedbackDescription by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.feedbackReceiptId) {
        if (state.feedbackReceiptId.isNotBlank()) {
            feedbackOpen = false
            feedbackCategory = "bug"
            feedbackTitle = ""
            feedbackDescription = ""
        }
    }

    if (feedbackOpen) {
        FeedbackDialog(
            category = feedbackCategory,
            title = feedbackTitle,
            description = feedbackDescription,
            submitting = state.feedbackBusy,
            notice = state.feedbackNotice,
            onCategoryChange = { feedbackCategory = it },
            onTitleChange = { feedbackTitle = it },
            onDescriptionChange = { feedbackDescription = it },
            onSubmit = {
                viewModel.submitFeedback(
                    feedbackCategory,
                    feedbackTitle,
                    feedbackDescription,
                )
            },
            onDismiss = {
                if (!state.feedbackBusy) feedbackOpen = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (state.session != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RankAvatar(
                        rank = state.rankStatus.rank,
                        size = 52.dp,
                        frameWidth = 3.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.session.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "@${state.session.slug} · ${state.rankStatus.rank.name}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ) {
                            Icon(
                                Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp).size(26.dp),
                            )
                        }
                        Column {
                            Text(
                                "登入",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "首次登入會自動建立帳號",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Seiue / BDFZ 用戶名") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密碼") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(16.dp),
                    )
                    Button(
                        onClick = { viewModel.login(username, password) },
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("登入")
                    }
                }
            }
        }
        if (state.notice.isNotBlank()) NoticeBanner(state.notice)
        FeedbackCard(
            notice = state.feedbackNotice,
            onOpen = {
                viewModel.clearFeedbackNotice()
                feedbackOpen = true
            },
        )
        UpdateCard(state, viewModel) { info, file -> viewModel.installUpdate(info, file) }
        Text(
            "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${if (BuildConfig.SELF_UPDATE_ENABLED) "R2 直裝通道" else "Google Play 通道"}",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeedbackCard(notice: String, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Rounded.Feedback,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(24.dp),
                    )
                }
                Text(
                    "意見反饋",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                FilledTonalButton(onClick = onOpen) {
                    Text("填寫")
                }
            }
            if (notice.isNotBlank()) {
                Text(
                    notice,
                    color = if (notice.contains("失敗") || notice.contains("未送達")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun FeedbackDialog(
    category: String,
    title: String,
    description: String,
    submitting: Boolean,
    notice: String,
    onCategoryChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val categories = listOf(
        "bug" to "問題",
        "content" to "內容",
        "ui" to "介面",
        "idea" to "建議",
        "other" to "其他",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Rounded.Feedback, contentDescription = null)
        },
        title = {
            Text("意見反饋", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    categories.forEach { (value, label) ->
                        FilterChip(
                            selected = category == value,
                            onClick = { onCategoryChange(value) },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { onTitleChange(it.take(160)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("主題") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { onDescriptionChange(it.take(2000)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("反饋內容") },
                    minLines = 4,
                    maxLines = 7,
                    shape = RoundedCornerShape(16.dp),
                )
                if (notice.isNotBlank()) {
                    Text(
                        notice,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !submitting && title.isNotBlank() && description.isNotBlank(),
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("送出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun UpdateCard(
    state: ReciteUiState,
    viewModel: ReciteViewModel,
    onInstall: (UpdateInfo, java.io.File) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Rounded.Update,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "App 更新",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when (val update = state.updateState) {
                            UpdateState.Idle -> "尚未檢查"
                            UpdateState.Checking -> "正在檢查更新"
                            UpdateState.Current -> if (BuildConfig.SELF_UPDATE_ENABLED) "目前是最新版本" else "由 Google Play 管理"
                            is UpdateState.Available -> "發現 ${update.info.version}"
                            is UpdateState.Downloading -> "正在下載 ${update.info.version}"
                            is UpdateState.Ready -> "${update.info.version} 可以安裝"
                            is UpdateState.Error -> "檢查失敗"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (val update = state.updateState) {
                UpdateState.Idle,
                UpdateState.Current,
                -> Unit
                UpdateState.Checking -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is UpdateState.Available -> {
                    update.info.releaseNotes.take(3).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is UpdateState.Downloading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Ready -> Text("下載與校驗已完成。")
                is UpdateState.Error -> Text(update.message, color = MaterialTheme.colorScheme.error)
            }

            when (val update = state.updateState) {
                is UpdateState.Available -> {
                    Button(
                        onClick = { viewModel.downloadUpdate(update.info) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (update.info.required) "下載必要更新" else "下載更新")
                    }
                }
                is UpdateState.Ready -> {
                    Button(
                        onClick = { onInstall(update.info, update.apk) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("安裝更新")
                    }
                }
                UpdateState.Checking,
                is UpdateState.Downloading,
                -> Unit
                else -> {
                    if (BuildConfig.SELF_UPDATE_ENABLED) {
                        FilledTonalButton(
                            onClick = { viewModel.checkForUpdate() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.updateState == UpdateState.Idle) "檢查更新" else "再次檢查")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String = "") {
    Column(
        modifier = Modifier.statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
