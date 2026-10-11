package com.example.remotecontrol

import android.content.Context
import android.content.SharedPreferences

object SecurityManager {

    private const val PREFS_NAME = "rc_security"
    private const val KEY_BLOCKED_NUMBERS = "blocked_numbers"
    private const val SEPARATOR = ","

    fun getBlockedNumbers(context: Context): Set<String> {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_BLOCKED_NUMBERS, "") ?: ""
        return if (raw.isEmpty()) emptySet()
        else raw.split(SEPARATOR).map { it.trim() }.toSet()
    }

    // Raqam bloklangan emasmi tekshirish
    fun isAllowed(context: Context, sender: String): Boolean {
        val blocked = getBlockedNumbers(context)
        // Ro'yxat bo'sh bo'lsa — hammaga ruxsat
        if (blocked.isEmpty()) return true
        return blocked.none { normalizeNumber(it) == normalizeNumber(sender) }
    }

    fun blockNumber(context: Context, number: String) {
        val current = getBlockedNumbers(context).toMutableSet()
        current.add(normalizeNumber(number))
        saveNumbers(context, current)
    }

    fun unblockNumber(context: Context, number: String) {
        val current = getBlockedNumbers(context).toMutableSet()
        current.remove(normalizeNumber(number))
        saveNumbers(context, current)
    }

    fun clearAll(context: Context) {
        getPrefs(context).edit().remove(KEY_BLOCKED_NUMBERS).apply()
    }

    private fun saveNumbers(context: Context, numbers: Set<String>) {
        getPrefs(context).edit()
            .putString(KEY_BLOCKED_NUMBERS, numbers.joinToString(SEPARATOR))
            .apply()
    }

    private fun normalizeNumber(number: String): String {
        return number.replace(Regex("[^0-9]"), "").takeLast(9)
    }

    // ── BUYRUQ KELGAN RAQAMLAR ───────────────────────────────────────

    private const val KEY_COMMAND_SENDERS = "command_senders"

    fun getCommandSenders(context: Context): Set<String> {
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_COMMAND_SENDERS, "") ?: ""
        return if (raw.isEmpty()) emptySet()
        else raw.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun addCommandSender(context: Context, number: String) {
        val current = getCommandSenders(context).toMutableSet()
        current.add(number)
        getPrefs(context).edit()
            .putString(KEY_COMMAND_SENDERS, current.joinToString(SEPARATOR))
            .apply()
    }

    fun clearCommandSenders(context: Context) {
        getPrefs(context).edit().remove(KEY_COMMAND_SENDERS).apply()
    }

    // ── PAROL ────────────────────────────────────────────────────────

    private const val KEY_PASSWORD = "command_password"

    fun getPassword(context: Context): String? {
        return getPrefs(context).getString(KEY_PASSWORD, null)
    }

    fun setPassword(context: Context, password: String) {
        getPrefs(context).edit().putString(KEY_PASSWORD, password).apply()
    }

    // Buyruq va parolni ajratish: "#ping_1234" -> ("#ping", "1234")
    // "#contacts:D_1234" -> ("#contacts:D", "1234")
    fun parseCommand(raw: String): Pair<String, String?> {
        val lastUnderscore = raw.lastIndexOf('_')
        if (lastUnderscore == -1) return Pair(raw, null)
        val possiblePassword = raw.substring(lastUnderscore + 1)
        // Parol: oxirgi _ dan keyin aynan 4 ta raqam
        return if (possiblePassword.length == 4 && possiblePassword.all { it.isDigit() }) {
            Pair(raw.substring(0, lastUnderscore), possiblePassword)
        } else {
            Pair(raw, null)
        }
    }

    // Parolni tekshirish
    fun checkPassword(context: Context, password: String?): Boolean {
        val stored = getPassword(context)
        // Parol o'rnatilmagan bo'lsa — hammaga ruxsat
        if (stored == null) return true
        return stored == password
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
