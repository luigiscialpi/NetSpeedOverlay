package com.example.netspeedoverlay.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Mini-grafico opzionale della cronologia recente di download+upload
 * combinati (una sola linea), disegnato come view figlia dentro l'overlay.
 * Buffer circolare a dimensione fissa: nessuna allocazione per-frame oltre
 * al Path (ricostruito ad ogni sample, frequenza già bassa — updateIntervalMs
 * minimo 500ms).
 *
 * A differenza di overlayRoot (che usa INVISIBLE per continuare a ricevere
 * WindowInsets, vedi NetSpeedOverlayService), questa view figlia NON ha
 * alcun listener di insets: usare View.GONE per nasconderla è corretto e
 * voluto, perché libera lo spazio occupato nel layout quando disattivata.
 */
class SparklineView(context: Context) : View(context) {

    private val history = ArrayDeque<Long>()
    private val maxSamples = 30

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val path = Path()

    fun pushSample(combinedBytesPerSec: Long) {
        history.addLast(combinedBytesPerSec)
        while (history.size > maxSamples) history.removeFirst()
        invalidate()
    }

    fun setLineColor(argb: Int) {
        linePaint.color = argb
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (history.size < 2) return
        val maxValue = (history.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val stepX = width.toFloat() / (maxSamples - 1)
        path.reset()
        history.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value.toFloat() / maxValue) * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }
}
