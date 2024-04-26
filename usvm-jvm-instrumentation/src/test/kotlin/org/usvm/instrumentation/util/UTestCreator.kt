package org.usvm.instrumentation.util

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.*
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import java.lang.IllegalArgumentException
import kotlin.random.Random

object UTestCreator {

    object A {

        fun isA(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("isA") ?: error("Cant find method isA in class A")
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
            val jcMethod = jcClass.findDeclaredMethodOrNull("indexOf") ?: error("Cant find method indexOf in class A")
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

        fun exception(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("exception") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())
            return UTest(listOf(), UTestMethodCall(instance, jcMethod, listOf()))
        }

        fun getNumberOfClassConstructors(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("getNumberOfClassConstructors") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())
            val arg1 = UTestClassExpression(jcClasspath.findTypeOrNull<example.A>()!!)
            return UTest(listOf(), UTestMethodCall(instance, jcMethod, listOf(arg1)))
        }


        fun indexOfWithIf(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("indexOf") ?: error("Cant find method indexOf in class A")
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
            val ifExpr = UTestBinaryConditionStatement(
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
            val jcMethod = jcClassA.findDeclaredMethodOrNull("indexOfT")!!
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

        fun methodWithBug(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("methodWithBug") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())
            return UTest(listOf(), UTestMethodCall(instance, jcMethod, listOf()))
        }

        fun mock(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("mock") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())

            val jcMockClass = jcClasspath.findClass<example.MockClass>()

            val mockedMethod1 = jcMockClass.declaredMethods.find { it.name == "getI" }!!
            val mockedMethodRetValue1 = UTestIntExpression(239, jcClasspath.int)
            val mockedMethod2 = jcMockClass.declaredMethods.find { it.name == "getStr" }!!
            val mockedMethodRetValue2 = UTestStringExpression("a", jcClasspath.stringType())

            val mockedField1 = jcMockClass.declaredFields.find { it.name == "intField" }!!
            val mockedFieldValue1 = UTestIntExpression(1, jcClasspath.int)
            val mockedField2 = jcMockClass.declaredFields.find { it.name == "stringField" }!!
            val mockedFieldValue2 = UTestStringExpression("a", jcClasspath.stringType())
            val mockedMethods = mapOf(
                mockedMethod1 to listOf(mockedMethodRetValue1),
                mockedMethod2 to listOf(mockedMethodRetValue2)
            )
            val mockedFields = mapOf(
                mockedField1 to mockedFieldValue1,
                mockedField2 to mockedFieldValue2
            )
            val argMock = UTestMockObject(jcMockClass.toType(), mockedFields, mockedMethods)
            return UTest(listOf(), UTestMethodCall(instance, jcMethod, listOf(argMock)))
        }

        fun mockMultiple(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("multipleMock") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())

            val jcMockClass = jcClasspath.findClass<example.MockClass>()

            val mockedMethod1 = jcMockClass.declaredMethods.find { it.name == "getI" }!!
            val mockedMethodRetValue1 = UTestIntExpression(238, jcClasspath.int)
            val mockedMethodRetValue2 = UTestIntExpression(1, jcClasspath.int)

            val mockedMethods = mapOf(
                mockedMethod1 to listOf(mockedMethodRetValue1, mockedMethodRetValue2),
            )

            val argMock = UTestMockObject(jcMockClass.toType(), mapOf(), mockedMethods)
            return UTest(listOf(), UTestMethodCall(instance, jcMethod, listOf(argMock)))
        }

        fun mockStaticMethod(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("staticMock") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())

            val jcMockClass = jcClasspath.findClass<example.MockClass>()

            val mockedMethod1 = jcMockClass.declaredMethods.find { it.name == "staticMock" }!!
            val mockedMethodRetValue1 = UTestIntExpression(239, jcClasspath.int)

            val mockedMethods = mapOf(
                mockedMethod1 to listOf(mockedMethodRetValue1)
            )

            val mock = UTestMockObject(jcMockClass.toType(), mapOf(), mockedMethods)
            return UTest(listOf(mock), UTestMethodCall(instance, jcMethod, listOf()))
        }

        fun mockInterface(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("mockInterface") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())

            val jcMockClass = jcClasspath.findClass<example.MockInterface>()
            val mockedMethod1 = jcMockClass.declaredMethods.find { it.name == "intMock" }!!
            val mockedMethodRetValue1 = UTestIntExpression(238, jcClasspath.int)
            val mockedMethod2 = jcMockClass.declaredMethods.find { it.name == "strMock" }!!
            val mockedMethodRetValue2 = UTestStringExpression("a", jcClasspath.stringType())
            val mockedMethods = mapOf(
                mockedMethod1 to listOf(mockedMethodRetValue1),
                mockedMethod2 to listOf(mockedMethodRetValue2)
            )
            val argMock = UTestMockObject(jcMockClass.toType(), mapOf(), mockedMethods)
            return UTest(listOf(), UTestMethodCall(instance, jcMethod, listOf(argMock)))
        }

        fun mockRandom(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("mockRandom") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())

            val jcMockClass = jcClasspath.findClass<java.util.Random>()
            val mockMethod = jcMockClass.declaredMethods.find { it.name == "nextInt" && it.parameters.isEmpty() }!!
            val mockRes = UTestIntExpression(239, jcClasspath.int)
            val argMock = UTestGlobalMock(jcMockClass.toType(), mapOf(), mapOf(mockMethod to listOf(mockRes)))
            return UTest(listOf(argMock), UTestMethodCall(instance, jcMethod, listOf()))
        }

        fun mockAbstractClass(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("mockAbstractClass") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())

            val jcMockClass = jcClasspath.findClass<example.MockAbstractClass>()

            val mockedMethod1 = jcMockClass.declaredMethods.find { it.name == "getI" }!!
            val mockedMethodRetValue1 = UTestIntExpression(239, jcClasspath.int)
            val mockedMethod2 = jcMockClass.declaredMethods.find { it.name == "getStr" }!!
            val mockedMethodRetValue2 = UTestStringExpression("a", jcClasspath.stringType())

            val mockedField1 = jcMockClass.declaredFields.find { it.name == "intField" }!!
            val mockedFieldValue1 = UTestIntExpression(1, jcClasspath.int)
            val mockedField2 = jcMockClass.declaredFields.find { it.name == "stringField" }!!
            val mockedFieldValue2 = UTestStringExpression("a", jcClasspath.stringType())
            val mockedMethods = mapOf(
                mockedMethod1 to listOf(mockedMethodRetValue1),
                mockedMethod2 to listOf(mockedMethodRetValue2)
            )
            val mockedFields = mapOf(
                mockedField1 to mockedFieldValue1,
                mockedField2 to mockedFieldValue2
            )
            val argMock = UTestMockObject(jcMockClass.toType(), mockedFields, mockedMethods)
            return UTest(listOf(), UTestMethodCall(instance, jcMethod, listOf(argMock)))
        }

        fun mockAbstractClass1(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("mockAbstractClass1") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())

            val jcMockClass = jcClasspath.findClass<example.MockAbstractClass>()

            val mockedMethod1 = jcMockClass.declaredMethods.find { it.name == "getI" }!!
            val mockedMethodRetValue1 = UTestIntExpression(1, jcClasspath.int)
            val mockedMethod2 = jcMockClass.declaredMethods.find { it.name == "getStr" }!!
            val mockedMethodRetValue2 = UTestStringExpression("a", jcClasspath.stringType())

            val mockedField2 = jcMockClass.declaredFields.find { it.name == "stringField" }!!
            val mockedFieldValue2 = UTestStringExpression("a", jcClasspath.stringType())
            val mockedMethods = mapOf(
                mockedMethod1 to listOf(mockedMethodRetValue1),
                mockedMethod2 to listOf(mockedMethodRetValue2)
            )
            val mockedFields = mapOf(
                mockedField2 to mockedFieldValue2
            )
            val argMock = UTestMockObject(jcMockClass.toType(), mockedFields, mockedMethods)
            return UTest(listOf(), UTestMethodCall(instance, jcMethod, listOf(argMock)))
        }

        fun mockInterfaceWithDefaultMock(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("mockInterface") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())

            val jcMockClass = jcClasspath.findClass<example.MockInterface>()
            val mockedMethod1 = jcMockClass.declaredMethods.find { it.name == "intMock" }!!
            val mockedMethodRetValue1 = UTestIntExpression(240, jcClasspath.int)
            val mockedMethod2 = jcMockClass.declaredMethods.find { it.name == "strMock" }!!
            val mockedMethodRetValue2 = UTestStringExpression("a", jcClasspath.stringType())
            val mockedMethod3 = jcMockClass.declaredMethods.find { it.name == "intMockDefault" }!!
            val mockedMethodRetValue3 = UTestIntExpression(-1, jcClasspath.int)
            val mockedMethods = mapOf(
                mockedMethod1 to listOf(mockedMethodRetValue1),
                mockedMethod2 to listOf(mockedMethodRetValue2),
                mockedMethod3 to listOf(mockedMethodRetValue3)
            )
            val argMock = UTestMockObject(jcMockClass.toType(), mapOf(), mockedMethods)
            return UTest(listOf(), UTestMethodCall(instance, jcMethod, listOf(argMock)))
        }

        fun arithmeticOperation(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.A>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("returnField") ?: error("Cant find method indexOf in class A")
            val constructor = jcClass.constructors.first()
            val instance = UTestConstructorCall(constructor, listOf())
            val intExpr = UTestIntExpression(427, jcClasspath.int)
            val field = jcClass.declaredFields.find { it.name == "field" }!!
            val fieldValue = UTestGetFieldExpression(instance, field)
            val arithExpr = UTestArithmeticExpression(ArithmeticOperationType.SUB, fieldValue, intExpr, jcClasspath.int)
            val newFieldValue = UTestSetFieldStatement(instance, field, arithExpr)

            val statements = listOf(
                instance,
                newFieldValue
            )
            return UTest(statements, UTestMethodCall(instance, jcMethod, listOf()))
        }
    }

    object AnnotationsEx {
        fun getSelfAnnotationCount(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.AnnotationsEx>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("getSelfAnnotationCount")
                ?: error("Cant find method getSelfAnnotationCount in class AnnotationsEx")
            val instance = UTestNullExpression(jcClass.toType())
            val statements = emptyList<UTestInst>()
            return UTest(statements, UTestMethodCall(instance, jcMethod, emptyList()))
        }

        fun getAnnotationDefaultValue(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.AnnotationsEx>()
            val jcMethod = jcClass.findDeclaredMethodOrNull("getAnnotationDefaultValue")
                ?: error("Cant find method getAnnotationDefaultValue in class AnnotationsEx")
            val instance = UTestNullExpression(jcClass.toType())
            val statements = emptyList<UTestInst>()
            return UTest(statements, UTestMethodCall(instance, jcMethod, emptyList()))
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

    object Doubles {

        fun join(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass("com.google.common.primitives.Doubles")
            val jcMethod = jcClass.declaredMethods.find { it.name == "join" }!!
            val separator = UTestStringExpression(",", jcClasspath.stringType())
            val doubles = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
            val doubleArray = UTestCreateArrayExpression(jcClasspath.double, UTestIntExpression(5, jcClasspath.int))
            val listInitializer = List(5) {
                UTestArraySetStatement(
                    doubleArray,
                    UTestIntExpression(it, jcClasspath.int),
                    UTestDoubleExpression(doubles[it], jcClasspath.double)
                )
            }
            val callMethod = UTestStaticMethodCall(jcMethod, listOf(separator, doubleArray))
            return UTest(listOf(doubleArray) + listInitializer, callMethod)
        }
    }

    object Singleton {
        fun addToArray(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass("example.Singleton")
            val addToArrayJcMethod = jcClass.declaredMethods.find { it.name == "addToArray" }!!
            val getInstanceJcMethod = jcClass.declaredMethods.find { it.name == "getInstance" }!!
            val arg1 = UTestIntExpression(1, jcClasspath.int)
            val singletonInstance = UTestStaticMethodCall(getInstanceJcMethod, listOf())
            val addToArrayMethodCall = UTestMethodCall(singletonInstance, addToArrayJcMethod, listOf(arg1))
            return UTest(listOf(), addToArrayMethodCall)
        }
    }

    object NestedClass {
        fun getB(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass("example.ClassWithNestedClasses\$A\$B")
            val jcMethod = jcClass.declaredMethods.find { it.name == "getB" }!!
            val jcClassInstance =
                UTestConstructorCall(jcClass.constructors.first(), listOf(UTestIntExpression(1, jcClasspath.int)))
            val jcMethodCall = UTestMethodCall(jcClassInstance, jcMethod, listOf())
            return UTest(listOf(), jcMethodCall)
        }
    }

    object SleepingClass {
        fun sleepFor(jcClasspath: JcClasspath, timeInMillis: Long): UTest {
            val jcClass = jcClasspath.findClass("example.SleepingClass")
            val jcMethod = jcClass.declaredMethods.find { it.name == "sleepFor" }!!
            val jcMethodCall = UTestStaticMethodCall(jcMethod, listOf(UTestLongExpression(timeInMillis, jcClasspath.long)))
            return UTest(listOf(), jcMethodCall)
        }
    }

    object Ex1{

        fun nestedDescriptors(jcClasspath: JcClasspath): UTest {
            val jcClass1 = jcClasspath.findClass("example.Ex1")
            val jcClass2 = jcClasspath.findClass("example.Ex2")
            val jcClass3 = jcClasspath.findClass("example.Ex3")
            val lolMethod = jcClass1.declaredMethods.find { it.name == "lol" }!!
            val c1 = jcClass1.constructors.first()
            val c2 = jcClass2.constructors.first()
            val c3 = jcClass3.constructors.first()

            val instance1 = UTestConstructorCall(c1, listOf())
            val instance3 = UTestConstructorCall(c3, listOf())
            val instance2 = UTestConstructorCall(c2, listOf(instance3))

            val methodCall = UTestMethodCall(instance1, lolMethod, listOf(instance2))
            return UTest(listOf(), methodCall)
        }
    }

    object StaticInterfaceMethodCall {
        fun callStaticInterfaceMethod(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass<example.StaticInterfaceMethodCall>()
            val jcMethod = jcClass.declaredMethods.find { it.name == "callStaticInterfaceMethod" }!!
            return UTest(listOf(), UTestStaticMethodCall(jcMethod, listOf()))
        }
    }
    
    object ParentStaticFieldUser {
        fun getParentStaticField(jcClasspath: JcClasspath): UTest {
            val jcClass = jcClasspath.findClass("example.ParentStaticFieldUser")
            val jcMethod = jcClass.declaredMethods.find { it.name == "getParentStaticField" }!!

            return UTest(listOf(), UTestStaticMethodCall(jcMethod, emptyList()))
        }
    }
}