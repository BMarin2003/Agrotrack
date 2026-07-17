package com.corall.agrotrack.presentation.reports

import com.corall.agrotrack.domain.model.SensorReading
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Arma CSV/JSON a partir de pares (nombre de sensor, lectura) — misma forma de
 *  fila para el reporte de Sensor y el de Gateway, que la comparten. */
object ReportExportUtil {

    fun buildCsv(rows: List<Pair<String, SensorReading>>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder("sensor,fecha,temperatura_c\n")
        for ((sensorName, reading) in rows) {
            val fecha = dateFormat.format(Date(reading.receivedAt))
            val temp  = reading.temperature?.toString().orEmpty()
            sb.append(csvEscape(sensorName)).append(',').append(fecha).append(',').append(temp).append('\n')
        }
        return sb.toString()
    }

    fun buildJson(rows: List<Pair<String, SensorReading>>): String {
        val exportGson = GsonBuilder().setPrettyPrinting().create()
        val list = rows.map { (sensorName, reading) ->
            mapOf(
                "sensor" to sensorName,
                "received_at" to reading.receivedAt,
                "temperature_c" to reading.temperature,
            )
        }
        return exportGson.toJson(list)
    }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
