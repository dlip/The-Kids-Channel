package com.thekidschannel.media

object NaturalOrder : Comparator<String> {
    private val chunks = Regex("\\d+|\\D+")

    override fun compare(left: String, right: String): Int {
        val leftChunks = chunks.findAll(left).map { it.value }.toList()
        val rightChunks = chunks.findAll(right).map { it.value }.toList()

        for (index in 0 until minOf(leftChunks.size, rightChunks.size)) {
            val result = compareChunk(leftChunks[index], rightChunks[index])
            if (result != 0) return result
        }

        return leftChunks.size.compareTo(rightChunks.size)
            .takeIf { it != 0 }
            ?: left.compareTo(right, ignoreCase = true)
                .takeIf { it != 0 }
            ?: left.compareTo(right)
    }

    private fun compareChunk(left: String, right: String): Int {
        val leftIsNumber = left.firstOrNull()?.isDigit() == true
        val rightIsNumber = right.firstOrNull()?.isDigit() == true
        if (leftIsNumber && rightIsNumber) {
            val leftValue = left.trimStart('0').ifEmpty { "0" }
            val rightValue = right.trimStart('0').ifEmpty { "0" }
            return leftValue.length.compareTo(rightValue.length)
                .takeIf { it != 0 }
                ?: leftValue.compareTo(rightValue)
                    .takeIf { it != 0 }
                ?: left.length.compareTo(right.length)
        }
        return left.compareTo(right, ignoreCase = true)
            .takeIf { it != 0 }
            ?: left.compareTo(right)
    }
}
