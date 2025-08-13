package org.usvm.api

/**
 * Generates a TypeScript test (Jest format) from a TsTest instance.
 */
object TsTestTypeScriptGenerator {
    fun generateTest(tsTest: TsTest): String {
        val methodName = tsTest.method.name
        val enclosingClassName = tsTest.method.enclosingClass?.name
        val before = tsTest.before
        val returnValue = tsTest.returnValue

        val paramsCode = before.parameters.map { valueToTs(it) }
        val paramList = paramsCode.joinToString(", ")

        val assertion = "expect(result).toEqual(${valueToTs(returnValue)});"
        val testTitle = enclosingClassName?.let { "$it.$methodName" } ?: methodName

        val body = if (enclosingClassName != null) {
            // Decide dynamically whether it's an instance or static method by inspecting prototype
            """
    let result;
    if (typeof $enclosingClassName !== 'undefined' && typeof $enclosingClassName.prototype !== 'undefined' && Object.prototype.hasOwnProperty.call($enclosingClassName.prototype, '$methodName')) {
        const __inst = new $enclosingClassName();
        result = __inst.$methodName($paramList);
    } else {
        result = $enclosingClassName.$methodName($paramList);
    }
    $assertion
            """.trimIndent()
        } else {
            // top-level function
            """
    const result = $methodName($paramList);
    $assertion
            """.trimIndent()
        }

        return """
test('$testTitle', () => {
$body
});
"""
    }

    private fun valueToTs(value: TsTestValue?, className: String? = null): String = when (value) {
        is TsTestValue.TsNumber -> when (value) {
            is TsTestValue.TsNumber.TsDouble -> value.number.toString()
            is TsTestValue.TsNumber.TsInteger -> value.number.toString()
        }
        is TsTestValue.TsBigInt -> value.value
        is TsTestValue.TsBoolean -> value.value.toString()
        is TsTestValue.TsString -> "\"${value.value.replace("\"", "\\\"")}\""
        is TsTestValue.TsNull -> "null"
        is TsTestValue.TsUndefined -> "undefined"
        is TsTestValue.TsAny -> "/* any */"
        is TsTestValue.TsUnknown -> "/* unknown */"
        is TsTestValue.TsException -> "/* exception */"
        is TsTestValue.TsArray<*> -> value.values.joinToString(prefix = "[", postfix = "]") { valueToTs(it) }
        is TsTestValue.TsClass -> {
            // Represent as plain object literal; we don't attempt constructor args
            val props = value.properties.entries.joinToString(", ") { (k, v) -> "$k: ${valueToTs(v)}" }
            "{ $props }"
        }
        null -> "undefined"
    }

    /** Strip a subset of TypeScript type annotations so code can run directly in Node without transpilation. */
    fun stripTypeAnnotations(src: String): String {
        return src
            // parameter / property annotations: identifier: Type
            .replace(Regex("([a-zA-Z_\\$][\\w\\$]*)\\s*:\\s*(boolean|number|string|any|unknown|object|void|never|bigint)"), "$1")
            // method return type: ) : Type {  -> ) {
            .replace(Regex("\\)\\s*:\\s*[a-zA-Z_\\$][\\w\\$]*\\s*\\{"), "){ ")
    }

    /** Build a self-contained JS harness with provided (already generated) test snippets. */
    fun buildHarness(originalTsSource: String, testBodies: List<String>): String {
        val stripped = stripTypeAnnotations(originalTsSource)
        val testsConcatenated = testBodies.joinToString("\n\n")
        return """
// Auto-generated test harness
// ---------------------------
// Original (stripped) source:
$stripped

const __results = [];
function expect(actual){ return { toEqual(expected){ const a = JSON.stringify(actual); const e = JSON.stringify(expected); if(a !== e){ throw new Error(`Assertion failed: ${'$'}{a} !== ${'$'}{e}`);} } } }
function test(name, fn){ try { fn(); __results.push({name, status: 'passed'}); console.log(`PASSED: ${'$'}{name}`);} catch(e){ __results.push({name, status: 'failed', error: e && e.message || String(e)}); } }

$testsConcatenated

let failed = __results.filter(r => r.status === 'failed');
for (const r of failed) {
  console.error(`FAILED: ${'$'}{r.name} :: ${'$'}{r.error}`);
}
if (failed.length === 0) {
  console.log('ALL_PASSED');
} else {
  process.exitCode = 1;
}
""".trimIndent()
    }
}
