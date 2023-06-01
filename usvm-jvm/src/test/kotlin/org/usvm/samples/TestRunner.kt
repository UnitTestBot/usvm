package org.usvm.samples

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDatabase
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.TestInstance
import org.usvm.JcMachine
import org.usvm.JcTestSuite
import org.usvm.util.JcTestResolver
import org.usvm.util.allClasspath
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2
import kotlin.reflect.KFunction3
import kotlin.reflect.KFunction4
import kotlin.test.assertTrue


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TestRunner {
    inline fun <reified T, reified R> run(method: KFunction1<T, R>, vararg matchers: (T, R) -> Boolean) {
        internalCheck(T::class, method) { suite ->
            assertTrue(suite.tests.isNotEmpty())
            for ((idx, matcher) in matchers.withIndex()) {
                val matcherResult = suite.tests.any { test ->
                    val instance = (test.before.thisInstance as? T) ?: return@any false
                    val result = (test.result.getOrElse { return@any false } as? R) ?: return@any false
                    matcher(instance, result)
                }
                assertTrue(matcherResult, "Matcher $idx failed")
            }
        }
    }

    inline fun <reified T, reified A0, reified R> run(
        method: KFunction2<T, A0, R>,
        vararg matchers: (T, A0, R) -> Boolean,
    ) {
        internalCheck(T::class, method) { suite ->
            assertTrue(suite.tests.isNotEmpty())
            for ((idx, matcher) in matchers.withIndex()) {
                val matcherResult = suite.tests.any { test ->
                    val instance = (test.before.thisInstance as? T) ?: return@any false
                    val param0 = (test.before.parameters[0] as? A0) ?: return@any false
                    val result = (test.result.getOrElse { return@any false } as? R) ?: return@any false
                    matcher(instance, param0, result)
                }
                assertTrue(matcherResult, "Matcher $idx failed")
            }
        }
    }

    inline fun <reified T, reified A0, reified A1, reified R> run(
        method: KFunction3<T, A0, A1, R>,
        vararg matchers: (T, A0, A1, R) -> Boolean,
    ) {
        internalCheck(T::class, method) { suite ->
            assertTrue(suite.tests.isNotEmpty())
            for ((idx, matcher) in matchers.withIndex()) {
                val matcherResult = suite.tests.any { test ->
                    val instance = (test.before.thisInstance as? T) ?: return@any false
                    val param0 = (test.before.parameters[0] as? A0) ?: return@any false
                    val param1 = (test.before.parameters[1] as? A1) ?: return@any false
                    val result = (test.result.getOrElse { return@any false } as? R) ?: return@any false
                    matcher(instance, param0, param1, result)
                }
                assertTrue(matcherResult, "Matcher $idx failed")
            }
        }
    }

    inline fun <reified T, reified A0, reified A1, reified A2, reified R> run(
        method: KFunction4<T, A0, A1, A2, R>,
        vararg matchers: (T, A0, A1, A2, R) -> Boolean,
    ) {
        internalCheck(T::class, method) { suite ->
            assertTrue(suite.tests.isNotEmpty())
            for ((idx, matcher) in matchers.withIndex()) {
                val matcherResult = suite.tests.any { test ->
                    val instance = (test.before.thisInstance as? T) ?: return@any false
                    val param0 = (test.before.parameters[0] as? A0) ?: return@any false
                    val param1 = (test.before.parameters[1] as? A1) ?: return@any false
                    val param2 = (test.before.parameters[2] as? A2) ?: return@any false
                    val result = (test.result.getOrElse { return@any false } as? R) ?: return@any false
                    matcher(instance, param0, param1, param2, result)
                }
                assertTrue(matcherResult, "Matcher $idx failed")
            }
        }
    }

    inline fun <reified T, reified R> runWithException(
        method: KFunction1<T, R>,
        vararg matchers: (T, Result<R>) -> Boolean,
    ) {
        internalCheck(T::class, method) { suite ->
            assertTrue(suite.tests.isNotEmpty())
            for ((idx, matcher) in matchers.withIndex()) {
                val matcherResult = suite.tests.any { test ->
                    val instance = (test.before.thisInstance as? T) ?: return@any false
                    val result = (test.result.map { it as? R ?: return@any false })
                    matcher(instance, result)
                }
                assertTrue(matcherResult, "Matcher $idx failed")
            }
        }
    }

    inline fun <reified T, reified A0, reified R> runWithException(
        method: KFunction2<T, A0, R>,
        vararg matchers: (T, A0, Result<R>) -> Boolean,
    ) {
        internalCheck(T::class, method) { suite ->
            assertTrue(suite.tests.isNotEmpty())
            for ((idx, matcher) in matchers.withIndex()) {
                val matcherResult = suite.tests.any { test ->
                    if (test.before.thisInstance !is T ||
                        test.before.parameters[0] !is A0) {
                        return@any false
                    }

                    val instance = (test.before.thisInstance as T)
                    val param0 = (test.before.parameters[0] as A0)
                    val result = (test.result.map { it as? R ?: return@any false })
                    matcher(instance, param0, result)
                }
                assertTrue(matcherResult, "Matcher $idx failed")
            }
        }
    }

    inline fun <reified T, reified A0, reified A1, reified R> runWithException(
        method: KFunction3<T, A0, A1, R>,
        vararg matchers: (T, A0, A1, Result<R>) -> Boolean,
    ) {
        internalCheck(T::class, method) { suite ->
            assertTrue(suite.tests.isNotEmpty())
            for ((idx, matcher) in matchers.withIndex()) {
                val matcherResult = suite.tests.any { test ->
                    val instance = (test.before.thisInstance as? T) ?: return@any false
                    val param0 = (test.before.parameters[0] as? A0) ?: return@any false
                    val param1 = (test.before.parameters[1] as? A1) ?: return@any false
                    val result = (test.result.map { it as? R ?: return@any false })
                    matcher(instance, param0, param1, result)
                }
                assertTrue(matcherResult, "Matcher $idx failed")
            }
        }
    }

    inline fun <reified T, reified A0, reified A1, reified A2, reified R> runWithException(
        method: KFunction4<T, A0, A1, A2, R>,
        vararg matchers: (T, A0, A1, A2, Result<R>) -> Boolean,
    ) {
        internalCheck(T::class, method) { suite ->
            assertTrue(suite.tests.isNotEmpty())
            for ((idx, matcher) in matchers.withIndex()) {
                val matcherResult = suite.tests.any { test ->
                    if (test.before.thisInstance !is T ||
                        test.before.parameters[0] !is A0 ||
                        test.before.parameters[1] !is A1 ||
                        test.before.parameters[2] !is A2) {
                        return@any false
                    }
                    val instance = (test.before.thisInstance as T)
                    val param0 = (test.before.parameters[0] as A0)
                    val param1 = (test.before.parameters[1] as A1)
                    val param2 = (test.before.parameters[2] as A2)
                    val result = (test.result.map { it as? R ?: return@any false })
                    matcher(instance, param0, param1, param2, result)
                }
                assertTrue(matcherResult, "Matcher $idx failed")
            }
        }
    }

    fun internalCheck(targetClass: KClass<*>, targetMethod: KFunction<*>, onSuite: (JcTestSuite) -> Unit) {
        val jcClass = cp.findClass(requireNotNull(targetClass.qualifiedName)).toType()
        val jcMethod = jcClass.declaredMethods.first { it.name == targetMethod.name }

        val machine = JcMachine(cp)
        val states = machine.analyze(jcMethod)

        val tests = states.map { testResolver.resolve(jcMethod, it) }
        val suite = JcTestSuite(tests)

        onSuite(suite)
    }

    companion object {
        private val classpath = allClasspath.filter { it.name.contains("samples") }

        private val db: JcDatabase
        private val cp: JcClasspath

        init {
            val (db, cp) = runBlocking {
                val db = jacodb {
                    useProcessJavaRuntime()
                    loadByteCode(classpath)
                }
                db to db.classpath(classpath)
            }
            this.db = db
            this.cp = cp
        }


    }

    private val testResolver = JcTestResolver()
}