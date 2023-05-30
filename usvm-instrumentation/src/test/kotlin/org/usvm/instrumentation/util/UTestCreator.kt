package org.usvm.instrumentation.util

import example.B
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.*
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.statement.*

object UTestCreator {

    object A {

        fun isA(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findMethodOrNull("isA") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())
            val arg1 = UTestIntExpression(1, jcClasspath.int)
            val statements = listOf(
                instance,
                arg1
            )
            return UTest(statements, UTestMethodCall(instance, jcMethod, listOf(arg1)))
        }

        fun indexOf(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findMethodOrNull("indexOf") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())
            val arg1 = UTestCreateArrayExpression(
                jcClasspath.int,
                UTestIntExpression(10, jcClasspath.int)
            )
            val setStatement = UTestArraySetStatement(
                arrayInstance = arg1,
                index = UTestIntExpression(5, jcClasspath.int),
                setValueExpression = UTestIntExpression(7, jcClasspath.int)
            )
            val arg2 = UTestIntExpression(7, jcClasspath.int)


            val statements = listOf(
                instance,
                arg1,
                setStatement
            )
            return UTest(statements, UTestMethodCall(instance, jcMethod, listOf(arg1, arg2)))
        }

        fun indexOfT(jcClasspath: JcClasspath): UTest {
            val jcClassA = jcClasspath.findClass<example.A>()
            val jcClassB = jcClasspath.findClass<example.B>()
            val jcMethod = jcClassA.findMethodOrNull("indexOfT")!!
            val constructorA = jcClassA.constructors.first()
            val constructorB = jcClassB.constructors.first()
            val instanceOfA = UTestConstructorCall(constructorA, listOf())
            val instanceOfB = UTestConstructorCall(constructorB, listOf())
            val setFieldOfB = UTestSetFieldStatement(
                instance = instanceOfB,
                field = jcClassB.findFieldOrNull("f")!!,
                value = UTestIntExpression(239, jcClasspath.int)
            )

            val instanceOfB2 = UTestConstructorCall(constructorB, listOf())
            val setFieldOfB2 = UTestSetFieldStatement(
                instance = instanceOfB2,
                field = jcClassB.findFieldOrNull("f")!!,
                value = UTestIntExpression(239, jcClasspath.int)
            )

            val arg1 = UTestCreateArrayExpression(jcClassB.toType(), UTestIntExpression(10, jcClasspath.int))
            val setStatement = UTestArraySetStatement(
                arrayInstance = arg1,
                index = UTestIntExpression(5, jcClasspath.int),
                setValueExpression = instanceOfB
            )

            val statements = listOf(
                instanceOfA,
                instanceOfB,
                setFieldOfB,
                instanceOfB2,
                setFieldOfB2,
                arg1,
                setStatement
            )
            return UTest(statements, UTestMethodCall(instanceOfA, jcMethod, listOf(arg1, instanceOfB2)))
        }

    }
}