package org.usvm.instrumentation.util

import example.C
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.*
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import java.lang.IllegalArgumentException
import kotlin.random.Random

object UTestCreator {

    object A {

        fun isA(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findMethodOrNull("isA") ?: error("Cant find method isA in class A")
            val constructor = jcClass.constructors.find { it.parameters.isEmpty() }!!
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

        fun indexOfWithIf(jcClasspath: JcClasspath): UTest {
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
            val getArrEl = UTestArrayGetExpression(
                arrayInstance = arg1,
                index = UTestIntExpression(5, jcClasspath.int)
            )
            val arg2 = UTestIntExpression(1, jcClasspath.int)
            val ifExpr = UTestConditionExpression(
                ConditionType.EQ,
                getArrEl,
                UTestIntExpression(7, jcClasspath.int),
                listOf(
                    UTestArraySetStatement(
                        arrayInstance = arg1,
                        index = UTestIntExpression(5, jcClasspath.int),
                        setValueExpression = UTestIntExpression(1, jcClasspath.int)
                    )
                ),
                listOf(
                    UTestArraySetStatement(
                        arrayInstance = arg1,
                        index = UTestIntExpression(5, jcClasspath.int),
                        setValueExpression = UTestIntExpression(2, jcClasspath.int)
                    )
                )
            )
            val statements = listOf(
                instance,
                arg1,
                setStatement,
                ifExpr
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

        fun javaStdLibCall(jcClasspath: JcClasspath): UTest {
            val jcClassA = jcClasspath.findClass<example.A>()
            val jcMethod = jcClassA.declaredMethods.find { it.name == "staticJavaStdLibCall" }!!
            val staticMethodCall = UTestStaticMethodCall(jcMethod, listOf())
            return UTest(listOf(), staticMethodCall)
        }

    }

    object Arrays {

        fun checkAllSamePoints(jcClasspath: JcClasspath): UTest {
            val jcClassArrays = jcClasspath.findClass<example.Arrays>()
            val jcClassPoint = jcClassArrays.innerClasses.find { it.simpleName == "Arrays\$Point" }!!
            val jcMethod = jcClassArrays.declaredMethods.find { it.name == "checkAllSamePoints" }!!
            val jcClassPointConstructor = jcClassPoint.constructors.first()
            val pointList = buildList {
                repeat(Random.nextInt(2, 5)) {
                    val xCoor = UTestIntExpression(Random.nextInt(1, 10), jcClasspath.int)
                    val yCoor = UTestIntExpression(Random.nextInt(1, 10), jcClasspath.int)
                    add(UTestConstructorCall(jcClassPointConstructor, listOf(xCoor, yCoor)))
                }
            }
            val pointListArray =
                UTestCreateArrayExpression(jcClassPoint.toType(), UTestIntExpression(pointList.size, jcClasspath.int))
            val arraySetStmts = buildList {
                for ((i, p) in pointList.withIndex()) {
                    val index = UTestIntExpression(i, jcClasspath.int)
                    add(UTestArraySetStatement(pointListArray, index, p))
                }
            }
            val jcArraysInstance = UTestConstructorCall(jcClassArrays.constructors.first(), listOf())
            val methodCall = UTestMethodCall(jcArraysInstance, jcMethod, listOf(pointListArray))
            val initStatements = pointList + listOf(pointListArray) + arraySetStmts + listOf(jcArraysInstance)
            return UTest(initStatements, methodCall)
        }

    }

    object Throwables {

        fun getRootCause(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass("com.google.common.base.Throwables")
            val jcMethod = jcClass.declaredMethods.find { it.name == "getRootCause" }!!
            val jcIllegalArgumentExceptionClass = jcClasspath.findClass<IllegalArgumentException>()
            val arg1 = UTestConstructorCall(jcIllegalArgumentExceptionClass.constructors.first(), listOf())
            val methodCall = UTestStaticMethodCall(jcMethod, listOf(arg1))
            return UTest(listOf(arg1), methodCall)
        }
    }

    object C {

        fun lol(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.C>()
            val jcMethod = jcClass.declaredMethods.find { it.name == "lol" }!!
            return UTest(listOf(), UTestStaticMethodCall(jcMethod, listOf()))
        }
    }
}