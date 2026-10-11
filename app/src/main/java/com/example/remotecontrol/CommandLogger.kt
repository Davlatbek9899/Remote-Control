package com.example.remotecontrol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object CommandLogger {

    private const val PREFS_NAME = "rc_logs"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOGS = 200

    data class LogEntry(val sender: String, val command: String, val time: String)

    fun log(context: Context, sender: String, command: String) {
        val logs = getLogsRaw(context)
        val time = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = JSONObject().apply {
            put("sender", sender)
            put("command", command)
            put("time", time)
        }
        logs.put(entry)
        val trimmed = JSONArray()
        val start = if (logs.length() > MAX_LOGS) logs.length() - MAX_LOGS else 0
        for (i in start until logs.length()) trimmed.put(logs.get(i))
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOGS, trimmed.toString()).apply()
    }

    fun getLogs(context: Context): List<LogEntry> {
        val raw = getLogsRaw(context)
        val list = mutableListOf<LogEntry>()
        for (i in 0 until raw.length()) {
            val obj = raw.getJSONObject(i)
            list.add(LogEntry(
                obj.optString("sender", "?"),
                obj.optString("command", "?"),
                obj.optString("time", "?")
            ))
        }
        return list
    }

    fun clearLog(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_LOGS).apply()
    }

    private fun getLogsRaw(context: Context): JSONArray {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOGS, "[]") ?: "[]"
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }
}
