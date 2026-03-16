package com.arche.threply.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.arche.threply.data.PrefsManager

/**
 * Haptic feedback utility.
 * Equivalent to iOS Haptics enum.
 */
object HapticsUtil {

    private fun isEnabled(context: Context): Boolean =
        PrefsManager.isHapticsEnabled(context)

    private fun getVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /** Light impact feedback (equivalent to iOS .light) */
    fun impact(view: View) {
        if (!isEnabled(view.context)) return
        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    /** Light tap feedback using Context (for IME key presses). */
    fun tap(context: Context) {
        if (!isEnabled(context)) return
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    /** Medium impact feedback (equivalent to iOS .medium) */
    fun impactMedium(context: Context) {
        if (!isEnabled(context)) return
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    /** Selection feedback (equivalent to iOS UISelectionFeedbackGenerator) */
    fun selection(view: View) {
        if (!isEnabled(view.context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
    }

    /** Success notification feedback */
    fun success(context: Context) {
        if (!isEnabled(context)) return
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    /** Variable strength feedback (equivalent to iOS previewStrength) */
    fun previewStrength(context: Context, intensity: Float) {
        if (!isEnabled(context)) return
        val clamped = intensity.coerceIn(0f, 1f)
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (clamped * 255).toInt().coerceIn(1, 255)
            vibrator.vibrate(VibrationEffect.createOneShot(20, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15)
        }
    }
}
