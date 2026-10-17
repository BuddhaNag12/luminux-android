package com.buddhanag.luminux.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.journal.JournalEntry
import com.buddhanag.luminux.feature.actions.AddToAlbumSheet
import com.buddhanag.luminux.feature.actions.selectionActionButtons
import com.buddhanag.luminux.feature.grid.AssetGrid
import com.buddhanag.luminux.feature.grid.SelectionModel
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont
import kotlinx.coroutines.delay
import java.time.ZoneId

/** Every entry, newest first, under year headers (JournalView). */
@Composable
fun JournalScreen() {
    val metro = LocalMetro.current
    val journal by LocalContext.current.container.journal.state.collectAsStateWithLifecycle()
    val years = remember(journal.entries) { journal.entries.groupBy { it.start.atZone(ZoneId.systemDefault()).year }.toSortedMap(reverseOrder()) }
    LazyColumn(
        Modifier.fillMaxSize().background(metro.background).windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = MetroMetrics.margin + 12.dp, end = MetroMetrics.margin + 12.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Column(Modifier.metroFeather(row = 0)) {
                MetroText("LUMINUX", MetroType.overline)
                MetroText("journal", MetroType.title, Modifier.offset(x = (-3).dp).semantics { heading() })
            }
        }
        if (journal.entries.isEmpty()) item { JournalPlaceholder(journal.isBuilt, Modifier.metroFeather(row = 1)) }
        years.entries.forEachIndexed { yearIndex, (year, entries) ->
            item(key = year) {
                MetroText(year.toString(), metroFont(20.sp, Semilight), Modifier.metroFeather(row = 1 + yearIndex * 3).semantics { heading() }, color = metro.accentColor)
            }
            itemsIndexed(entries, key = { _, e -> e.id }) { index, entry ->
                JournalEntryTile(entry, journal, Modifier.metroFeather(row = 2 + yearIndex * 3 + index))
            }
        }
    }
}

/** One entry: its title, dates and note over all of its photos (JournalEntryView). */
@Composable
fun JournalEntryScreen(entryId: String) {
    val metro = LocalMetro.current
    val repository = LocalContext.current.container.journal
    val journal by repository.state.collectAsStateWithLifecycle()
    val entry = journal.entry(entryId)
    var expanded by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var editingNote by rememberSaveable { mutableStateOf(false) }
    val selection = remember { SelectionModel() }
    var addToAlbum by remember { mutableStateOf<List<MediaItem>?>(null) }

    val buttons = when {
        entry == null -> emptyList()
        selection.isActive -> selectionActionButtons(selection) { addToAlbum = it }
        else -> listOf(
            AppBarButton("rename", JournalIcons.pencil) { renaming = true },
            AppBarButton("note", JournalIcons.note) { editingNote = true },
        )
    }
    val menu = if (selection.isActive) listOf(AppBarMenuItem("cancel selection") { selection.end() }) else emptyList()
    MetroAppBarHost(buttons, expanded, { expanded = it }, Modifier.background(metro.background), menu) { padding ->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 16.dp)) {
            MetroText("LUMINUX · JOURNAL", MetroType.overline, Modifier.padding(start = MetroMetrics.margin + 4.dp).metroFeather(row = 0))
            if (entry == null) {
                MetroText("This entry isn't in your journal any more.", MetroType.body, Modifier.padding(horizontal = MetroMetrics.margin + 4.dp), color = metro.secondary)
                return@Column
            }
            Column(
                Modifier.padding(horizontal = MetroMetrics.margin + 4.dp).padding(bottom = 12.dp).metroFeather(row = 0),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicText(
                    journal.title(entry), Modifier.semantics { heading() },
                    style = metroFont(40.sp, FontWeight.Light).copy(color = metro.foreground), maxLines = 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 40.sp),
                )
                MetroText(subtitle(LocalContext.current, entry), MetroType.caption, color = metro.secondary)
                journal.note(entry)?.let { note ->
                    MetroText(note, MetroType.body, Modifier.fillMaxWidth().padding(top = 6.dp).clickable(onClickLabel = "Edits the note") { editingNote = true }, maxLines = 4)
                }
            }
            AssetGrid(
                AssetSource.Identifiers(entry.photoIds), selection, Modifier.weight(1f),
                emptyMessage = "These photos are no longer in your library.", contentPadding = padding,
            )
        }
    }
    AddToAlbumSheet(addToAlbum) { addToAlbum = null }

    if (entry != null && renaming) RenameDialog(journal.title(entry), { repository.rename(entry.id, it) }) { renaming = false }
    if (entry != null && editingNote) NoteEditor(entry, journal.title(entry), journal.note(entry).orEmpty(), { repository.setNote(entry.id, it) }) { editingNote = false }
}

/** The iOS rename alert: an empty title goes back to the automatic one. */
@Composable
private fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    val metro = LocalMetro.current
    var text by rememberSaveable { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(metro.chrome).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MetroText("Rename entry", MetroType.section)
            MetroText("Leave it empty to go back to the automatic title.", MetroType.caption, color = metro.secondary)
            BasicTextField(
                text, { text = it },
                Modifier.fillMaxWidth().border(2.dp, metro.foreground).padding(8.dp).semantics { contentDescription = "Title" },
                textStyle = MetroType.body.copy(color = metro.foreground), singleLine = true, cursorBrush = SolidColor(metro.accentColor),
            )
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton("Cancel", onDismiss)
                MetroButton("Rename", { onRename(text); onDismiss() })
            }
        }
    }
}

/** A full-height page over the entry: the note box, then save and cancel (JournalNoteEditor). */
@Composable
private fun NoteEditor(entry: JournalEntry, title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val metro = LocalMetro.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    var text by rememberSaveable(entry.id) { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(metro.background)
                // A tap anywhere outside the text box puts the keyboard away.
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = MetroMetrics.margin + 12.dp, end = MetroMetrics.margin + 12.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetroText("note", MetroType.pivot, Modifier.semantics { heading() })
            MetroText(title, MetroType.caption, color = metro.secondary)
            // Short enough that the box and its buttons stay above the keyboard; longer notes scroll inside it.
            BasicTextField(
                text, { text = it },
                Modifier.fillMaxWidth().height(150.dp).background(metro.chrome).padding(8.dp).focusRequester(focus).semantics { contentDescription = "Note" },
                textStyle = MetroType.body.copy(color = metro.foreground), cursorBrush = SolidColor(metro.accentColor),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton("save", { onSave(text); onDismiss() })
                MetroButton("cancel", onDismiss)
            }
        }
    }
    LaunchedEffect(Unit) {
        // Focus once the page has settled, so the keyboard doesn't come up mid-presentation.
        delay(450)
        focus.requestFocus()
    }
}
