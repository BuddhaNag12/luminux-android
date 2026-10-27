package com.buddhanag.luminux.feature.actions

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType

/**
 * The iOS alert with a text field ("New album", "Rename album"). [onConfirm] gets the name with spaces trimmed and
 * only when it isn't empty; the dialog closes either way.
 */
@Composable
fun NameDialog(title: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit, initial: String = "") {
    val metro = LocalMetro.current
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    val confirm = {
        onDismiss()
        field.text.trim().takeIf { it.isNotEmpty() }?.let(onConfirm)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    MetroDialog(title, onDismiss) {
        BasicTextField(
            field, { field = it },
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .background(metro.background)
                .border(2.dp, metro.accentColor)
                .padding(10.dp),
            textStyle = MetroType.body.copy(color = metro.foreground),
            cursorBrush = SolidColor(metro.accentColor),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            decorationBox = { inner ->
                Box {
                    if (field.text.isEmpty()) MetroText("Album name", MetroType.body, color = metro.secondary)
                    inner()
                }
            },
        )
        Buttons {
            MetroButton("cancel", onDismiss)
            MetroButton(confirmLabel, { confirm() })
        }
    }
}

/** The iOS confirmation dialog: a question and one destructive action. */
@Composable
fun ConfirmDialog(message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MetroDialog(message, onDismiss) {
        Buttons {
            MetroButton("cancel", onDismiss)
            MetroButton(confirmLabel, {
                onDismiss()
                onConfirm()
            })
        }
    }
}

@Composable
internal fun MetroDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(LocalMetro.current.chrome).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetroText(title, MetroType.section)
            content()
        }
    }
}

@Composable
internal fun Buttons(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) { content() }
}
