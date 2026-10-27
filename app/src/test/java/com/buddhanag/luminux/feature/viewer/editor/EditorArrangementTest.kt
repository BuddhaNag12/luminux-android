package com.buddhanag.luminux.feature.viewer.editor

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorArrangementTest {
    @Test fun phonesStackTheToolsUnderThePhoto() {
        assertEquals(EditorArrangement.Compact, editorArrangement(411.dp, 891.dp, null))
    }

    @Test fun aSplitLineSplitsUnlessTheWindowIsWideAndExpanded() {
        assertEquals(EditorArrangement.Split, editorArrangement(673.dp, 841.dp, 420.dp))
        assertEquals(EditorArrangement.Split, editorArrangement(411.dp, 891.dp, 400.dp))
        // Side by side is checked first: it wins over the split row.
        assertEquals(EditorArrangement.SideBySide, editorArrangement(841.dp, 673.dp, 336.dp))
    }

    @Test fun sideBySideNeedsBothAnExpandedWidthAndALandscapeShape() {
        assertEquals(EditorArrangement.SideBySide, editorArrangement(915.dp, 411.dp, null))
        assertEquals(EditorArrangement.SideBySide, editorArrangement(600.dp, 599.dp, null))
        assertEquals(EditorArrangement.Compact, editorArrangement(599.dp, 400.dp, null))
        assertEquals(EditorArrangement.Compact, editorArrangement(700.dp, 700.dp, null))
    }
}
