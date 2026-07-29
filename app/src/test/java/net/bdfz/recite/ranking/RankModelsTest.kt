package net.bdfz.recite.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankModelsTest {
    @Test
    fun boundariesResolveToExpectedRanksAndFrames() {
        assertEquals("初識", ReciteRanks.status(0).rank.name)
        assertEquals("啟聲", ReciteRanks.status(20).rank.name)
        assertEquals("殿堂", ReciteRanks.status(315).rank.name)
        assertEquals(AvatarFrameKind.PALACE, ReciteRanks.status(315).rank.frameKind)
        assertEquals("巔峰", ReciteRanks.status(375).rank.name)
        assertEquals(AvatarFrameKind.PEAK, ReciteRanks.status(390).rank.frameKind)
    }

    @Test
    fun progressAndDistanceUseCurrentRankSpan() {
        val status = ReciteRanks.status(40)

        assertEquals("啟聲", status.rank.name)
        assertEquals("尋章", status.nextRank?.name)
        assertEquals(15, status.pointsToNext)
        assertEquals(57, status.progressToNext)
    }

    @Test
    fun peakIsCappedAndHasNoNextRank() {
        val status = ReciteRanks.status(999)

        assertEquals(ReciteRanks.MAX_POINTS, status.points)
        assertEquals(100, status.progressToNext)
        assertEquals(0, status.pointsToNext)
        assertNull(status.nextRank)
    }
}
