package org.usvm.ts.pbt.coverage

internal fun matchesCoveragePath(
    path: String,
    patterns: List<String>,
    sourceRoots: List<String>,
): Boolean {
    if (patterns.isEmpty()) return true
    val candidates = buildList {
        add(path)
        sourceRoots.forEach { sourceRoot ->
            if (isWithin(path, sourceRoot) && path != sourceRoot) {
                add(path.removePrefix(rootPrefix(sourceRoot)))
            }
        }
    }
    return patterns.any { pattern ->
        val regex = coverageGlobToRegex(pattern)
        candidates.any(regex::matches)
    }
}

/**
 * Converts a coverage glob into a regex over normalized forward-slash paths.
 *
 * A single `*` or `?` stays within one path segment, while `**` may cross directory separators.
 * The resulting regex matches the complete candidate path rather than an arbitrary substring.
 */
private fun coverageGlobToRegex(pattern: String): Regex {
    // Treat Windows and Unix patterns uniformly and accept the common relative-path prefix.
    val normalized = pattern.replace('\\', '/').removePrefix("./")
    val expression = StringBuilder("^")
    var index = 0
    while (index < normalized.length) {
        val character = normalized[index]
        when {
            character == '*' && normalized.getOrNull(index + 1) == '*' -> {
                if (normalized.getOrNull(index + DOUBLE_WILDCARD_LENGTH) == '/') {
                    // `**/` covers zero or more whole segments: `**/*.ts` also matches `file.ts`.
                    expression.append("(?:.*/)?")
                    index += DOUBLE_WILDCARD_WITH_SEPARATOR_LENGTH
                } else {
                    // A bare `**` consumes any characters, including directory separators.
                    expression.append(".*")
                    index += 2
                }
            }

            character == '*' -> {
                // A single wildcard consumes characters only inside the current path segment.
                expression.append("[^/]*")
                index++
            }

            character == '?' -> {
                // `?` consumes exactly one non-separator character in the current segment.
                expression.append("[^/]")
                index++
            }

            character in REGEX_SPECIAL_CHARACTERS -> {
                // Non-glob regex metacharacters are literals in coverage patterns.
                expression.append('\\').append(character)
                index++
            }

            else -> {
                expression.append(character)
                index++
            }
        }
    }
    expression.append('$')
    return Regex(expression.toString())
}

internal fun isWithin(path: String, root: String): Boolean = path == root || path.startsWith(rootPrefix(root))

private fun rootPrefix(root: String): String = if (root.endsWith('/')) root else "$root/"

private const val REGEX_SPECIAL_CHARACTERS = ".+()^$|{}[]"
private const val DOUBLE_WILDCARD_LENGTH = 2
private const val DOUBLE_WILDCARD_WITH_SEPARATOR_LENGTH = 3
