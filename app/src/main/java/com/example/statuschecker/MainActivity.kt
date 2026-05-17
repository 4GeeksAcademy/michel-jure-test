package com.example.statuschecker

import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val displayDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    private lateinit var yesButton: AppCompatImageButton
    private lateinit var noButton: AppCompatImageButton
    private lateinit var refreshButton: Button
    private lateinit var statusText: TextView
    private lateinit var endpointDataTable: TableLayout

    private data class EndpointRecord(
        val deviceId: String,
        val status: String,
        val ip: String,
        val receivedAt: String
    )

    private data class EndpointDataResult(
        val records: List<EndpointRecord> = emptyList(),
        val httpCode: Int? = null,
        val networkError: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        yesButton = findViewById(R.id.btnYes)
        noButton = findViewById(R.id.btnNo)
        refreshButton = findViewById(R.id.btnRefresh)
        statusText = findViewById(R.id.tvStatus)
        endpointDataTable = findViewById(R.id.tblEndpointData)

        showNeutralState()

        refreshButton.setOnClickListener {
            checkEndpointStatus()
        }
    }

    private fun checkEndpointStatus() {
        lifecycleScope.launch {
            setLoading(true)

            val (isOnline, endpointTextResult) = withContext(Dispatchers.IO) {
                Pair(
                    requestStatus200(EndpointConfig.URL),
                    requestTextEndpoint(EndpointConfig.TEXT_URL)
                )
            }

            updateState(isOnline)
            updateEndpointData(endpointTextResult)
            setLoading(false)
        }
    }

    private fun setLoading(loading: Boolean) {
        refreshButton.isEnabled = !loading
        refreshButton.text = if (loading) getString(R.string.loading) else getString(R.string.refresh)
        statusText.text = if (loading) getString(R.string.checking_status) else statusText.text
        if (loading) {
            renderMessageTable(getString(R.string.checking_text_data))
        }
    }

    private fun showNeutralState() {
        yesButton.alpha = 0.45f
        noButton.alpha = 0.45f
        statusText.text = getString(R.string.press_refresh)
        renderMessageTable(getString(R.string.endpoint_text_placeholder))
    }

    private fun updateState(isOk: Boolean) {
        yesButton.alpha = if (isOk) 1f else 0.3f
        noButton.alpha = if (isOk) 0.3f else 1f
        statusText.text = if (isOk) {
            getString(R.string.status_ok)
        } else {
            getString(R.string.status_error)
        }
    }

    private fun updateEndpointData(result: EndpointDataResult) {
        when {
            result.records.isNotEmpty() -> {
                val sorted = result.records.sortedBy { it.receivedAt }
                val latestTenOrdered = sorted.takeLast(10)
                renderRecordsTable(latestTenOrdered)
            }

            result.httpCode != null -> renderMessageTable(getString(R.string.endpoint_text_http_error, result.httpCode))
            result.networkError -> renderMessageTable(getString(R.string.endpoint_text_network_error))
            else -> renderMessageTable(getString(R.string.endpoint_text_empty))
        }
    }

    private fun renderRecordsTable(records: List<EndpointRecord>) {
        endpointDataTable.removeAllViews()

        val header = TableRow(this)
        addCell(header, getString(R.string.table_header_date), true)
        addCell(header, getString(R.string.table_header_device), true)
        addCell(header, getString(R.string.table_header_status), true)
        addCell(header, getString(R.string.table_header_ip), true)
        endpointDataTable.addView(header)

        records.forEach { record ->
            val row = TableRow(this)
            addCell(row, formatTimestamp(record.receivedAt), false)
            addCell(row, record.deviceId, false)
            addCell(row, record.status, false, resolveStatusColor(record.status))
            addCell(row, record.ip, false)
            endpointDataTable.addView(row)
        }
    }

    private fun renderMessageTable(message: String) {
        endpointDataTable.removeAllViews()

        val row = TableRow(this)
        val textView = TextView(this).apply {
            text = message
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            setBackgroundResource(R.drawable.bg_table_cell)
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
        }

        row.addView(
            textView,
            TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                span = 4
            }
        )
        endpointDataTable.addView(row)
    }

    private fun addCell(row: TableRow, value: String, isHeader: Boolean, textColor: Int? = null) {
        val textView = TextView(this).apply {
            text = value
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            setBackgroundResource(if (isHeader) R.drawable.bg_table_header_cell else R.drawable.bg_table_cell)
            val colorRes = textColor ?: if (isHeader) R.color.table_header_text else R.color.text_primary
            setTextColor(getColor(colorRes))
            textSize = if (isHeader) 13f else 12f
        }

        row.addView(
            textView,
            TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        )
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun formatTimestamp(value: String): String {
        if (value.isBlank()) return "-"

        return try {
            OffsetDateTime.parse(value).format(displayDateFormatter)
        } catch (_: Exception) {
            value
                .replace("T", " ")
                .substringBefore("+")
                .substringBefore("Z")
        }
    }

    private fun resolveStatusColor(status: String): Int {
        return when (status.trim().lowercase(Locale.getDefault())) {
            "alarm", "error", "fail", "failed", "critical" -> R.color.no_red
            "ok", "normal", "online", "healthy", "success" -> R.color.yes_green
            else -> R.color.text_primary
        }
    }

    private fun requestStatus200(endpointUrl: String): Boolean {
        if (endpointUrl.isBlank()) return false

        return try {
            val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            try {
                connection.connect()
                connection.responseCode == HttpURLConnection.HTTP_OK
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun requestTextEndpoint(endpointUrl: String): EndpointDataResult {
        if (endpointUrl.isBlank()) return EndpointDataResult(networkError = true)

        return try {
            val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            try {
                connection.connect()
                val code = connection.responseCode
                if (code in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                    EndpointDataResult(records = parseRecords(body))
                } else {
                    EndpointDataResult(httpCode = code)
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            EndpointDataResult(networkError = true)
        }
    }

    private fun parseRecords(body: String): List<EndpointRecord> {
        if (body.isBlank()) return emptyList()

        return try {
            val root = JSONObject(body)
            val array = root.optJSONArray("data") ?: JSONArray()
            parseRecordsFromArray(array)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseRecordsFromArray(array: JSONArray): List<EndpointRecord> {
        val result = mutableListOf<EndpointRecord>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val payload = item.optJSONObject("data")

            val deviceId = payload?.optString("device_id").orEmpty().ifBlank { "-" }
            val status = payload?.optString("status").orEmpty().ifBlank { "-" }
            val ip = item.optString("ip").orEmpty().ifBlank { "-" }
            val receivedAt = item.optString("received_at").orEmpty().ifBlank { "-" }

            result.add(
                EndpointRecord(
                    deviceId = deviceId,
                    status = status,
                    ip = ip,
                    receivedAt = receivedAt
                )
            )
        }

        return result
    }
}

object EndpointConfig {
    // Reemplaza esta URL con el endpoint que me compartas.
    const val URL = "http://65.108.223.190/health"
    const val TEXT_URL = "http://65.108.223.190/api/esp32/data"
}