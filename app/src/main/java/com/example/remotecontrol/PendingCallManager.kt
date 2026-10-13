package com.example.remotecontrol

// Bir nechta mos kontakt/raqam topilganda, foydalanuvchining tanlovini kutib turish uchun
// har bir chat (chatId) bo'yicha vaqtincha saqlanadigan holat.
object PendingCallManager {

    enum class PendingAction { CALL, SHOW_NUMBER }

    data class Pending(val candidates: List<Pair<String, String>>, val action: PendingAction)

    private val pending = mutableMapOf<String, Pending>() // chatId -> Pending

    fun setPending(chatId: String, candidates: List<Pair<String, String>>, action: PendingAction = PendingAction.CALL) {
        pending[chatId] = Pending(candidates, action)
    }

    fun getPending(chatId: String): Pending? = pending[chatId]

    fun clear(chatId: String) {
        pending.remove(chatId)
    }

    /**
     * Foydalanuvchi javobini (raqam — "1", "2" — yoki ism bo'yicha) nomzodlar
     * ro'yxati bilan solishtiradi. Mos kelsa (ism, raqam) juftligini qaytaradi.
     */
    fun resolveSelection(text: String, candidates: List<Pair<String, String>>): Pair<String, String>? {
        val trimmed = text.trim()
        trimmed.toIntOrNull()?.let { index ->
            if (index in 1..candidates.size) return candidates[index - 1]
        }
        val lower = trimmed.lowercase()
        return candidates.firstOrNull { it.first.lowercase().contains(lower) || lower.contains(it.first.lowercase()) }
    }
}
