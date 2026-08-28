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
                add(path.removePrefix("$sourceRoot/"))
            }
        }
    }
    return patterns.any { pattern ->
        val regex = coverageGlobToRegex(pattern)
        candidates.any(regex::matches)
    }
}

private fun coverageGlobToRegex(pattern: String): Regex {
    val normalized = pattern.replace('\\', '/').removePrefix("./")
    val expression = StringBuilder("^")
    var index = 0
    while (index < normalized.length) {
        val character = normalized[index]
        when {
            character == '*' && normalized.getOrNull(index + 1) == '*' -> {
                if (normalized.getOrNull(index + DOUBLE_WILDCARD_LENGTH) == '/') {
                    expression.append("(?:.*/)?")
                    index += DOUBLE_WILDCARD_WITH_SEPARATOR_LENGTH
                } else {
                    expression.append(".*")
                    index += 2
                }
            }

            character == '*' -> {
                expression.append("[^/]*")
                index++
            }

            character == '?' -> {
                expression.append("[^/]")
                index++
            }

            character in REGEX_SPECIAL_CHARACTERS -> {
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

internal fun isWithin(path: String, root: String): Boolean = path == root || path.startsWith("$root/")

private const val REGEX_SPECIAL_CHARACTERS = ".+()^$|{}[]"
private const val DOUBLE_WILDCARD_LENGTH = 2
private const val DOUBLE_WILDCARD_WITH_SEPARATOR_LENGTH = 3
