package net.bdfz.recite.ranking

enum class AvatarFrameKind {
    STANDARD,
    PALACE,
    PEAK,
}

data class ReciteRank(
    val index: Int,
    val name: String,
    val motto: String,
    val minimumPoints: Int,
    val frameKind: AvatarFrameKind = AvatarFrameKind.STANDARD,
)

data class RankStatus(
    val rank: ReciteRank,
    val nextRank: ReciteRank?,
    val points: Int,
    val pointsToNext: Int,
    val progressToNext: Int,
)

data class LeaderboardEntry(
    val position: Int,
    val displayName: String,
    val totalPoints: Int,
    val todayPoints: Int,
    val rank: ReciteRank,
    val isMe: Boolean,
)

data class LeaderboardSnapshot(
    val daily: List<LeaderboardEntry> = emptyList(),
    val total: List<LeaderboardEntry> = emptyList(),
    val meDaily: LeaderboardEntry? = null,
    val meTotal: LeaderboardEntry? = null,
    val generatedAt: String = "",
)

enum class LeaderboardScope {
    DAILY,
    TOTAL,
}

object ReciteRanks {
    const val MAX_POINTS = 390

    val ladder = listOf(
        ReciteRank(1, "初識", "初聞琅琅", 0),
        ReciteRank(2, "啟聲", "開卷有聲", 20),
        ReciteRank(3, "尋章", "循句得章", 55),
        ReciteRank(4, "知音", "文意相應", 105),
        ReciteRank(5, "博聞", "群篇入懷", 170),
        ReciteRank(6, "文心", "出口成章", 240),
        ReciteRank(7, "殿堂", "群篇成誦", 315, AvatarFrameKind.PALACE),
        ReciteRank(8, "巔峰", "琅然自成", 375, AvatarFrameKind.PEAK),
    )

    fun status(points: Int): RankStatus {
        val bounded = points.coerceIn(0, MAX_POINTS)
        val rankIndex = ladder.indexOfLast { bounded >= it.minimumPoints }.coerceAtLeast(0)
        val rank = ladder[rankIndex]
        val next = ladder.getOrNull(rankIndex + 1)
        val progress = if (next == null) {
            100
        } else {
            val span = (next.minimumPoints - rank.minimumPoints).coerceAtLeast(1)
            ((bounded - rank.minimumPoints) * 100 / span).coerceIn(0, 100)
        }
        return RankStatus(
            rank = rank,
            nextRank = next,
            points = bounded,
            pointsToNext = next?.let { (it.minimumPoints - bounded).coerceAtLeast(0) } ?: 0,
            progressToNext = progress,
        )
    }

    fun fromName(name: String, fallbackPoints: Int): ReciteRank {
        return ladder.firstOrNull { it.name == name } ?: status(fallbackPoints).rank
    }
}
