package com.example.speechcoach.core

enum class TranscriptChange { UNCHANGED, REMOVED, ADDED }

data class TranscriptSpan(val text: String, val change: TranscriptChange) {
    val changed: Boolean get() = change != TranscriptChange.UNCHANGED
}

data class TranscriptComparison(
    val original: List<TranscriptSpan>,
    val improved: List<TranscriptSpan>,
    val changes: List<TranscriptSpan>,
    val approximate: Boolean,
)

object TranscriptComparer {
    private val tokens = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*|\\s+|[^\\p{L}\\p{N}\\s]+")
    private const val MAX_MATRIX_CELLS = 2_000_000L

    fun compare(original: String, improved: String): TranscriptComparison {
        val before = tokens.findAll(original).map { it.value }.toList()
        val after = tokens.findAll(improved).map { it.value }.toList()
        var prefix = 0
        while (prefix < before.size && prefix < after.size && before[prefix] == after[prefix]) {
            prefix++
        }
        var suffix = 0
        while (
            suffix < before.size - prefix && suffix < after.size - prefix &&
            before[before.lastIndex - suffix] == after[after.lastIndex - suffix]
        ) {
            suffix++
        }
        val originalSpans = SpanBuilder()
        val improvedSpans = SpanBuilder()
        val changes = SpanBuilder()
        for (index in 0 until prefix) {
            originalSpans.add(before[index], TranscriptChange.UNCHANGED)
            improvedSpans.add(after[index], TranscriptChange.UNCHANGED)
            changes.add(before[index], TranscriptChange.UNCHANGED)
        }

        val beforeCount = before.size - prefix - suffix
        val afterCount = after.size - prefix - suffix
        val approximate = (beforeCount + 1L) * (afterCount + 1L) > MAX_MATRIX_CELLS
        if (approximate) {
            for (index in prefix until before.size - suffix) {
                originalSpans.add(before[index], TranscriptChange.REMOVED)
                changes.add(before[index], TranscriptChange.REMOVED)
            }
            for (index in prefix until after.size - suffix) {
                improvedSpans.add(after[index], TranscriptChange.ADDED)
                changes.add(after[index], TranscriptChange.ADDED)
            }
        } else {
            val lengths = Array(beforeCount + 1) { IntArray(afterCount + 1) }
            for (i in beforeCount - 1 downTo 0) {
                for (j in afterCount - 1 downTo 0) {
                    lengths[i][j] = if (before[prefix + i] == after[prefix + j]) {
                        lengths[i + 1][j + 1] + 1
                    } else {
                        maxOf(lengths[i + 1][j], lengths[i][j + 1])
                    }
                }
            }
            var i = 0
            var j = 0
            while (i < beforeCount || j < afterCount) {
                when {
                    i < beforeCount && j < afterCount && before[prefix + i] == after[prefix + j] -> {
                        originalSpans.add(before[prefix + i], TranscriptChange.UNCHANGED)
                        improvedSpans.add(after[prefix + j], TranscriptChange.UNCHANGED)
                        changes.add(before[prefix + i], TranscriptChange.UNCHANGED)
                        i++
                        j++
                    }
                    i < beforeCount && (j == afterCount || lengths[i + 1][j] >= lengths[i][j + 1]) -> {
                        originalSpans.add(before[prefix + i], TranscriptChange.REMOVED)
                        changes.add(before[prefix + i++], TranscriptChange.REMOVED)
                    }
                    else -> {
                        improvedSpans.add(after[prefix + j], TranscriptChange.ADDED)
                        changes.add(after[prefix + j++], TranscriptChange.ADDED)
                    }
                }
            }
        }
        for (index in suffix downTo 1) {
            originalSpans.add(before[before.size - index], TranscriptChange.UNCHANGED)
            improvedSpans.add(after[after.size - index], TranscriptChange.UNCHANGED)
            changes.add(before[before.size - index], TranscriptChange.UNCHANGED)
        }
        return TranscriptComparison(originalSpans.build(), improvedSpans.build(), changes.build(), approximate)
    }

    private class SpanBuilder {
        private val spans = mutableListOf<TranscriptSpan>()
        private val text = StringBuilder()
        private var change = TranscriptChange.UNCHANGED

        fun add(value: String, nextChange: TranscriptChange) {
            if (text.isNotEmpty() && change != nextChange) flush()
            change = nextChange
            text.append(value)
        }

        fun build(): List<TranscriptSpan> {
            flush()
            return spans
        }

        private fun flush() {
            if (text.isNotEmpty()) {
                spans.add(TranscriptSpan(text.toString(), change))
                text.clear()
            }
        }
    }
}
