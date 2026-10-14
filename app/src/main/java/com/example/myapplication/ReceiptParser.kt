package com.example.myapplication

import java.util.Locale

data class ParsedReceipt(
    val total: Double?,
    val currency: String,   // "PKR", "USD", "EUR", "GBP", or "UNKNOWN"
    val date: String?,      // yyyy-MM-dd, or null if not found
    val items: List<String>
)

object ReceiptParser {

    fun parse(text: String): ParsedReceipt {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val currency = detectCurrency(text)
        val date = findDate(lines)
        val total = findTotal(lines)

        val items = lines
            .filter { line ->
                val lower = line.lowercase(Locale.ROOT)

                !lower.contains("expenseflow") &&
                        !lower.contains("smart expense tracking") &&
                        !lower.contains("ocr text") &&
                        !lower.contains("add receipt photo") &&
                        !lower.contains("thank you") &&
                        !lower.contains("subtotal") &&
                        !lower.contains("total") &&
                        !lower.contains("change due") &&
                        !lower.contains("debit") &&
                        !lower.contains("terminal") &&
                        !lower.contains("reference") &&
                        !lower.contains("verified") &&
                        !lower.contains("manager") &&
                        !lower.contains("cashier") &&
                        line.count { it.isLetter() } >= 3 &&
                        !Regex("""^\s*[\d.,\s\-/$€£]+\s*$""").matches(line)
            }
            .take(10)

        return ParsedReceipt(
            total = total,
            currency = currency,
            date = date,
            items = items
        )
    }

    /*
     * Currency rules:
     * - PKR is recognised from "PKR", "Rs" or "Rs." only.
     * - USD from "USD", "US$" or a bare "$" (when no PKR marker exists).
     * - EUR from "EUR" or "€"; GBP from "GBP" or "£".
     * - Anything else is UNKNOWN. It is never assumed to be PKR.
     * PKR is checked first, so a receipt that clearly shows Rs stays PKR.
     */
    private fun detectCurrency(text: String): String {
        val hasPkr = Regex("""(?i)\bPKR\b|\bRs\b""").containsMatchIn(text)
        val hasUsd = Regex("""(?i)\bUSD\b|US\$|\$""").containsMatchIn(text)
        val hasEur = Regex("""(?i)\bEUR\b|€""").containsMatchIn(text)
        val hasGbp = Regex("""(?i)\bGBP\b|£""").containsMatchIn(text)

        return when {
            hasPkr -> "PKR"
            hasUsd -> "USD"
            hasEur -> "EUR"
            hasGbp -> "GBP"
            else -> "UNKNOWN"
        }
    }

    private fun findDate(lines: List<String>): String? {
        val patterns = listOf(
            Regex("""\b(20\d{2})[-/](\d{1,2})[-/](\d{1,2})\b"""),
            Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](20\d{2})\b"""),
            Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{2})\b""")
        )

        for (line in lines) {
            for ((index, pattern) in patterns.withIndex()) {
                val match = pattern.find(line) ?: continue
                val values = match.groupValues

                val year: Int
                val month: Int
                val day: Int

                if (index == 0) {
                    year = values[1].toInt()
                    month = values[2].toInt()
                    day = values[3].toInt()
                } else {
                    val first = values[1].toInt()
                    val second = values[2].toInt()
                    year = values[3].toInt().let {
                        if (it < 100) it + 2000 else it
                    }

                    // If one value is greater than 12, it must be the day.
                    // Otherwise use month/day order as the default.
                    if (first > 12 && second <= 12) {
                        day = first
                        month = second
                    } else {
                        month = first
                        day = second
                    }
                }

                if (month !in 1..12 || day !in 1..31) continue

                return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
            }
        }

        return null
    }

    private fun findTotal(lines: List<String>): Double? {
        val amountRegex = Regex(
            """(?i)(?:PKR|Rs\.?|USD|US\$|EUR|GBP|[$€£])?\s*([0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""
        )

        // Prefer the last line labelled TOTAL, excluding SUBTOTAL.
        for (line in lines.asReversed()) {
            if (
                line.contains("total", ignoreCase = true) &&
                !line.contains("subtotal", ignoreCase = true)
            ) {
                val value = amountRegex.findAll(line).toList().lastOrNull()
                    ?.groupValues?.get(1)
                    ?.replace(",", "")
                    ?.toDoubleOrNull()

                if (value != null && value > 0) return value
            }
        }

        // If TOTAL is on its own line, check the next few lines.
        for (i in lines.indices) {
            if (
                lines[i].contains("total", ignoreCase = true) &&
                !lines[i].contains("subtotal", ignoreCase = true)
            ) {
                for (j in i + 1 until minOf(i + 4, lines.size)) {
                    val match = amountRegex.find(lines[j]) ?: continue
                    val value = match.groupValues[1]
                        .replace(",", "")
                        .toDoubleOrNull()

                    if (value != null && value > 0) return value
                }
            }
        }

        return null
    }
}