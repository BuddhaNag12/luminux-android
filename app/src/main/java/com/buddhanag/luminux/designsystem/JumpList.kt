package com.buddhanag.luminux.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Month
import java.time.format.TextStyle


/** The months of one year that have photos, mapped to the first section for each month. */
data class JumpListYear(val year: Int, val sectionIds: Map<Int, String>) {
    companion object {
        /** Builds years newest first from sections that are already newest first. Undated sections are skipped. */
        fun years(sections: List<MonthSection>): List<JumpListYear> {
            val order = mutableListOf<Int>()
            val months = mutableMapOf<Int, MutableMap<Int, String>>()
            for (section in sections) {
                if (section.isUndated) continue
                months.getOrPut(section.year) { order += section.year; mutableMapOf() }.putIfAbsent(section.month, section.id)
            }
            return order.map { JumpListYear(it, months.getValue(it)) }
        }
    }
}

/** Month grid for jumping through a long timeline; months with photos use the accent color. A full-screen overlay. */
@Composable
fun JumpListView(
    sections: List<MonthSection>,
    currentSectionId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metro = LocalMetro.current
    val locale = LocalLocale.current.platformLocale
    val monthNames = remember(locale) { (1..12).map { Month.of(it).getDisplayName(TextStyle.SHORT, locale).lowercase(locale) } }
    val years = remember(sections) { JumpListYear.years(sections) }
    val current = sections.firstOrNull { it.id == currentSectionId }
    val undated = sections.firstOrNull { it.isUndated }
    val bars = WindowInsets.systemBars.asPaddingValues()

    FeatherScope(modifier = modifier.fillMaxSize().background(metro.background.copy(alpha = 0.94f))) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MetroMetrics.margin, end = MetroMetrics.margin,
                top = 24.dp + bars.calculateTopPadding(), bottom = 24.dp + bars.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Each year is a header row and three rows of months.
            itemsIndexed(years, key = { _, year -> year.year }) { position, year ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetroText(year.year.toString(), MetroType.section, Modifier.metroFeather(row = position * 4).semantics { heading() })
                    MonthGrid(12) { index, cell ->
                        val month = index + 1
                        MonthTile(
                            monthNames[index], year.sectionIds[month],
                            isCurrent = current?.year == year.year && current.month == month,
                            onSelect = onSelect,
                            modifier = cell.metroFeather(row = position * 4 + 1 + index / 4, column = index % 4),
                        )
                    }
                }
            }
            if (undated != null) {
                item(key = "undated") {
                    MonthGrid(1) { _, cell ->
                        MonthTile(
                            "undated", undated.id, isCurrent = current?.isUndated == true, onSelect = onSelect,
                            modifier = cell.metroFeather(row = years.size * 4),
                        )
                    }
                }
            }
        }
    }
}

/** Four flexible columns, 6 apart, as many rows as [count] needs. */
@Composable
private fun MonthGrid(count: Int, cell: @Composable (index: Int, modifier: Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until (count + 3) / 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (column in 0 until 4) {
                    val index = row * 4 + column
                    val modifier = Modifier.weight(1f).aspectRatio(1f)
                    if (index < count) cell(index, modifier) else Spacer(modifier)
                }
            }
        }
    }
}

@Composable
private fun MonthTile(name: String, sectionId: String?, isCurrent: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val metro = LocalMetro.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .tiltPress(interaction, trackTouch = false)
            .background(if (sectionId == null) metro.chrome else metro.accentColor)
            .then(if (isCurrent) Modifier.border(2.dp, metro.foreground) else Modifier)
            .clickable(interaction, indication = null, enabled = sectionId != null) { sectionId?.let(onSelect) },
    ) {
        BasicText(
            name,
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
            style = metroFont(22.sp, Semilight),
            color = { if (sectionId == null) metro.secondary.copy(alpha = 0.5f) else Color.White },
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 13.2.sp, maxFontSize = 22.sp),
        )
    }
}
