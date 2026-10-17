package com.buddhanag.luminux.feature.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType

/** One dialog button; the dialog closes after any of them. */
internal data class DialogAction(val title: String, val onClick: () -> Unit = {})

/** The text field of a naming dialog. */
internal class DialogField(val placeholder: String, val value: String, val onChange: (String) -> Unit)

/**
 * The iOS alerts and confirmation dialogs, drawn the Metro way: a chrome panel with a title, an optional message and
 * text field, and outlined buttons. The last action is the default (keyboard done runs it).
 */
@Composable
internal fun HubDialog(
    title: String?,
    actions: List<DialogAction>,
    onDismiss: () -> Unit,
    message: String? = null,
    field: DialogField? = null,
) {
    val metro = LocalMetro.current
    val run = { action: DialogAction ->
        onDismiss()
        action.onClick()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(metro.chrome).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (title != null) MetroText(title, MetroType.section)
            if (message != null) MetroText(message, MetroType.body)
            if (field != null) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                BasicTextField(
                    field.value, field.onChange,
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .semantics { contentDescription = field.placeholder },
                    textStyle = MetroType.body.copy(color = metro.foreground),
                    singleLine = true,
                    cursorBrush = SolidColor(metro.accentColor),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { run(actions.last()) }),
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxWidth().border(2.dp, metro.accentColor).background(metro.background).padding(10.dp),
                        ) {
                            if (field.value.isEmpty()) MetroText(field.placeholder, MetroType.body, color = metro.secondary)
                            inner()
                        }
                    },
                )
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                actions.forEach { action -> MetroButton(action.title, { run(action) }) }
            }
        }
    }
}
