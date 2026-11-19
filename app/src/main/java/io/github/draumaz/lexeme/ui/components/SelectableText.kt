package io.github.draumaz.lexeme.ui.components

import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration

/**
 * Reading text you can long-press to select and copy. Starting a selection lands with a heavy
 * click rather than the system's faint text tick; dragging the handles afterwards keeps the
 * light ticks, so stretching across a paragraph doesn't buzz on every letter.
 */
@Composable
fun SelectableText(content: @Composable () -> Unit) {
    val base = LocalHapticFeedback.current
    val strong = rememberHeavyClick()
    val longPress = LocalViewConfiguration.current.longPressTimeoutMillis
    val press = remember { PressWatch() }
    val haptics = remember(base, strong, longPress) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                val selecting = hapticFeedbackType == HapticFeedbackType.TextHandleMove ||
                    hapticFeedbackType == HapticFeedbackType.LongPress
                if (selecting && press.claimSelectionStart(longPress)) strong() else base.performHapticFeedback(hapticFeedbackType)
            }
        }
    }
    Box(
        // Watches presses without taking them, so taps on links and folds work as before.
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                press.downAt = down.uptimeMillis
                press.claimed = false
            }
        }
    ) {
        CompositionLocalProvider(LocalHapticFeedback provides haptics) {
            SelectionContainer(content = content)
        }
    }
}

private class PressWatch {
    var downAt = Long.MIN_VALUE
    var claimed = true

    /**
     * Whether a selection haptic is the one that starts a selection: the first of this press,
     * arriving as the finger has been held for a long press (and not a beat later, mid-drag).
     */
    fun claimSelectionStart(longPress: Long): Boolean {
        val held = SystemClock.uptimeMillis() - downAt
        if (claimed || held < longPress - 50 || held > longPress + 400) return false
        claimed = true
        return true
    }
}

/** A heavy click, played as touch feedback so it follows the system's touch-vibration setting. */
@Composable
private fun rememberHeavyClick(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        val context = view.context
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        val play: () -> Unit = {
            when {
                !view.isHapticFeedbackEnabled -> Unit
                vibrator == null || !vibrator.hasVibrator() -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
                else -> vibrator.vibrate(effect)
            }
        }
        play
    }
}
