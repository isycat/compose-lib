package com.isycat.compose.util

/**
 * Lightweight, dependency-free fuzzy search suitable for filtering in-memory lists
 * (command palettes, database browsers, autocompletes, etc.).
 *
 * Matching is subsequence-based: every character of the query must appear in the target in
 * order, but not necessarily contiguously. Scoring rewards contiguous runs, matches at the
 * start of the target, and matches at word boundaries (after a space, `-`, `_` or a
 * lower→upper case transition), so the most "intuitive" matches sort first.
 *
 * Matching is case-insensitive. A blank query matches everything with a score of 0.
 */
object FuzzySearch {

    data class Result<T>(
        val item: T,
        val score: Int,
        /** Indices into the matched target string that the query characters landed on. */
        val matchedIndices: List<Int>
    )

    // Scoring constants (tuned to feel similar to fzf-style matchers).
    private const val SCORE_MATCH = 16
    private const val BONUS_CONSECUTIVE = 8
    private const val BONUS_START = 12
    private const val BONUS_WORD_BOUNDARY = 8
    private const val PENALTY_GAP = 1

    /**
     * Scores [query] against [target]. Returns `null` when [query] is not a subsequence of
     * [target]. Higher scores are better. A blank query yields a score of 0.
     */
    fun score(query: String, target: String): Int? = match(query, target)?.score

    private fun match(query: String, target: String): Result<Unit>? {
        if (query.isBlank()) return Result(Unit, 0, emptyList())
        if (target.isEmpty()) return null

        val q = query.trim().lowercase()
        val t = target.lowercase()

        var score = 0
        var queryIndex = 0
        var prevMatchIndex = -1
        val indices = ArrayList<Int>(q.length)

        for (targetIndex in t.indices) {
            if (queryIndex >= q.length) break
            if (t[targetIndex] != q[queryIndex]) continue

            var charScore = SCORE_MATCH
            if (targetIndex == 0) {
                charScore += BONUS_START
            } else if (isWordBoundary(target, targetIndex)) {
                charScore += BONUS_WORD_BOUNDARY
            }
            if (prevMatchIndex >= 0) {
                if (targetIndex == prevMatchIndex + 1) {
                    charScore += BONUS_CONSECUTIVE
                } else {
                    charScore -= (targetIndex - prevMatchIndex - 1) * PENALTY_GAP
                }
            }

            score += charScore
            indices.add(targetIndex)
            prevMatchIndex = targetIndex
            queryIndex++
        }

        if (queryIndex < q.length) return null // not all query chars matched

        // Slightly favour shorter targets so exact-ish hits beat long strings that happen to contain the chars.
        score -= (t.length - q.length) / 8

        return Result(Unit, score, indices)
    }

    private fun isWordBoundary(target: String, index: Int): Boolean {
        if (index <= 0) return true
        val prev = target[index - 1]
        if (prev == ' ' || prev == '-' || prev == '_' || prev == '.' || prev == '/') return true
        // camelCase boundary
        return prev.isLowerCase() && target[index].isUpperCase()
    }

    /**
     * Filters and ranks [items] by how well [query] matches the string produced by [key].
     * Non-matching items are dropped. Ties are broken by the key string (stable, alphabetical).
     */
    fun <T> search(
        query: String,
        items: List<T>,
        limit: Int = Int.MAX_VALUE,
        key: (T) -> String
    ): List<Result<T>> {
        if (query.isBlank()) {
            return items.asSequence()
                .sortedBy { key(it).lowercase() }
                .take(limit)
                .map { Result(it, 0, emptyList()) }
                .toList()
        }

        return items.asSequence()
            .mapNotNull { item ->
                match(query, key(item))?.let { m -> Result(item, m.score, m.matchedIndices) }
            }
            .sortedWith(compareByDescending<Result<T>> { it.score }.thenBy { key(it.item).lowercase() })
            .take(limit)
            .toList()
    }

    /**
     * Like [search], but each item may expose multiple searchable strings (e.g. a display name
     * and aliases). The best-scoring key wins; [matchedIndices] refer to that winning key.
     */
    fun <T> searchMulti(
        query: String,
        items: List<T>,
        limit: Int = Int.MAX_VALUE,
        keys: (T) -> List<String>
    ): List<Result<T>> {
        if (query.isBlank()) {
            return items.asSequence()
                .sortedBy { keys(it).firstOrNull()?.lowercase() ?: "" }
                .take(limit)
                .map { Result(it, 0, emptyList()) }
                .toList()
        }

        return items.asSequence()
            .mapNotNull { item ->
                keys(item)
                    .mapNotNull { k -> match(query, k) }
                    .maxByOrNull { it.score }
                    ?.let { best -> Result(item, best.score, best.matchedIndices) }
            }
            .sortedWith(
                compareByDescending<Result<T>> { it.score }
                    .thenBy { keys(it.item).firstOrNull()?.lowercase() ?: "" }
            )
            .take(limit)
            .toList()
    }
}
