package com.frank.jiesheng

object SplitExportNames {
    fun forSource(sourceName: String, count: Int): List<String> {
        require(count in 1..21) { "Segment count must be between 1 and 21" }
        val trimmed = sourceName.trim()
        val baseName = trimmed.substringBeforeLast('.', missingDelimiterValue = trimmed)
            .ifBlank { "音频" }
        return (1..count).map { index ->
            "${baseName}_${index.toString().padStart(2, '0')}.m4a"
        }
    }
}
