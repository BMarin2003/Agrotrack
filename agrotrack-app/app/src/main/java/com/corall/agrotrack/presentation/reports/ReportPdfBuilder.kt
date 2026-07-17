package com.corall.agrotrack.presentation.reports

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.corall.agrotrack.domain.model.Sensor
import com.corall.agrotrack.domain.model.SensorReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PAGE_WIDTH  = 595 // A4 a 72pt/pulgada
private const val PAGE_HEIGHT = 842
private const val MARGIN      = 40f
private const val CONTENT_W   = PAGE_WIDTH - 2 * MARGIN
private const val BLOCK_HEIGHT = 190f

/**
 * PDF pensado para una persona sin conocimientos técnicos: resumen visual
 * (mín/prom/máx + gráfico de tendencia) por sensor, sin volcar la tabla
 * cruda de lecturas — para eso ya está el formato CSV/JSON.
 */
object ReportPdfBuilder {

    fun build(periodLabel: String, sensors: List<Pair<Sensor, List<SensorReading>>>): PdfDocument {
        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = drawHeader(canvas, MARGIN, periodLabel, isFirstPage = true)

        if (sensors.isEmpty()) {
            canvas.drawText("Sin datos para el período seleccionado.", MARGIN, y + 14f, bodyPaint())
        }

        for ((sensor, readings) in sensors) {
            if (y + BLOCK_HEIGHT > PAGE_HEIGHT - MARGIN - 24f) {
                drawFooter(canvas, pageNumber)
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = drawHeader(canvas, MARGIN, periodLabel, isFirstPage = false)
            }
            y = drawSensorBlock(canvas, y, sensor, readings)
        }

        drawFooter(canvas, pageNumber)
        doc.finishPage(page)
        return doc
    }

    private fun drawHeader(canvas: Canvas, yStart: Float, periodLabel: String, isFirstPage: Boolean): Float {
        var y = yStart
        if (isFirstPage) {
            canvas.drawText("AgroTrack — Reporte de Temperatura", MARGIN, y + 18f, titlePaint())
            y += 30f
            canvas.drawText("Período: $periodLabel", MARGIN, y + 12f, subtitlePaint())
            y += 16f
            val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Generado el $now", MARGIN, y + 12f, subtitlePaint())
            y += 22f
        } else {
            canvas.drawText("AgroTrack — Reporte de Temperatura (continuación)", MARGIN, y + 14f, subtitlePaint())
            y += 24f
        }
        canvas.drawLine(MARGIN, y, MARGIN + CONTENT_W, y, dividerPaint())
        y += 18f
        return y
    }

    private fun drawSensorBlock(canvas: Canvas, yStart: Float, sensor: Sensor, readings: List<SensorReading>): Float {
        var y = yStart
        canvas.drawText(sensor.name, MARGIN, y + 14f, sectionPaint())
        y += 18f
        if (sensor.gatewayName.isNotBlank()) {
            canvas.drawText(sensor.gatewayName, MARGIN, y + 10f, subtitlePaint())
            y += 16f
        } else {
            y += 4f
        }

        val temps = readings.mapNotNull { it.temperature }
        if (temps.isEmpty()) {
            canvas.drawText("Sin lecturas en el período.", MARGIN, y + 12f, bodyPaint())
            y += 100f
        } else {
            val min = temps.min(); val max = temps.max(); val avg = temps.average()
            canvas.drawText(
                "Mínima: %.1f°C     Promedio: %.1f°C     Máxima: %.1f°C".format(min, avg, max),
                MARGIN, y + 12f, bodyPaint(),
            )
            y += 20f

            val chartRect = RectF(MARGIN, y, MARGIN + CONTENT_W, y + 80f)
            canvas.drawRoundRect(chartRect, 6f, 6f, chartBgPaint())

            val floatTemps = readings.mapNotNull { it.temperature?.toFloat() }
            if (floatTemps.size >= 2) {
                val chartMin = floatTemps.min(); val chartMax = floatTemps.max()
                val range = (chartMax - chartMin).coerceAtLeast(0.5f)
                val pad = 10f
                val path = Path()
                floatTemps.forEachIndexed { i, t ->
                    val x = chartRect.left + 8f + (i.toFloat() / (floatTemps.size - 1)) * (chartRect.width() - 16f)
                    val py = chartRect.bottom - pad - ((t - chartMin) / range) * (chartRect.height() - 2 * pad)
                    if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
                }
                canvas.drawPath(path, chartLinePaint())
            }
            y += 86f

            val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            val from = readings.minByOrNull { it.receivedAt }?.receivedAt?.let { dateFormat.format(Date(it)) } ?: "—"
            val to   = readings.maxByOrNull { it.receivedAt }?.receivedAt?.let { dateFormat.format(Date(it)) } ?: "—"
            canvas.drawText("$from  →  $to   ·   ${readings.size} lecturas", MARGIN, y + 10f, smallPaint())
            y += 20f
        }

        y += 6f
        canvas.drawLine(MARGIN, y, MARGIN + CONTENT_W, y, dividerPaint())
        y += 20f
        return y
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        canvas.drawText("Página $pageNumber", PAGE_WIDTH - MARGIN - 55f, PAGE_HEIGHT - 20f, smallPaint())
        canvas.drawText("AgroTrack", MARGIN, PAGE_HEIGHT - 20f, smallPaint())
    }

    private fun titlePaint()    = Paint().apply { color = Color.rgb(13, 27, 42);    textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
    private fun subtitlePaint() = Paint().apply { color = Color.rgb(100, 116, 139); textSize = 11f; isAntiAlias = true }
    private fun sectionPaint()  = Paint().apply { color = Color.rgb(13, 27, 42);    textSize = 14f; isFakeBoldText = true; isAntiAlias = true }
    private fun bodyPaint()     = Paint().apply { color = Color.rgb(30, 41, 59);    textSize = 11f; isAntiAlias = true }
    private fun smallPaint()    = Paint().apply { color = Color.rgb(100, 116, 139); textSize = 9f;  isAntiAlias = true }
    private fun chartLinePaint() = Paint().apply { color = Color.rgb(6, 182, 212); strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true }
    private fun chartBgPaint()   = Paint().apply { color = Color.rgb(241, 245, 249); style = Paint.Style.FILL }
    private fun dividerPaint()   = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f }
}
