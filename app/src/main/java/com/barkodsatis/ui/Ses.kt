package com.barkodsatis.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Okutma sesi ve titreşim. */
class Ses(context: Context) {
    private val ton: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
    private val titresim: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    }.getOrNull()

    fun basarili() {
        ton?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        titret(40)
    }

    fun hata() {
        ton?.startTone(ToneGenerator.TONE_SUP_ERROR, 350)
        titret(250)
    }

    private fun titret(ms: Long) {
        val v = titresim ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }

    fun kapat() { ton?.release() }
}
