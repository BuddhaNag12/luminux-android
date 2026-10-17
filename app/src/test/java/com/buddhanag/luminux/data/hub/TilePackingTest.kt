package com.buddhanag.luminux.data.hub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.buddhanag.luminux.data.hub.HubTileSize.Medium
import com.buddhanag.luminux.data.hub.HubTileSize.Small
import com.buddhanag.luminux.data.hub.HubTileSize.Wide
import org.junit.Assert.assertEquals
import org.junit.Test

/** TilePackingTests in HubTilesTests.swift. */
class TilePackingTest {
    private fun placement(column: Int, row: Int, size: HubTileSize) = TilePlacement(column, row, size.width, size.height)

    @Test fun mediumTilesMakeTwoColumns() {
        val placements = TilePacking.place(listOf(Medium, Medium, Medium))
        assertEquals(listOf(placement(0, 0, Medium), placement(2, 0, Medium), placement(0, 2, Medium)), placements)
        assertEquals(4, TilePacking.rows(placements))
    }

    @Test fun smallTilesFillABlockBeforeTheNextMedium() {
        val placements = TilePacking.place(listOf(Small, Small, Small, Medium, Small))
        assertEquals(
            listOf(placement(0, 0, Small), placement(1, 0, Small), placement(2, 0, Small), placement(0, 2, Medium), placement(3, 0, Small)),
            placements,
        )
    }

    @Test fun mediumTilesStayOnWholeBlocks() {
        // A medium never straddles two blocks, even where it would fit.
        assertEquals(placement(2, 0, Medium), TilePacking.place(listOf(Small, Medium))[1])
    }

    @Test fun wideTilesTakeAWholeRow() {
        val placements = TilePacking.place(listOf(Medium, Wide, Medium))
        assertEquals(listOf(placement(0, 0, Medium), placement(0, 2, Wide), placement(2, 0, Medium)), placements)
    }

    @Test fun framesIncludeTheGutters() {
        assertEquals(Rect(208f, 0f, 412f, 204f), TilePacking.frame(placement(2, 0, Medium), 412f, 4f))
    }

    // The default hub on a 400 wide grid with no gaps (100 cells): two mediums on top, the wide tile below, then two more.
    private val sizes = listOf(Medium, Medium, Wide, Medium, Medium)
    private fun drop(held: Rect, moving: Int) = TilePacking.dropIndex(held, sizes, moving, 400f, 0f)

    @Test fun aBigTileLandsWhereItIsHeld() {
        // The wide tile dragged up over the top row.
        assertEquals(0, drop(Rect(Offset(0f, 40f), Size(400f, 200f)), moving = 2))
    }

    @Test fun aSlowDragMovesOnceAndDoesNotFlipBack() {
        val order = sizes.toMutableList()
        var moving = 0
        var moves = 0
        for (y in 0..300 step 10) {
            val index = TilePacking.dropIndex(Rect(Offset(0f, y.toFloat()), Size(200f, 200f)), order, moving, 400f, 0f)
            if (index != moving) {
                order.add(index, order.removeAt(moving))
                moving = index
                moves++
            }
        }
        assertEquals(1, moves)
        // It ends up under the wide tile, on the left.
        assertEquals(Offset(0f, 400f), TilePacking.frame(TilePacking.place(order)[moving], 400f, 0f).topLeft)
    }

    @Test fun aSmallNudgeLeavesTheOrderAlone() {
        assertEquals(0, drop(Rect(Offset(10f, 10f), Size(200f, 200f)), moving = 0))
    }
}
