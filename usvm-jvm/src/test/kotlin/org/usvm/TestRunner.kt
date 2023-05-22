package org.usvm

import kotlinx.coroutines.runBlocking
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
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
    val classpath = allClasspath.filter { it.name.contains("samples") }

    val db = runBlocking {
        jacodb {
            useProcessJavaRuntime()
            loadByteCode(classpath)
        }
    }
    val cp = runBlocking { db.classpath(classpath) }

    val testResolver = JcTestResolver()

    inline fun <reified T, reified R> run(method: KFunction1<T, R>, vararg matchers: (T, R) -> Boolean) {
        internalCheck(T::class, method) {
            assertTrue(it.tests.isNotEmpty())
            for (matcher in matchers) {
                it.tests.any { test ->
                    val instance = (test.before.thisInstance as? T) ?: return@any false
                    val result = (test.result as? R) ?: return@any false
                    matcher(instance, result)
                }
            }
        }
    }

    inline fun <reified T, reified A0, reified R> run(
        method: KFunction2<T, A0, R>,
        vararg matchers: (T, A0, R) -> Boolean,
    ) {
        internalCheck(T::class, method) {
            assertTrue(it.tests.isNotEmpty())
            for (matcher in matchers) {
                it.tests.any { test ->
                    val instance = (test.before.thisInstance as? T) ?: return@any false
                    val param0 = (test.before.parameters[0] as? A0) ?: return@any false
                    val result = (test.result as? R) ?: return@any false
                    matcher(instance, param0, result)
                }
            }
        }
    }

    inline fun <reified T, reified A0, reified A1, reified R> run(
        method: KFunction3<T, A0, A1, R>,
        vararg matchers: (T, A0, A1, R) -> Boolean,
    ) {
        internalCheck(T::class, method) {
            assertTrue(it.tests.isNotEmpty())
            for (matcher in matchers) {
                it.tests.any { test ->
                    val instance = (test.before.thisInstance as? T) ?: return@any false
                    val param0 = (test.before.parameters[0] as? A0) ?: return@any false
                    val param1 = (test.before.parameters[1] as? A1) ?: return@any false
                    val result = (test.result as? R) ?: return@any false
                    matcher(instance, param0, param1, result)
                }
            }
        }
    }

    inline fun <reified T, reified A0, reified A1, reified A2, reified R> run(
        method: KFunction4<T, A0, A1, A2, R>,
        vararg matchers: (T, A0, A1, A2, R) -> Boolean,
    ) {
        internalCheck(T::class, method) {
            assertTrue(it.tests.isNotEmpty())
            for (matcher in matchers) {
                it.tests.any { test ->
                    val instance = (test.before.thisInstance as? T) ?: return@any false
                    val param0 = (test.before.parameters[0] as? A0) ?: return@any false
                    val param1 = (test.before.parameters[1] as? A1) ?: return@any false
                    val param2 = (test.before.parameters[2] as? A2) ?: return@any false
                    val result = (test.result as? R) ?: return@any false
                    matcher(instance, param0, param1, param2, result)
                }
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

    @AfterAll
    fun close() {
        cp.close()
        db.close()
    }
}