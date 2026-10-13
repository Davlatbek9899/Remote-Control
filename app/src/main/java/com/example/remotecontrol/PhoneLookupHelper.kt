package com.example.remotecontrol

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhoneLookupHelper {

    data class NumberMatch(
        val number: String,          // faqat raqamlar (masalan "998932219899")
        val contactName: String?,    // kontaktlarda topilgan ism, bo'lmasa null
        val lastCallMillis: Long?    // qo'ng'iroqlar tarixidagi oxirgi vaqt, bo'lmasa null
    )

    /**
     * Berilgan oxirgi raqamlar (masalan "9899") bilan tugaydigan barcha raqamlarni —
     * ham kontaktlar, ham qo'ng'iroqlar tarixi ichidan — qidirib, birlashtirib qaytaradi.
     */
    fun findByLastDigits(context: Context, digits: String): List<NumberMatch> {
        if (digits.isBlank()) return emptyList()

        // normalizedNumber -> (contactName?, lastCallMillis?)
        val found = linkedMapOf<String, Pair<String?, Long?>>()

        // 1) Kontaktlar
        val contactsCursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )
        contactsCursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val rawNumber = it.getString(numberIdx) ?: continue
                val norm = onlyDigits(rawNumber)
                if (norm.endsWith(digits)) {
                    val existing = found[norm]
                    found[norm] = (existing?.first ?: name) to existing?.second
                }
            }
        }

        // 2) Qo'ng'iroqlar tarixi
        val callCursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.CACHED_NAME),
            null, null, "${CallLog.Calls.DATE} DESC"
        )
        callCursor?.use {
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            while (it.moveToNext()) {
                val rawNumber = it.getString(numberIdx) ?: continue
                val norm = onlyDigits(rawNumber)
                if (norm.endsWith(digits)) {
                    val date = it.getLong(dateIdx)
                    val cachedName = it.getString(nameIdx)
                    val existing = found[norm]
                    // DESC tartibda o'qiyapmiz, shuning uchun birinchi uchragani eng so'nggi qo'ng'iroq
                    val lastCall = existing?.second ?: date
                    val name = existing?.first ?: cachedName
                    found[norm] = name to lastCall
                }
            }
        }

        return found.map { (number, pair) ->
            NumberMatch(number, pair.first, pair.second)
        }.sortedWith(
            compareByDescending<NumberMatch> { it.contactName != null }
                .thenByDescending { it.lastCallMillis ?: 0L }
        )
    }

    private fun onlyDigits(s: String) = s.filter { it.isDigit() }

    /** "998932219899" -> "+998 93 221 98 99" ko'rinishida formatlaydi (O'zbekiston raqamlari uchun). */
    fun formatUzPhone(digits: String): String {
        return when {
            digits.length == 12 && digits.startsWith("998") ->
                "+${digits.substring(0, 3)} ${digits.substring(3, 5)} ${digits.substring(5, 8)} " +
                        "${digits.substring(8, 10)} ${digits.substring(10, 12)}"
            digits.length == 9 ->
                "+998 ${digits.substring(0, 2)} ${digits.substring(2, 5)} " +
                        "${digits.substring(5, 7)} ${digits.substring(7, 9)}"
            else -> "+$digits"
        }
    }

    fun formatCallDate(millis: Long): String =
        SimpleDateFormat("dd:MM:yyyy    HH:mm", Locale.getDefault()).format(Date(millis))
}
