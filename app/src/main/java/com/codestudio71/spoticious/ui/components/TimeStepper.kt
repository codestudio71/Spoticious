@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.codestudio71.spoticious.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

private val StepperButtonSize = 30.dp
private val StepperIconSize = 18.dp
private val StepperValueWidth = 32.dp
private val StepperMsValueWidth = 40.dp
private val StepperUnitWidth = StepperButtonSize + StepperValueWidth + StepperButtonSize
private val StepperMsUnitWidth = StepperButtonSize + StepperMsValueWidth + StepperButtonSize
private val StepperSeparatorWidth = 10.dp

data class TimeBreakdown(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val millis: Int,
) {
    fun toMs(): Long =
        ((hours * 3600L + minutes * 60L + seconds) * 1000L + millis).coerceAtLeast(0L)

    companion object {
        fun fromMs(ms: Long): TimeBreakdown {
            val clamped = ms.coerceAtLeast(0L)
            val totalMs = (clamped % 1000L).toInt()
            val totalSec = clamped / 1000L
            val s = (totalSec % 60L).toInt()
            val totalMin = totalSec / 60L
            val m = (totalMin % 60L).toInt()
            val h = (totalMin / 60L).toInt().coerceAtMost(99)
            return TimeBreakdown(h, m, s, totalMs)
        }
    }
}

enum class TimeStepUnit(val deltaMs: Long) {
    HOURS(3_600_000L),
    MINUTES(60_000L),
    SECONDS(1_000L),
    MILLIS(10L),
}

fun replaceTimeUnit(
    timeMs: Long,
    unit: TimeStepUnit,
    value: Int,
): Long {
    val parts = TimeBreakdown.fromMs(timeMs)
    val clamped =
        when (unit) {
            TimeStepUnit.HOURS -> value.coerceIn(0, 99)
            TimeStepUnit.MINUTES -> value.coerceIn(0, 59)
            TimeStepUnit.SECONDS -> value.coerceIn(0, 59)
            TimeStepUnit.MILLIS -> value.coerceIn(0, 999)
        }
    val updated =
        when (unit) {
            TimeStepUnit.HOURS -> parts.copy(hours = clamped)
            TimeStepUnit.MINUTES -> parts.copy(minutes = clamped)
            TimeStepUnit.SECONDS -> parts.copy(seconds = clamped)
            TimeStepUnit.MILLIS -> parts.copy(millis = clamped)
        }
    return updated.toMs()
}

@Composable
fun TimeStepper(
    label: String,
    timeMs: Long,
    onNudge: (Long) -> Unit,
    onSetUnit: (TimeStepUnit, Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    showHours: Boolean = true,
) {
    val look = LocalSpoticiousLook.current
    val resolvedAccent = accent ?: look.accent
    val breakdown = remember(timeMs) { TimeBreakdown.fromMs(timeMs) }
    var editUnit by remember { mutableStateOf<TimeStepUnit?>(null) }

    editUnit?.let { unit ->
        val currentValue =
            when (unit) {
                TimeStepUnit.HOURS -> breakdown.hours
                TimeStepUnit.MINUTES -> breakdown.minutes
                TimeStepUnit.SECONDS -> breakdown.seconds
                TimeStepUnit.MILLIS -> breakdown.millis
            }
        TimeUnitEditDialog(
            unit = unit,
            initialValue = currentValue,
            accent = resolvedAccent,
            onDismiss = { editUnit = null },
            onConfirm = { value ->
                onSetUnit(unit, value)
                editUnit = null
            },
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = look.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                formatStepperTimeHms(timeMs),
                color = resolvedAccent.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }

        SpacerBetweenLabelAndControls()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showHours) {
                TimeUnitStepper(
                    unitLabel = "h",
                    display = breakdown.hours.toString().padStart(2, '0'),
                    accent = resolvedAccent,
                    valueWidth = StepperValueWidth,
                    onDelta = { sign -> onNudge(sign * TimeStepUnit.HOURS.deltaMs) },
                    onDoubleTap = { editUnit = TimeStepUnit.HOURS },
                )
                TimeStepSeparator(":", resolvedAccent)
            }
            TimeUnitStepper(
                unitLabel = "m",
                display = breakdown.minutes.toString().padStart(2, '0'),
                accent = resolvedAccent,
                valueWidth = StepperValueWidth,
                onDelta = { sign -> onNudge(sign * TimeStepUnit.MINUTES.deltaMs) },
                onDoubleTap = { editUnit = TimeStepUnit.MINUTES },
            )
            TimeStepSeparator(":", resolvedAccent)
            TimeUnitStepper(
                unitLabel = "s",
                display = breakdown.seconds.toString().padStart(2, '0'),
                accent = resolvedAccent,
                valueWidth = StepperValueWidth,
                onDelta = { sign -> onNudge(sign * TimeStepUnit.SECONDS.deltaMs) },
                onDoubleTap = { editUnit = TimeStepUnit.SECONDS },
            )
            TimeStepSeparator(".", resolvedAccent)
            TimeUnitStepper(
                unitLabel = "ms",
                display = breakdown.millis.toString().padStart(3, '0'),
                accent = resolvedAccent,
                valueWidth = StepperMsValueWidth,
                unitWidth = StepperMsUnitWidth,
                onDelta = { sign -> onNudge(sign * TimeStepUnit.MILLIS.deltaMs) },
                onDoubleTap = { editUnit = TimeStepUnit.MILLIS },
            )
        }
    }
}

@Composable
private fun SpacerBetweenLabelAndControls() {
    Box(Modifier.height(10.dp))
}

@Composable
private fun TimeStepSeparator(
    text: String,
    accent: Color,
) {
    Text(
        text = text,
        color = accent.copy(alpha = 0.55f),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .width(StepperSeparatorWidth)
                .padding(bottom = 12.dp),
    )
}

@Composable
private fun TimeUnitStepper(
    unitLabel: String,
    display: String,
    accent: Color,
    onDelta: (sign: Int) -> Unit,
    onDoubleTap: () -> Unit,
    valueWidth: Dp,
    unitWidth: Dp = StepperUnitWidth,
) {
    val look = LocalSpoticiousLook.current
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier.width(unitWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            unitLabel,
            color = look.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            HoldRepeatStepperButton(
                icon = Icons.Default.Remove,
                tint = accent,
                onStep = { onDelta(-1) },
            )
            Box(
                modifier =
                    Modifier
                        .width(valueWidth)
                        .combinedClickable(
                            onClick = {},
                            onDoubleClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDoubleTap()
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    display,
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )
            }
            HoldRepeatStepperButton(
                icon = Icons.Default.Add,
                tint = accent,
                onStep = { onDelta(+1) },
            )
        }
    }
}

@Composable
private fun TimeUnitEditDialog(
    unit: TimeStepUnit,
    initialValue: Int,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val look = LocalSpoticiousLook.current
    val maxDigits =
        when (unit) {
            TimeStepUnit.HOURS -> 2
            TimeStepUnit.MINUTES, TimeStepUnit.SECONDS -> 2
            TimeStepUnit.MILLIS -> 3
        }
    val unitLabel =
        when (unit) {
            TimeStepUnit.HOURS -> stringResource(R.string.audio_cut_unit_hours)
            TimeStepUnit.MINUTES -> stringResource(R.string.audio_cut_unit_minutes)
            TimeStepUnit.SECONDS -> stringResource(R.string.audio_cut_unit_seconds)
            TimeStepUnit.MILLIS -> stringResource(R.string.audio_cut_unit_millis)
        }
    var fieldValue by remember(unit, initialValue) { mutableStateOf(initialValue.toString()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = look.dialogFill,
        title = {
            Text(
                stringResource(R.string.audio_cut_edit_unit_title, unitLabel),
                color = look.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it.filter { c -> c.isDigit() }.take(maxDigits) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle =
                    androidx.compose.ui.text.TextStyle(
                        color = look.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = look.textPrimary,
                        unfocusedTextColor = look.textPrimary,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = look.textMuted,
                        cursorColor = accent,
                        focusedLabelColor = accent,
                        unfocusedLabelColor = look.textMuted,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = fieldValue.toIntOrNull() ?: initialValue
                    onConfirm(parsed)
                },
            ) {
                Text(stringResource(R.string.save), color = accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = look.accentAlt)
            }
        },
    )
}

/**
 * Klik = jeden krok. Przytrzymanie: po ~350 ms powtarzanie z przyspieszaniem (do ~35 ms).
 */
@Composable
private fun HoldRepeatStepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onStep: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier =
            Modifier
                .size(StepperButtonSize)
                .pointerInput(onStep) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val downAt = System.currentTimeMillis()
                        var repeated = false
                        val repeatJob =
                            scope.launch {
                                delay(350)
                                repeated = true
                                var interval = 220L
                                while (isActive) {
                                    onStep()
                                    delay(interval)
                                    interval = max(35L, (interval * 0.78f).toLong())
                                }
                            }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            if (!change.pressed) break
                        }
                        repeatJob.cancel()
                        if (!repeated && System.currentTimeMillis() - downAt < 400) {
                            onStep()
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(StepperIconSize))
    }
}

/** mm:ss — krótki format (lista fragmentów, oś waveform). */
fun formatStepperTime(ms: Long): String {
    val parts = TimeBreakdown.fromMs(ms)
    return if (parts.hours > 0) {
        "%d:%02d:%02d".format(parts.hours, parts.minutes, parts.seconds)
    } else {
        "%d:%02d".format(parts.minutes, parts.seconds)
    }
}

/** h:mm:ss.mmm — pełny podgląd w stepperze. */
fun formatStepperTimeHms(ms: Long): String {
    val p = TimeBreakdown.fromMs(ms)
    return if (p.hours > 0) {
        "%d:%02d:%02d.%03d".format(p.hours, p.minutes, p.seconds, p.millis)
    } else {
        "%d:%02d.%03d".format(p.minutes, p.seconds, p.millis)
    }
}
