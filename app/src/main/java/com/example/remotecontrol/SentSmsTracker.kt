package com.example.remotecontrol

object SentSmsTracker {
    // Qaysi raqamlarga va qachon SMS yuborilganini saqlaymiz
    private val sentToNumbers = mutableMapOf<String, Long>()

    fun track(phoneNumber: String) {
        val key = phoneNumber.filter { it.isDigit() }.takeLast(9)
        sentToNumbers[key] = System.currentTimeMillis()
    }

    fun wasRecentlySentTo(phoneNumber: String): Boolean {
        val key = phoneNumber.filter { it.isDigit() }.takeLast(9)
        val sentTime = sentToNumbers[key] ?: return false
        val elapsed = System.currentTimeMillis() - sentTime
        return elapsed < 8000 // 8 sekund ichida
    }

    fun clear(phoneNumber: String) {
        val key = phoneNumber.filter { it.isDigit() }.takeLast(9)
        sentToNumbers.remove(key)
    }
}