package org.usvm.instrumentation.org.usvm.instrumentation.classloader

import allFields
import getFieldByName
import getFieldValue
import org.jacodb.api.*
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.*
import org.jacodb.impl.bytecode.JcClassOrInterfaceImpl
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.cfg.JcRawBool
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.cfg.util.isPrimitive
import org.jacodb.impl.fs.ClassSourceImpl
import org.jacodb.impl.types.AnnotationInfo
import org.jacodb.impl.types.MethodInfo
import org.jacodb.impl.types.ParameterInfo
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.instrumentation.JcInstructionTracer
import org.usvm.instrumentation.org.usvm.instrumentation.instrumentation.TraceHelper
import org.usvm.instrumentation.trace.collector.MockCollector
import org.usvm.instrumentation.util.*
import setFieldValue
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

class MockHelper(val jcClasspath: JcClasspath, val classLoader: WorkerClassLoader) {

    private val mockedClasses = hashMapOf<JcClassOrInterface, Int>()
    private val encodedMethods = hashMapOf<JcMethod, Long>()
    private val mockCache = hashMapOf<JcMethod, MethodNode>()
    private val traceHelper = TraceHelper(jcClasspath, MockCollector::class.java)

    private fun createGetMockValueMethodName(returnTypeName: TypeName) =
        when (returnTypeName) {
            jcClasspath.boolean.getTypename() -> "getBooleanMockValue"
            jcClasspath.byte.getTypename() -> "getByteMockValue"
            jcClasspath.short.getTypename() -> "getShortMockValue"
            jcClasspath.int.getTypename() -> "getIntMockValue"
            jcClasspath.long.getTypename() -> "getLongMockValue"
            jcClasspath.float.getTypename() -> "getFloatMockValue"
            jcClasspath.double.getTypename() -> "getDoubleMockValue"
            jcClasspath.char.getTypename() -> "getCharMockValue"
            else -> "getMockValue"
        }


    private fun addMockToMethod(jcClass: JcClassOrInterface, jcMethod: JcMethod, mockedMethodId: Long): MethodNode {
        val instList = jcMethod.rawInstList.toMutableList()
        val firstInst = instList.first()
        val mockBeginLabel = JcRawLabelInst(jcMethod, "#mockBeginGenerated0")
        val mockTypeName = jcMethod.returnType
        instList.insertBefore(firstInst, mockBeginLabel)
        val mockEndLabel =
            jcMethod.rawInstList.firstOrNull { it !is JcRawLineNumberInst }?.let { firstInstruction ->
                if (firstInstruction is JcRawLabelInst) {
                    firstInstruction
                } else {
                    JcRawLabelInst(jcMethod, "#mockEndGenerated0")
                }
            } ?: JcRawLabelInst(jcMethod, "#mockEndGenerated0")
        val isMockedLocalVar = JcRawLocalVar("%isMockedGenerated0", jcClasspath.boolean.getTypename())
        val isMockedStaticCallExpr =
            traceHelper.createMockCollectorCall("isMocked", mockedMethodId, JcRawThis(jcClass.typename))
        val isMockedAssignInst = JcRawAssignInst(jcMethod, isMockedLocalVar, isMockedStaticCallExpr)

        val ifCondition = JcRawEqExpr(jcClasspath.boolean.getTypename(), isMockedLocalVar, JcRawBool(false))
        val returnMockValueLabel = JcRawLabelInst(jcMethod, "#returnMockValueGenerated0")
        val ifInst = JcRawIfInst(jcMethod, ifCondition, mockEndLabel.ref, returnMockValueLabel.ref)

        val mockRetValueLocalVar =
            if (mockTypeName.isPrimitive) {
                JcRawLocalVar("%mockReturnValueGenerated0", mockTypeName)
            } else {
                JcRawLocalVar("%mockReturnValueGenerated0", jcClasspath.objectType.getTypename())
            }
        val mockRetValueVirtualCall = traceHelper.createMockCollectorCall(
            createGetMockValueMethodName(mockTypeName),
            mockedMethodId,
            JcRawThis(jcClass.typename)
        )
        val mockRetValueAssignInst = JcRawAssignInst(jcMethod, mockRetValueLocalVar, mockRetValueVirtualCall)
        if (mockTypeName.isPrimitive) {
            val returnMock = JcRawReturnInst(jcMethod, mockRetValueLocalVar)
            instList.insertBefore(
                firstInst,
                isMockedAssignInst,
                ifInst,
                returnMockValueLabel,
                mockRetValueAssignInst,
                returnMock
            )
        } else {
            val localVar = JcRawLocalVar("%mockReturnValueGenerated1", mockTypeName)
            val assignAndCastInst =
                JcRawAssignInst(jcMethod, localVar, JcRawCastExpr(mockTypeName, mockRetValueLocalVar))
            val returnMock = JcRawReturnInst(jcMethod, localVar)
            instList.insertBefore(
                firstInst,
                isMockedAssignInst,
                ifInst,
                returnMockValueLabel,
                mockRetValueAssignInst,
                assignAndCastInst,
                returnMock
            )
        }
        if (mockEndLabel.name == "mockEndGenerated0") instList.insertBefore(firstInst, mockEndLabel)
        return MethodNodeBuilder(jcMethod, instList).build()
    }

    private fun replaceMethodBodyToMock(
        jcClass: JcClassOrInterface,
        jcMethod: JcMethod,
        mockedMethodId: Long
    ): MethodNode {
        val insnList = jcMethod.rawInstList.instructions.toMutableList()
        val mockTypeName = jcMethod.returnType
        val localVar1 =
            if (mockTypeName.isPrimitive) {
                JcRawLocalVar("%mockReturnValueGenerated0", mockTypeName)
            } else {
                JcRawLocalVar("%mockReturnValueGenerated0", jcClasspath.objectType.getTypename())
            }
        val rtv = traceHelper.createMockCollectorCall(
            createGetMockValueMethodName(mockTypeName),
            mockedMethodId,
            JcRawThis(jcClass.typename)
        )
        val assignInst = JcRawAssignInst(jcMethod, localVar1, rtv)
        if (mockTypeName.isPrimitive) {
            val returnInst = JcRawReturnInst(jcMethod, localVar1)
            insnList.addAll(0, listOf(assignInst, returnInst))
        } else {
            val localVar2 = JcRawLocalVar("%mockReturnValueGenerated1", mockTypeName)
            val assignAndCastInst = JcRawAssignInst(jcMethod, localVar2, JcRawCastExpr(mockTypeName, localVar1))
            val returnInst = JcRawReturnInst(jcMethod, localVar2)
            insnList.addAll(0, listOf(assignInst, assignAndCastInst, returnInst))
        }
        jcMethod.rawInstList.forEach { insnList.remove(it) }
        return MethodNodeBuilder(jcMethod, JcInstListImpl(insnList))
            .build()
            .also { it.access = it.access and Opcodes.ACC_ABSTRACT.inv() }
    }

    private fun addMockInfoAndRedefineClass(
        jcClass: JcClassOrInterface,
        methods: List<JcMethod>
    ): Pair<Class<*>, Map<JcMethod, Long>> {
        val classNode = jcClass.asmNode()
        val asmMethods = classNode.methods
        for (jcMethod in methods) {
            if (jcMethod in mockCache) continue
            val encodedMethodId = encodeMethod(jcClass, jcMethod)
            val asmMethod = asmMethods.find { jcMethod.asmNode().isSameSignature(it) } ?: continue
            val mockedMethod = addMockToMethod(jcClass, jcMethod, encodedMethodId)
            asmMethods.replace(asmMethod, mockedMethod)
        }
        val jClass = jcClass.toJavaClass(classLoader)
        classLoader.redefineClass(jClass, classNode)
        val redefinedClass = classLoader.loadClass(jcClass.name)
        return redefinedClass to encodedMethods
    }

    private fun addMockInfoAndDefineNewClass(
        jcClass: JcClassOrInterface,
        methods: List<JcMethod>
    ): Pair<Class<*>, Map<JcMethod, Long>> {
        println("jcClass meth = ${jcClass.declaredMethods.map { it.name }}")
        val classNode = jcClass.asmNode()
        val mockedClassNode = ClassNode()
        classNode.accept(mockedClassNode)
        mockedClassNode.access = mockedClassNode.access and Opcodes.ACC_INTERFACE.inv() and Opcodes.ACC_ABSTRACT.inv()
        if (jcClass.isInterface) {
            mockedClassNode.interfaces.add(classNode.name)
        } else {
            mockedClassNode.superName = classNode.name
        }
        val asmMethods = mockedClassNode.methods
        val mockedObjectId = mockedClasses.getOrPut(jcClass) { 0 }
        mockedClasses[jcClass] = mockedObjectId + 1
        mockedClassNode.name = "${classNode.name}Mocked$mockedObjectId"
        for (jcMethod in methods) {
            val mockedMethod = mockCache.getOrPut(jcMethod) {
                val encodedMethodId = encodeMethod(jcClass, jcMethod)
                replaceMethodBodyToMock(jcClass, jcMethod, encodedMethodId)
            }
            val asmMethod = asmMethods.find { jcMethod.asmNode().isSameSignature(it) } ?: continue
            asmMethods.replace(asmMethod, mockedMethod)
        }
        //Load mockedClass
        val mockedClassJvmName = mockedClassNode.name.replace('/', '.')
        val newClass = classLoader.defineClass(mockedClassJvmName, mockedClassNode) ?: error("Mocking error")
        return newClass to encodedMethods
    }


    fun addMockInfoInBytecode(
        jcClass: JcClassOrInterface,
        methods: List<JcMethod>
    ): Pair<Class<*>, Map<JcMethod, Long>> =
        when {
            jcClass.isInterface || jcClass.isAbstract -> addMockInfoAndDefineNewClass(jcClass, methods)
            else -> addMockInfoAndRedefineClass(jcClass, methods)
        }


    /**
     *       0000 0000 0000 0000 0000 0000 0000 0000
     *       |    class id     |      methodId     |
     */
    private fun encodeMethod(jcClass: JcClassOrInterface, jcMethod: JcMethod) =
        encodedMethods.getOrPut(jcMethod) {
            val encodedClassId = JcInstructionTracer.encodeClass(jcClass).id
            val encodedMethodId = JcInstructionTracer.encodeMethod(jcClass, jcMethod).id
            (encodedClassId shl Byte.SIZE_BITS * 4) or encodedMethodId
        }

}

