package com.isycat.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class DropdownOption<T>(val value: T, val label: String)

/**
 * Compact single-select dropdown: a standard-height button showing the current selection that
 * opens a menu of options. Use for device/engine/enum pickers where a full-height text field is
 * too tall.
 */
@Composable
fun <T> Dropdown(
    options: List<DropdownOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }?.label ?: placeholder

    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(current, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = { onSelect(opt.value); expanded = false }
                )
            }
        }
    }
}

/**
 * Compact multi-select dropdown: a standard-height button showing a summary that opens a checkable
 * menu. The menu stays open while toggling. Use for filters (generations, formats, …).
 */
@Composable
fun <T> MultiSelectDropdown(
    options: List<DropdownOption<T>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: (Set<T>) -> String = { if (it.isEmpty()) "All" else "${it.size} selected" },
    enabled: Boolean = true
) {
    MultiSelectMenu(
        options, summary(selected), enabled, modifier,
        stateOf = { if (it in selected) OptionState.INCLUDED else OptionState.NONE },
        onPrimary = onToggle,
        onSecondary = null
    )
}

/**
 * Include/exclude multi-select dropdown: left-click ticks an option (✓ include), right-click crosses it
 * (✕ exclude). An option is never both — either click moves it out of the other state. For filters where
 * "not X" is as useful as "only X" (a regulation ticked and its predecessor crossed = what's new).
 *
 * The button summarises both using [shortLabel]: "M-C · not M-B", "All formats except M-A".
 */
@Composable
fun <T> MultiSelectDropdown(
    options: List<DropdownOption<T>>,
    selected: Set<T>,
    excluded: Set<T>,
    onChange: (selected: Set<T>, excluded: Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "All",
    shortLabel: (DropdownOption<T>) -> String = { it.label },
    enabled: Boolean = true
) {
    val inc = options.filter { it.value in selected }.map(shortLabel)
    val exc = options.filter { it.value in excluded }.map(shortLabel)
    val summary = when {
        inc.isEmpty() && exc.isEmpty() -> allLabel
        exc.isEmpty() -> inc.joinToString(", ")
        inc.isEmpty() -> "$allLabel except ${exc.joinToString(", ")}"
        else -> "${inc.joinToString(", ")} · not ${exc.joinToString(", ")}"
    }
    MultiSelectMenu(
        options, summary, enabled, modifier,
        stateOf = {
            when (it) {
                in selected -> OptionState.INCLUDED
                in excluded -> OptionState.EXCLUDED
                else -> OptionState.NONE
            }
        },
        onPrimary = { v -> if (v in selected) onChange(selected - v, excluded) else onChange(selected + v, excluded - v) },
        onSecondary = { v -> if (v in excluded) onChange(selected, excluded - v) else onChange(selected - v, excluded + v) }
    )
}

private enum class OptionState { NONE, INCLUDED, EXCLUDED }

/** Shared body of the multi-select dropdowns. [onSecondary] null = tick-only (right-click does nothing). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <T> MultiSelectMenu(
    options: List<DropdownOption<T>>,
    summary: String,
    enabled: Boolean,
    modifier: Modifier,
    stateOf: (T) -> OptionState,
    onPrimary: (T) -> Unit,
    onSecondary: ((T) -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(summary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                val state = stateOf(opt.value)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(24.dp)) {
                                when (state) {
                                    OptionState.INCLUDED -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.align(Alignment.CenterStart))
                                    OptionState.EXCLUDED -> Icon(
                                        Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.align(Alignment.CenterStart)
                                    )
                                    OptionState.NONE -> {}
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(opt.label)
                        }
                    },
                    onClick = { onPrimary(opt.value) }, // keep menu open for multi-select
                    // Right-click excludes. `clickable` only reacts to the primary button on desktop, so a
                    // right-click never also ticks.
                    modifier = if (onSecondary == null) Modifier else Modifier.onPointerEvent(PointerEventType.Press) { e ->
                        if (e.buttons.isSecondaryPressed) {
                            e.changes.forEach { it.consume() }
                            onSecondary(opt.value)
                        }
                    }
                )
            }
            if (onSecondary != null) {
                Text(
                    "Right-click to exclude",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
