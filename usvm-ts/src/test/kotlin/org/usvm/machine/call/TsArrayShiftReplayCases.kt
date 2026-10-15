package org.usvm.machine.call

internal fun arrayShiftReplayCases(): List<ArrayShiftReplayCase> = buildList {
    for (type in listOf("any", "unknown")) {
        add(
            ArrayShiftReplayCase(
                name = "first $type read",
                parameters = "values: $type[]",
                maxResult = 6,
                body = """
                    if (values.length !== 1) return 0;
                    const removed = values.shift();
                    if (removed === 42) return 1;
                    if (removed === true) return 2;
                    if (removed === false) return 3;
                    if (removed === null) return 4;
                    if (removed === undefined) return 5;
                    return 6;
                """.trimIndent(),
            )
        )
        add(
            ArrayShiftReplayCase(
                name = "repeated mixed $type shifts",
                parameters = "values: $type[]",
                maxResult = 3,
                body = """
                    if (values.length !== 2) return 0;
                    const first = values.shift();
                    const second = values.shift();
                    if (first === 42 && second === true) return 1;
                    if (first === false && second === 17) return 2;
                    return 3;
                """.trimIndent(),
            )
        )
        add(
            ArrayShiftReplayCase(
                name = "read shifted $type tail",
                parameters = "values: $type[]",
                maxResult = 3,
                body = """
                    if (values.length !== 2) return 0;
                    values.shift();
                    if (values[0] === 42) return 1;
                    if (values[0] === true) return 2;
                    return 3;
                """.trimIndent(),
            )
        )
        add(
            ArrayShiftReplayCase(
                name = "overwrite $type then shift",
                parameters = "values: $type[], index: number",
                maxResult = 3,
                body = """
                    if (values.length !== 2 || (index !== 0 && index !== 1)) return 0;
                    values[index] = true;
                    const first = values.shift();
                    if (first === 42 && values[0] === true) return 1;
                    if (first === true && values[0] === 17) return 2;
                    return 3;
                """.trimIndent(),
            )
        )
    }
    add(
        ArrayShiftReplayCase(
            name = "typed pop payload remains usable as number and array index",
            parameters = "values: number[]",
            maxResult = 2,
            body = """
                if (values.length !== 1) return 0;
                const n = values.pop();
                if (n !== 1) return 1;
                const target = [10, 20];
                return Math.floor(n) === 1 && target[n] === 20 ? 2 : -1;
            """.trimIndent(),
        )
    )
    addAll(assertionAndWriteCases())
    addAll(storageOperationCases())
    addAll(pairCases())
    addAll(typedCases())
}

private fun assertionAndWriteCases(): List<ArrayShiftReplayCase> = buildList {
    add(
        ArrayShiftReplayCase(
            name = "number type assertions preserve runtime values",
            parameters = "value: number",
            maxResult = 1,
            body = """
                if (value !== 42) return 0;
                const asserted = value as unknown as string;
                return typeof asserted === 'number' && asserted === 42 ? 1 : -1;
            """.trimIndent(),
        )
    )
    add(
        ArrayShiftReplayCase(
            name = "boolean type assertions do not convert to numbers",
            parameters = "value: boolean",
            maxResult = 1,
            body = """
                if (!value) return 0;
                const asserted = value as unknown as number;
                return typeof asserted === 'boolean' && asserted === true ? 1 : -1;
            """.trimIndent(),
        )
    )
    add(
        ArrayShiftReplayCase(
            name = "truthy wrapped number retains its runtime kind through assertion and write",
            parameters = "values: number[]",
            maxResult = 2,
            body = """
                if (values.length !== 1) return 0;
                const item = values[0];
                if (!item) return 1;
                const asserted = item as unknown as number;
                const output = [0];
                output[0] = asserted;
                return output[0] === item ? 2 : -1;
            """.trimIndent(),
        )
    )
    add(
        ArrayShiftReplayCase(
            name = "typed array copies preserve wrapped numbers including NaN",
            parameters = "values: number[]",
            maxResult = 1,
            body = """
                if (values.length !== 2) return 0;
                values[0] = values[1];
                return values[0] === values[1] || values[0] !== values[0] ? 1 : -1;
            """.trimIndent(),
        )
    )
}

private fun storageOperationCases(): List<ArrayShiftReplayCase> = listOf("any", "unknown").flatMap { type ->
    copyCases(type) + mutationCases(type) + concatCases(type)
}.map { case -> case.copy(name = "compatibility storage: ${case.name}", compatibility = true) }

private fun copyCases(type: String): List<ArrayShiftReplayCase> = listOf(
    ArrayShiftReplayCase(
        name = "$type slice ignores an evaluated extra argument",
        parameters = "values: $type[]",
        maxResult = 1,
        body = """
            if (values.length !== 1) return 0;
            const alias = values;

            const copy = values.slice(0, 1, values.push(true));

            return copy.length === 1 && alias.length === 2 && alias[1] === true ? 1 : -1;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type slice preserves a deleted slot at a translated index",
        parameters = "values: $type[]",
        maxResult = 1,
        body = """
            if (values.length !== 1) return 0;
            const source = [7, 11, 22];
            delete source[1];

            const copy = source.slice(1, 3);

            return copy.length === 2 && copy[0] === undefined && copy[1] === 22 &&
                source[0] === 7 ? 1 : -1;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type slice() retains all runtime kinds",
        parameters = "values: $type[]",
        maxResult = 6,
        body = """
            if (values.length !== 1) return 0;
            const copy = values.slice();
            const value = copy[0];
            if (value === 42) return 1;
            if (value === true) return 2;
            if (value === false) return 3;
            if (value === null) return 4;
            if (value === undefined) return 5;
            return 6;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type slice().reverse() retains all runtime kinds",
        parameters = "values: $type[]",
        maxResult = 6,
        body = """
            if (values.length !== 1) return 0;
            const copy = values.slice().reverse();
            const value = copy[0];
            if (value === 42) return 1;
            if (value === true) return 2;
            if (value === false) return 3;
            if (value === null) return 4;
            if (value === undefined) return 5;
            return 6;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type sliced input mixed with appended wrapper",
        parameters = "values: $type[], index: number",
        maxResult = 4,
        body = """
            if (values.length !== 1) return 0;
            const i = Math.floor(index);
            if (!(i >= 0 && i <= 1)) return 0;
            const copy = values.slice();
            copy.push(true);
            const value = copy[i];
            if (i === 1 && value === true) return 1;
            if (i === 0 && value === 42) return 2;
            if (i === 0 && value === false) return 3;
            return 4;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type copied payload moves through two shifts",
        parameters = "values: $type[]",
        maxResult = 3,
        body = """
            if (values.length !== 2) return 0;
            const copy = values.slice();
            const first = copy.shift();
            const second = copy.shift();
            if (first === 42 && second === true) return 1;
            if (first === false && second === 17) return 2;
            return 3;
        """.trimIndent(),
    ),
)

private fun mutationCases(type: String): List<ArrayShiftReplayCase> = listOf(
    ArrayShiftReplayCase(
        name = "$type compatibility pop ignores evaluated extra arguments",
        parameters = "values: $type[]",
        maxResult = 1,
        body = """
            if (values.length !== 1) return 0;
            const alias = values;
            const removed = values.pop(values.push(true));
            return removed === true && alias.length === 1 ? 1 : -1;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type compatibility push accepts zero and three values",
        parameters = "values: $type[]",
        maxResult = 1,
        body = """
            if (values.length !== 1) return 0;
            const alias = values;
            const unchangedLength = values.push();
            const newLength = values.push(7, true, null);
            return unchangedLength === 1 && newLength === 4 && alias[1] === 7 &&
                alias[2] === true && alias[3] === null ? 1 : -1;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type compatibility unshift accepts zero and three values",
        parameters = "values: $type[]",
        maxResult = 1,
        body = """
            if (values.length !== 1) return 0;
            const alias = values;
            const unchangedLength = values.unshift();
            const newLength = values.unshift(7, true, null);
            return unchangedLength === 1 && newLength === 4 && alias[0] === 7 &&
                alias[1] === true && alias[2] === null ? 1 : -1;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type unshift preserves unread tail and pop kind",
        parameters = "values: $type[]",
        maxResult = 3,
        body = """
            if (values.length !== 1) return 0;
            const alias = values;
            values.unshift(true);
            const tail = alias.pop();
            if (alias[0] !== true || alias.length !== 1) return -1;
            if (tail === 42) return 1;
            if (tail === false) return 2;
            return 3;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type reverse permutes payloads and kinds",
        parameters = "values: $type[]",
        maxResult = 3,
        body = """
            if (values.length !== 2) return 0;
            const copy = values.slice();
            copy.reverse();
            if (copy[0] === 42 && copy[1] === true) return 1;
            if (copy[0] === false && copy[1] === 17) return 2;
            return 3;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type fill overrides input kind selectors",
        parameters = "values: $type[], index: number",
        maxResult = 4,
        body = """
            if (values.length !== 2) return 0;
            const i = Math.floor(index);
            if (!(i >= 0 && i <= 1)) return 0;
            values.fill(true, 1, 2);
            const value = values[i];
            if (i === 1 && value === true) return 1;
            if (i === 0 && value === 42) return 2;
            if (i === 0 && value === false) return 3;
            return 4;
        """.trimIndent(),
    ),
)

private fun concatCases(type: String): List<ArrayShiftReplayCase> = listOf(
    ArrayShiftReplayCase(
        name = "$type concat preserves deleted receiver slot and alias",
        parameters = "values: $type[]",
        maxResult = 1,
        body = """
            if (values.length !== 1) return 0;
            const source = [7, 11];
            const alias = source;
            delete source[1];

            const copy = source.concat([22], 33);

            return copy.length === 4 && copy[1] === undefined && copy[2] === 22 &&
                copy[3] === 33 && alias[1] === undefined && alias.length === 2 ? 1 : -1;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type concat preserves deleted argument slot at offset",
        parameters = "values: $type[]",
        maxResult = 1,
        body = """
            if (values.length !== 1) return 0;
            const source = [7];
            const other = [11, 22];
            delete other[0];

            const copy = source.concat(other);

            return copy.length === 3 && copy[0] === 7 && copy[1] === undefined &&
                copy[2] === 22 ? 1 : -1;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type concat copies unread input arrays",
        parameters = "left: $type[], right: $type[]",
        maxResult = 3,
        body = """
            if (left.length !== 1 || right.length !== 1) return 0;
            const copy = left.concat(right);
            const first = copy.shift();
            const second = copy.shift();
            if (first === 42 && second === true) return 1;
            if (first === false && second === 17) return 2;
            return 3;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type concat wraps scalar primitives",
        parameters = "values: $type[]",
        maxResult = 3,
        body = """
            if (values.length !== 1) return 0;
            const copy = values.concat(true);
            if (copy[1] !== true) return -1;
            if (copy[0] === 42) return 1;
            if (copy[0] === false) return 2;
            return 3;
        """.trimIndent(),
    ),
    ArrayShiftReplayCase(
        name = "$type empty pop returns undefined and retains length",
        parameters = "values: $type[]",
        maxResult = 1,
        body = """
            if (values.length !== 0) return 0;
            const result = values.pop();
            return result === undefined && values.length === 0 ? 1 : -1;
        """.trimIndent(),
    ),
)

private fun pairCases(): List<ArrayShiftReplayCase> = buildList {
    val literals = listOf("42", "true", "false", "null", "undefined", "'left'")
    for (type in listOf("any", "unknown")) {
        for (first in literals) {
            for (second in literals) {
                // Unconstrained string equality is not modeled yet; write string slots before shifting.
                val writes = listOf(first, second).mapIndexedNotNull { index, literal ->
                    if (literal == "'left'") "values[$index] = $literal;" else null
                }.joinToString(separator = "\n")
                val label = if (writes.isEmpty()) "input pair" else "pair with written string"
                val maxResult = if (first == "'left'" && second == "'left'") 1 else 2
                add(
                    ArrayShiftReplayCase(
                        name = "$type $label $first then $second",
                        parameters = "values: $type[]",
                        maxResult = maxResult,
                        body = """
                            if (values.length !== 2) return 0;
                            $writes
                            const first = values.shift();
                            const second = values.shift();
                            return first === $first && second === $second ? 1 : 2;
                        """.trimIndent(),
                    )
                )
            }
        }
    }
}

private fun typedCases(): List<ArrayShiftReplayCase> = buildList {
    for ((type, literal) in listOf("number" to "42", "boolean" to "true")) {
        add(
            ArrayShiftReplayCase(
                name = "homogeneous $type",
                parameters = "values: $type[]",
                maxResult = 2,
                body = """
                    if (values.length !== 2) return 0;
                    const first = values.shift();
                    return first === $literal && values[0] === $literal ? 1 : 2;
                """.trimIndent(),
            )
        )
    }
    add(
        ArrayShiftReplayCase(
            name = "written homogeneous string",
            parameters = "values: string[]",
            maxResult = 1,
            body = """
                if (values.length !== 2) return 0;
                values[0] = 'left';
                values[1] = 'right';
                return values.shift() === 'left' && values[0] === 'right' ? 1 : 2;
            """.trimIndent(),
        )
    )
    add(
        ArrayShiftReplayCase(
            name = "written symbolic union",
            parameters = "values: (number | boolean)[]",
            maxResult = 1,
            body = """
                if (values.length !== 2) return 0;
                values[0] = 42;
                values[1] = true;
                return values.shift() === 42 && values[0] === true ? 1 : 2;
            """.trimIndent(),
        )
    )
    for (type in listOf("number", "boolean")) {
        for (aliasType in listOf("any", "unknown")) {
            add(
                ArrayShiftReplayCase(
                    name = "symbolic $type array through $aliasType alias",
                    parameters = "values: $type[]",
                    maxResult = if (type == "number") 2 else 1,
                    body = """
                        if (values.length !== 2) return 0;
                        const alias: $aliasType[] = values;
                        const first = values[0];
                        const second = values[1];
                        return alias.shift() === first && values[0] === second && alias.length === 1 ? 1 : 2;
                    """.trimIndent(),
                )
            )
        }
    }
    add(
        ArrayShiftReplayCase(
            name = "symbolic object array preserves references",
            parameters = "values: ReplayElement[]",
            maxResult = 2,
            body = """
                if (values.length !== 2) return 0;
                const first = values[0];
                const second = values[1];
                if (first == null || second == null) return 2;
                return values.shift() === first && values[0] === second ? 1 : 3;
            """.trimIndent(),
        )
    )
}

internal data class ArrayShiftReplayCase(
    val name: String,
    val parameters: String,
    val maxResult: Int,
    val body: String,
    val compatibility: Boolean = false,
)
