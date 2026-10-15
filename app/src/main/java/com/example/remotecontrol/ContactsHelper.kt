package com.example.remotecontrol

import android.content.Context
import android.provider.ContactsContract

object ContactsHelper {

    private const val THRESHOLD = 0.70

    // O'zbekcha kelishik qo'shimchalari/ko'makchilar — ismga qo'shilib ("Tolqinga")
    // yoki alohida so'z sifatida ("Tolqin ga") yozilishi mumkin. Taqqoslashdan oldin
    // bularni sinab chiqarib tashlaymiz, shunda asl ism bilan solishtiriladi.
    private val SUFFIXES = listOf("ning", "dagi", "niki", "dan", "ga", "ka", "qa", "ni", "da")

    /**
     * So'ralgan ismga (query) kamida THRESHOLD (70%) darajasida o'xshash barcha kontaktlarni
     * qaytaradi, o'xshashlik darajasi bo'yicha kamayish tartibida. Bir xil ismli (lekin turli
     * raqamli) yozuvlar bitta natijaga birlashtiriladi — eng yuqori ball bilan.
     */
    fun searchContacts(context: Context, query: String): List<Pair<String, String>> {
        val queryVariants = buildQueryVariants(query)
        if (queryVariants.isEmpty()) return emptyList()

        val scored = mutableListOf<Triple<Double, String, String>>() // score, name, number
        val seen = mutableSetOf<String>()

        forEachContact(context) { name, number ->
            val key = "$name|$number"
            if (seen.add(key)) {
                val normName = normalize(name)
                val score = queryVariants.maxOf { similarity(it, normName) }
                if (score >= THRESHOLD) {
                    scored.add(Triple(score, name, number))
                }
            }
        }

        // Bir xil ismli kontaktlarni birlashtiramiz (eng yuqori ball bilan)
        return scored.groupBy { it.second }
            .map { (_, group) -> group.maxByOrNull { it.first }!! }
            .sortedByDescending { it.first }
            .map { it.second to it.third }
    }

    /** Telefondagi barcha kontaktlarni (ism, raqam), takrorlanmagan, alifbo tartibida qaytaradi. */
    fun getAllContacts(context: Context): List<Pair<String, String>> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Pair<String, String>>()
        forEachContact(context) { name, number ->
            if (seen.add(name)) result.add(name to number)
        }
        return result.sortedBy { it.first.lowercase() }
    }

    private fun forEachContact(context: Context, action: (name: String, number: String) -> Unit) {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numberIdx) ?: continue
                action(name, number)
            }
        }
    }

    // So'rovning bir nechta mumkin bo'lgan "asl shakl" variantlarini tuzadi: to'liq holicha,
    // oxiridagi alohida yozilgan ko'makchi so'z olib tashlangan holda, va ismga yopishib
    // ketgan kelishik qo'shimchasi kesib tashlangan holda.
    private fun buildQueryVariants(query: String): List<String> {
        val base = normalize(query)
        if (base.isBlank()) return emptyList()

        val variants = linkedSetOf(base)

        // Oxirgi so'z alohida yozilgan ko'makchi bo'lsa (masalan "to'lqin ga")
        val words = base.split(Regex("\\s+"))
        if (words.size > 1 && words.last() in SUFFIXES) {
            variants.add(words.dropLast(1).joinToString(" "))
        }

        // Ismga to'g'ridan-to'g'ri yopishgan qo'shimcha bo'lsa (masalan "to'lqinga")
        val lastWord = words.last()
        for (suffix in SUFFIXES.sortedByDescending { it.length }) {
            if (lastWord.length > suffix.length + 2 && lastWord.endsWith(suffix)) {
                val stripped = (words.dropLast(1) + lastWord.removeSuffix(suffix)).joinToString(" ")
                variants.add(stripped)
                break // eng uzun mos qo'shimchadan birini topsak yetarli
            }
        }

        return variants.toList()
    }

    // Apostrof belgisining turli Unicode variantlarini ("ʻ", "ʼ", "'", "`") bitta
    // standart shaklga keltiradi — aks holda kontaktdagi va yozilgan matndagi apostrof
    // turi farq qilsa, bir xil ism ham mos kelmay qolishi mumkin.
    // Apostrofni butunlay olib tashlaymiz (normallashtirmasdan) — foydalanuvchi uni
    // yozishni unutishi tez-tez uchraydi, va bu belgi ismning mazmuniga ta'sir qilmaydi.
    private fun normalize(s: String) = s.trim().lowercase()
        .replace(Regex("[\u02BB\u02BC\u2018\u2019\u0060\u00B4']"), "")

    // Kontaktning to'liq ismi va har bir so'zi bo'yicha alohida solishtirib, eng yaxshi
    // mosligini qaytaradi — shu orqali "Sardor" so'rovi "Sardor aka" kontaktiga ham mos tushadi.
    private fun similarity(query: String, fullName: String): Double {
        if (query.isBlank()) return 0.0
        if (fullName.contains(query)) return 1.0
        var best = levenshteinSimilarity(query, fullName)
        for (word in fullName.split(Regex("\\s+"))) {
            // Ism "-bek", "-boy", "-xon", "-jon" kabi qo'shimchalar bilan davom etishi odatiy
            // hol — so'ralgan ism so'zning boshlang'ich qismi bo'lsa, buni to'liq moslik
            // (aniq moslik) deb hisoblaymiz. Lekin so'z juda qisqa (masalan bitta harf —
            // "A Sinf", "A Avaz" kabi guruh/sinf nomlarida uchraydi) bo'lsa, bu tasodifiy
            // mos kelib qolishi mumkin — shuning uchun bunday qisqa so'zlarni chetlab o'tamiz.
            if (query.length >= 3 && word.length >= 3) {
                if (word.startsWith(query)) return 1.0
                if (query.startsWith(word)) {
                    // So'rovda shu so'zdan keyin yana so'z bor bo'lsa (masalan "aka", "opa" —
                    // hurmat so'zi), bu qoldiq qism kontakt ismida ham bo'lishi shart —
                    // aks holda "Ravshan aka" so'rovi oddiy "Ravshan" kontaktiga ham
                    // noto'g'ri mos kelib qolaveradi.
                    val remainder = query.removePrefix(word).trim()
                    if (remainder.isEmpty() || fullName.contains(remainder)) return 1.0
                }
            }
            val sim = levenshteinSimilarity(query, word)
            if (sim > best) best = sim
        }
        return best
    }

    private fun levenshteinSimilarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[a.length][b.length]
    }
}
