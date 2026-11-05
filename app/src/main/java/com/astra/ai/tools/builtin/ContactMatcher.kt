package com.astra.ai.tools.builtin

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import com.astra.ai.core.AstraLogger
import java.util.Locale

/**
 * Intelligent Contact Resolver supporting:
 * - Native English, Urdu, Roman Urdu, and Arabic script names
 * - Automatic transliteration and alias mapping (e.g. منان <-> Mannan/Manan, ماما <-> Mama/Ammi)
 * - Phonetic and token-based fuzzy matching
 * - Graceful fallback across device contacts
 */
object ContactMatcher {

    private const val TAG = "ContactMatcher"

    // Comprehensive Urdu script to Roman Urdu / English dictionary for frequent contacts & relationships
    private val scriptAliases = mapOf(
        "منان" to listOf("mannan", "manan", "abdul mannan"),
        "ماما" to listOf("mama", "mom", "ammi", "mother", "ami"),
        "امی" to listOf("ammi", "ami", "mom", "mother", "mama"),
        "اممی" to listOf("ammi", "ami", "mom", "mother", "mama"),
        "سلمان" to listOf("salman", "sulman"),
        "علی" to listOf("ali", "alee", "aly"),
        "عثمان" to listOf("usman", "osman", "uthman"),
        "عمر" to listOf("umar", "omer", "omar"),
        "احمد" to listOf("ahmad", "ahmed"),
        "محمد" to listOf("muhammad", "mohammad", "mohd", "muhammed"),
        "طارق" to listOf("tariq", "tareeq"),
        "حمزہ" to listOf("hamza", "humza"),
        "بلال" to listOf("bilal", "belaal"),
        "حسن" to listOf("hassan", "hasan"),
        "حسین" to listOf("hussain", "husain"),
        "وقاص" to listOf("waqas", "waqqas"),
        "کاشف" to listOf("kashif", "kasheef"),
        "اسد" to listOf("asad"),
        "بابر" to listOf("babar"),
        "فیضان" to listOf("faizan"),
        "فریحہ" to listOf("fariha", "fareeha", "pariha", "dmp fariha", "dmp fareeha"),
        "پریحہ" to listOf("fariha", "fareeha", "pariha", "dmp fariha", "dmp fareeha"),
        "ذیشان" to listOf("zeeshan", "zishan"),
        "شاہ زیب" to listOf("shahzaib", "shahzeb"),
        "محسن" to listOf("mohsin", "mohsen"),
        "رضوان" to listOf("rizwan"),
        "کامران" to listOf("kamran"),
        "فرحان" to listOf("farhan"),
        "راشد" to listOf("rashid"),
        "عمران" to listOf("imran"),
        "شعیب" to listOf("shoaib", "shoieb"),
        "شاہد" to listOf("shahid"),
        "زاہد" to listOf("zahid"),
        "نوید" to listOf("naveed", "navid"),
        "نعیم" to listOf("naeem", "naim"),
        "ندیم" to listOf("nadeem", "nadim"),
        "وسیم" to listOf("waseem", "wasim"),
        "عاطف" to listOf("atif"),
        "عاصم" to listOf("asim"),
        "فہد" to listOf("fahad"),
        "سعد" to listOf("saad"),
        "طلحہ" to listOf("talha"),
        "بابا" to listOf("baba", "abu", "abba", "father", "dad"),
        "ابو" to listOf("abu", "abba", "baba", "father", "dad"),
        "بھائی" to listOf("bhai", "brother"),
        "بہن" to listOf("behan", "sister"),
        "دوست" to listOf("dost", "friend"),
        "بیٹا" to listOf("beta", "son"),
        "بیٹی" to listOf("beti", "daughter"),
        "بیوی" to listOf("biwi", "wife"),
        "شوہر" to listOf("shohar", "husband"),
        "باس" to listOf("boss"),
        "ڈرائیور" to listOf("driver"),
        "آفس" to listOf("office"),
        "گھر" to listOf("home", "ghar")
    )

    // Reverse mapping from Latin to Urdu
    private val romanToUrduAliases: Map<String, List<String>> by lazy {
        val reverse = mutableMapOf<String, MutableList<String>>()
        for ((urdu, romans) in scriptAliases) {
            for (roman in romans) {
                reverse.getOrPut(roman) { mutableListOf() }.add(urdu)
            }
        }
        reverse
    }

    /**
     * Searches device contacts and custom address book using multi-strategy matching:
     * 1. Custom Address Book lookup (from AstraSettings)
     * 2. Direct exact match (case-insensitive)
     * 3. Exact containment (e.g. "Mannan Bhai" contains "Mannan")
     * 4. Script Alias lookup (Urdu <-> Latin)
     * 5. Transliteration matching
     * 6. Consonant-skeleton matching (vowel-independent match)
     * 7. Full contact list fuzzy ranking
     * 8. Direct ContactsContract filter query
     */
    fun findMatchingContacts(
        resolver: ContentResolver,
        query: String,
        customContacts: Map<String, String> = emptyMap()
    ): List<DiscoveredContact> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        val normalizedQuery = cleanQuery.lowercase(Locale.ROOT)

        val customDiscovered = customContacts.map { (name, number) ->
            DiscoveredContact("custom_${name.hashCode()}", name, number)
        }
        val allContacts = loadAllPhoneContacts(resolver) + customDiscovered

        if (allContacts.isEmpty()) {
            return emptyList()
        }

        // Strategy 1: Direct exact match (case-insensitive)
        val exactMatches = allContacts.filter { it.name.equals(cleanQuery, ignoreCase = true) }
        if (exactMatches.isNotEmpty()) return exactMatches

        // Strategy 2: Exact containment (e.g. "Mannan Bhai" contains "Mannan")
        val containmentMatches = allContacts.filter {
            it.name.contains(cleanQuery, ignoreCase = true) || cleanQuery.contains(it.name, ignoreCase = true)
        }
        if (containmentMatches.isNotEmpty()) return containmentMatches

        // Strategy 3: Script Alias lookup (Urdu <-> Latin)
        val aliasCandidates = mutableSetOf<String>()
        scriptAliases[cleanQuery]?.let { aliasCandidates.addAll(it) }
        romanToUrduAliases[normalizedQuery]?.let { aliasCandidates.addAll(it) }

        // Also check if any key in scriptAliases is contained in cleanQuery
        for ((urduKey, romans) in scriptAliases) {
            if (cleanQuery.contains(urduKey)) {
                aliasCandidates.addAll(romans)
            }
        }
        for ((romanKey, urdus) in romanToUrduAliases) {
            if (normalizedQuery.contains(romanKey)) {
                aliasCandidates.addAll(urdus)
            }
        }

        if (aliasCandidates.isNotEmpty()) {
            val matchedByAlias = allContacts.filter { contact ->
                val cName = contact.name.lowercase(Locale.ROOT)
                aliasCandidates.any { alias ->
                    cName.contains(alias, ignoreCase = true) || alias.contains(cName, ignoreCase = true)
                }
            }
            if (matchedByAlias.isNotEmpty()) {
                AstraLogger.i(TAG, "Found contact for \"$cleanQuery\" via script aliases: ${matchedByAlias.map { it.name }}")
                return matchedByAlias
            }
        }

        // Strategy 4: Transliteration matching for Urdu script queries
        val transliterated = transliterateUrduToLatin(cleanQuery)
        if (transliterated.isNotBlank() && transliterated != cleanQuery) {
            val matchedByTranslit = allContacts.filter {
                it.name.contains(transliterated, ignoreCase = true) || transliterated.contains(it.name.lowercase(Locale.ROOT))
            }
            if (matchedByTranslit.isNotEmpty()) {
                AstraLogger.i(TAG, "Found contact via transliteration ($transliterated): ${matchedByTranslit.map { it.name }}")
                return matchedByTranslit
            }
        }

        // Strategy 5: Consonant-skeleton matching (Soundex-style for South Asian names)
        val querySkeleton = extractConsonantSkeleton(if (transliterated.isNotBlank()) transliterated else cleanQuery)
        if (querySkeleton.length >= 2) {
            val skeletonMatches = allContacts.filter { contact ->
                val contactSkeleton = extractConsonantSkeleton(contact.name)
                contactSkeleton.contains(querySkeleton) || querySkeleton.contains(contactSkeleton)
            }
            if (skeletonMatches.isNotEmpty()) {
                AstraLogger.i(TAG, "Found contact via consonant skeleton ($querySkeleton): ${skeletonMatches.map { it.name }}")
                return skeletonMatches
            }
        }

        // Strategy 6: Fuzzy word-level token similarity
        val queryTokens = cleanQuery.split(Regex("\\s+")).filter { it.length > 1 }
        val fuzzyMatches = allContacts.filter { contact ->
            val contactTokens = contact.name.split(Regex("\\s+"))
            queryTokens.any { qTok ->
                contactTokens.any { cTok ->
                    isFuzzyMatch(qTok, cTok)
                }
            }
        }
        if (fuzzyMatches.isNotEmpty()) {
            return fuzzyMatches
        }

        // Strategy 7: Direct filter query via ContentProvider
        val filtered = queryByFilter(resolver, cleanQuery)
        if (filtered.isNotEmpty()) {
            return filtered
        }

        return emptyList()
    }

    private fun loadAllPhoneContacts(resolver: ContentResolver): List<DiscoveredContact> {
        val contacts = mutableListOf<DiscoveredContact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.let { c ->
                val idCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (c.moveToNext()) {
                    val id = if (idCol >= 0) c.getString(idCol) ?: "" else ""
                    val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                    val number = if (numCol >= 0) c.getString(numCol) ?: "" else ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        contacts.add(DiscoveredContact(id, name, number))
                    }
                }
            }
        } catch (e: Exception) {
            AstraLogger.e(TAG, "Error querying device contacts: ${e.message}")
        } finally {
            cursor?.close()
        }
        return contacts
    }

    /**
     * Translates basic Urdu characters to Latin phonetic characters.
     */
    private fun transliterateUrduToLatin(urdu: String): String {
        val sb = StringBuilder()
        for (ch in urdu) {
            val mapped = when (ch) {
                'ا', 'آ', 'ع' -> "a"
                'ب' -> "b"
                'پ' -> "p"
                'ت', 'ط' -> "t"
                'ٹ' -> "t"
                'ث', 'س', 'ص' -> "s"
                'ج' -> "j"
                'چ' -> "ch"
                'ح', 'ھ', 'ہ' -> "h"
                'خ' -> "kh"
                'د' -> "d"
                'ڈ' -> "d"
                'ذ', 'ز', 'ض', 'ظ' -> "z"
                'ر' -> "r"
                'ڑ' -> "r"
                'ژ' -> "zh"
                'ش' -> "sh"
                'غ' -> "gh"
                'ف' -> "f"
                'ق', 'ک' -> "k"
                'گ' -> "g"
                'ل' -> "l"
                'م' -> "m"
                'ن', 'ں' -> "n"
                'و' -> "o"
                'ی', 'ے' -> "i"
                else -> if (ch.isLetterOrDigit()) ch.toString() else ""
            }
            sb.append(mapped)
        }
        return sb.toString().trim()
    }

    private fun isFuzzyMatch(s1: String, s2: String): Boolean {
        if (s1.equals(s2, ignoreCase = true)) return true
        val minLen = minOf(s1.length, s2.length)
        if (minLen < 3) return false
        val distance = levenshteinDistance(s1.lowercase(Locale.ROOT), s2.lowercase(Locale.ROOT))
        return distance <= 1 || (minLen >= 5 && distance <= 2)
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1) { 0 }

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }

    fun extractConsonantSkeleton(input: String): String {
        val lower = input.lowercase(Locale.ROOT)
        val sb = StringBuilder()
        for (ch in lower) {
            when (ch) {
                'a', 'e', 'i', 'o', 'u', 'y', 'h', ' ', '-', '\'', '.', '_' -> {}
                'q', 'c' -> sb.append('k')
                'w' -> sb.append('v')
                'z', 'j' -> sb.append('z')
                else -> if (ch.isLetter()) sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun queryByFilter(resolver: ContentResolver, query: String): List<DiscoveredContact> {
        val results = mutableListOf<DiscoveredContact>()
        try {
            val filterUri = android.net.Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                android.net.Uri.encode(query)
            )
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            resolver.query(filterUri, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val id = if (idCol >= 0) c.getString(idCol) ?: "" else ""
                    val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                    val num = if (numCol >= 0) c.getString(numCol) ?: "" else ""
                    if (name.isNotBlank() && num.isNotBlank()) {
                        results.add(DiscoveredContact(id, name, num))
                    }
                }
            }
        } catch (_: Exception) {}
        return results
    }
}
