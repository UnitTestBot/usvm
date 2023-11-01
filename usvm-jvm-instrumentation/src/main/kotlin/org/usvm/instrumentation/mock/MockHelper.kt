package org.usvm.instrumentation.mock

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.TypeName
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.*
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.cfg.JcRawBool
import org.jacodb.impl.cfg.JcRawString
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.cfg.util.isPrimitive
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.collector.trace.MockCollector
import org.usvm.instrumentation.instrumentation.JcInstructionTracer
import org.usvm.instrumentation.instrumentation.TraceHelper
import org.usvm.instrumentation.util.*

class MockHelper(val jcClasspath: JcClasspath, val classLoader: WorkerClassLoader) {

    //Using for not remock methods of usual classes
    val mockCache = hashMapOf<JcMethod, Long>()

    private val traceHelper = TraceHelper(jcClasspath, MockCollector::class.java)

    private fun createGetMockValueMethodName(returnTypeName: TypeName) = when (returnTypeName) {
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

    private fun addMockInvocationInJcdbInstructions(
        jcClass: JcClassOrInterface,
        jcMethod: JcMethod,
        mockedMethodId: Long,
        isGlobalMock: Boolean
    ): List<JcRawInst> {
        val newInstList = mutableListOf<JcRawInst>()
        val mockBeginLabel = JcRawLabelInst(jcMethod, MOCK_BEGIN)
        val mockTypeName = jcMethod.returnType
        newInstList.add(mockBeginLabel)
        val mockEndLabel = jcMethod.rawInstList
            .firstOrNull { it !is JcRawLineNumberInst }
            ?.let { firstInstruction ->
                if (firstInstruction is JcRawLabelInst) {
                    firstInstruction
                } else {
                    JcRawLabelInst(jcMethod, MOCK_END)
                }
            } ?: JcRawLabelInst(jcMethod, MOCK_END)
        val isMockedLocalVar = JcRawLocalVar(IS_MOCKED, jcClasspath.boolean.getTypename())
        val jcThisReference =
            if (jcMethod.isStatic || isGlobalMock) {
                JcRawNullConstant(jcClass.typename)
            } else {
                JcRawThis(jcClass.typename)
            }
        val isMockedStaticCallExpr =
            if (isGlobalMock) {
                traceHelper.createMockCollectorIsInExecutionCall()
            } else {
                traceHelper.createMockCollectorCall(IS_MOCKED_FUN_NAME, mockedMethodId, jcThisReference)
            }
        val isMockedAssignInst = JcRawAssignInst(jcMethod, isMockedLocalVar, isMockedStaticCallExpr)

        val ifCondition = JcRawEqExpr(jcClasspath.boolean.getTypename(), isMockedLocalVar, JcRawBool(false))
        val returnMockValueLabel = JcRawLabelInst(jcMethod, RETURN_MOCK_LABEL)
        val ifInst = JcRawIfInst(jcMethod, ifCondition, mockEndLabel.ref, returnMockValueLabel.ref)

        val mockRetValueLocalVar = if (mockTypeName.isPrimitive) {
            JcRawLocalVar(MOCK_RETURN_VALUE_0, mockTypeName)
        } else {
            JcRawLocalVar(MOCK_RETURN_VALUE_0, jcClasspath.objectType.getTypename())
        }
        val mockRetValueVirtualCall = traceHelper.createMockCollectorCall(
            createGetMockValueMethodName(mockTypeName), mockedMethodId, jcThisReference
        )
        val mockRetValueAssignInst = JcRawAssignInst(jcMethod, mockRetValueLocalVar, mockRetValueVirtualCall)
        if (mockTypeName.isPrimitive) {
            val returnMock = JcRawReturnInst(jcMethod, mockRetValueLocalVar)
            newInstList.addAll(
                listOf(isMockedAssignInst, ifInst, returnMockValueLabel, mockRetValueAssignInst, returnMock)
            )
        } else {
            val localVar = JcRawLocalVar(MOCK_RETURN_VALUE_1, mockTypeName)
            val assignAndCastInst =
                JcRawAssignInst(jcMethod, localVar, JcRawCastExpr(mockTypeName, mockRetValueLocalVar))
            val returnMock = JcRawReturnInst(jcMethod, localVar)
            newInstList.addAll(
                listOf(
                    isMockedAssignInst,
                    ifInst,
                    returnMockValueLabel,
                    mockRetValueAssignInst,
                    assignAndCastInst,
                    returnMock
                )
            )
        }
        if (mockEndLabel.name == MOCK_END) newInstList.add(mockEndLabel)
        return newInstList
    }

    private fun throwExceptionInJcdbInstructions(jcMethod: JcMethod): List<JcRawInst> {
        val jcExceptionClass = jcClasspath.findClass<java.lang.IllegalStateException>()
        val localVar = JcRawLocalVar(NOT_MOCKED, jcExceptionClass.typename)
        val newExceptionInst = JcRawNewExpr(jcExceptionClass.typename)
        val assignInst = JcRawAssignInst(jcMethod, localVar, newExceptionInst)
        val specialCall = JcRawSpecialCallExpr(
            jcExceptionClass.typename,
            "<init>",
            listOf(jcClasspath.stringType().getTypename()),
            jcClasspath.void.getTypename(),
            localVar,
            listOf(JcRawString("Method should be mocked!!"))
        )
        val callInst = JcRawCallInst(jcMethod, specialCall)
        val throwInst = JcRawThrowInst(jcMethod, localVar)
        return listOf(assignInst, callInst, throwInst)
    }

    private fun addMockToAbstractMethod(
        jcMethod: JcMethod,
        mockedMethodId: Long,
        classRebuilder: MockClassRebuilder
    ): MethodNode {
        val newJcMethod = classRebuilder.createNewVirtualMethod(jcMethod = jcMethod, makeNotAbstract = true)
        val mockInstructions =
            addMockInvocationInJcdbInstructions(
                jcClass = classRebuilder.mockedJcVirtualClass,
                jcMethod = newJcMethod,
                mockedMethodId = mockedMethodId,
                isGlobalMock = false
            )
        val throwExceptionInstructions = throwExceptionInJcdbInstructions(newJcMethod)
        return MethodNodeBuilder(
            method = newJcMethod,
            instList = JcInstListImpl(mockInstructions + throwExceptionInstructions)
        ).build()
    }

    private fun addMockToMethod(
        jcClass: JcClassOrInterface,
        jcMethod: JcMethod,
        mockedMethodId: Long,
        isGlobalMock: Boolean
    ): MethodNode {
        val mockInstructions = addMockInvocationInJcdbInstructions(jcClass, jcMethod, mockedMethodId, isGlobalMock)
        val oldInstructions = jcMethod.rawInstList.toMutableList()
        return MethodNodeBuilder(
            method = jcMethod,
            instList = JcInstListImpl(mockInstructions + oldInstructions)
        ).build()
    }

    private fun addMockInfoAndRedefineClass(
        jcClass: JcClassOrInterface, methods: List<JcMethod>
    ): Class<*> {
        val classNode = jcClass.asmNode()
        val asmMethods = classNode.methods
        for (jcMethod in methods) {
            if (mockCache.contains(jcMethod)) continue
            val asmMethod = asmMethods.find { jcMethod.asmNode().isSameSignature(it) } ?: continue
            val encodedMethodId = encodeMethod(jcMethod)
            val mockedMethod = addMockToMethod(jcClass, jcMethod, encodedMethodId, false)
            asmMethods.replace(asmMethod, mockedMethod)
        }
        val jClass = jcClass.toJavaClass(classLoader)
        classLoader.redefineClass(jClass, classNode)
        return classLoader.loadClass(jcClass.name)
    }


    private fun processMethodsWithDefaultImplementation(jcMethods: List<JcMethod>) {
        val filteredMethods = jcMethods.filter { it !in mockCache && !it.isAbstract }
        val groupedByClasses = filteredMethods.groupBy { it.enclosingClass }
        for ((jcClass, methodsToModify) in groupedByClasses) {
            val jcClassByteCode = jcClass.asmNode()
            val jClass = jcClass.toJavaClass(classLoader)
            val asmMethods = jcClassByteCode.methods
            for (jcMethod in methodsToModify) {
                val encodedMethodId = encodeMethod(jcMethod)
                val mockedMethod = addMockToMethod(jcClass, jcMethod, encodedMethodId, false)
                val asmMethod = asmMethods.find { jcMethod.asmNode().isSameSignature(it) } ?: continue
                asmMethods.replace(asmMethod, mockedMethod)
            }
            classLoader.redefineClass(jClass, jcClassByteCode)
        }
    }

    private fun mockGlobal(
        jcClass: JcClassOrInterface, methods: List<JcMethod>
    ): Class<*> {
        val classNode = jcClass.asmNode()
        val asmMethods = classNode.methods
        for (jcMethod in methods) {
            if (mockCache.contains(jcMethod)) continue
            val asmMethod = asmMethods.find { jcMethod.asmNode().isSameSignature(it) } ?: continue
            val encodedMethodId = encodeMethod(jcMethod)
            val mockedMethod = addMockToMethod(jcClass, jcMethod, encodedMethodId, true)
            asmMethods.replace(asmMethod, mockedMethod)
        }
        val jClass = jcClass.toJavaClass(classLoader)
        classLoader.redefineClass(jClass, classNode)
        return classLoader.loadClass(jcClass.name)
    }

    //TODO Decide what to do with partially mocked classes
    private fun addMockInfoAndDefineNewClass(
        jcClass: JcClassOrInterface, methods: List<JcMethod>
    ): Class<*> {
        val classNode = jcClass.asmNode()
        val mockedClassJVMName = "${classNode.name}Mocked0"
        val mockedClassName = mockedClassJVMName.replace('/', '.')
        val mockedClass =
            try {
                Class.forName(mockedClassName, false, classLoader)
            } catch (e: Throwable) {
                null
            }

        if (mockedClass != null) {
            processMethodsWithDefaultImplementation(methods)
            return mockedClass
        }

        val mockedClassNode = ClassNode()
        classNode.accept(mockedClassNode)
        mockedClassNode.fields.clear()
        val asmMethods = mockedClassNode.methods

        mockedClassNode.name = mockedClassJVMName
        mockedClassNode.access = mockedClassNode.access and Opcodes.ACC_INTERFACE.inv() and Opcodes.ACC_ABSTRACT.inv()
        if (jcClass.isInterface) {
            mockedClassNode.interfaces.add(classNode.name)
        } else {
            mockedClassNode.superName = classNode.name
        }

        val classRebuilder = MockClassRebuilder(jcClass, mockedClassName)


        val abstractMethods =
            (jcClass.declaredMethods + jcClass.allSuperHierarchy.flatMap { it.declaredMethods })
                .filter { it.isAbstract }
                .filterDuplicatesBy { it.jvmSignature }
        for (jcMethod in abstractMethods) {
            val encodedMethodId = encodeMethod(jcMethod)
            val mockedMethod = addMockToAbstractMethod(jcMethod, encodedMethodId, classRebuilder)
            val asmMethod = asmMethods.find { jcMethod.asmNode().isSameSignature(it) } ?: continue
            asmMethods.replace(asmMethod, mockedMethod)
        }

        val defaultMethods = methods.filter { !it.isAbstract }
        for (jcMethod in defaultMethods) {
            val asmMethod = asmMethods.find { jcMethod.asmNode().isSameSignature(it) } ?: continue
            asmMethods.remove(asmMethod)
        }


        //Also we need to rebuild constructors
        for (jcConstructor in jcClass.constructors) {
            val newConstructor = rebuildConstructorForAbstractClass(jcConstructor, classRebuilder)
            val oldConstructor =
                asmMethods.find { jcConstructor.asmNode().isSameSignature(it) } ?: error("cant find constructor in ASM")
            asmMethods.replace(oldConstructor, newConstructor)
        }

        //Load mockedClass
        val mockedJClass = classLoader.defineClass(mockedClassName, mockedClassNode) ?: error("Mocking error")

        //Handle methods with default implementation
        processMethodsWithDefaultImplementation(methods)

        return mockedJClass
    }

    private fun rebuildConstructorForAbstractClass(
        jcConstructor: JcMethod, methodRebuilder: MockClassRebuilder
    ): MethodNode {
        val (newMethod, instList) = methodRebuilder.rebuildInstructions(jcConstructor, true)
        return MethodNodeBuilder(newMethod, instList).build()
    }

    fun addMockInfoInBytecode(
        jcClass: JcClassOrInterface, methods: List<JcMethod>, isGlobalMock: Boolean
    ): Class<*> = when {
        isGlobalMock -> mockGlobal(jcClass, methods)
        jcClass.isInterface || jcClass.isAbstract -> addMockInfoAndDefineNewClass(jcClass, methods)
        else -> addMockInfoAndRedefineClass(jcClass, methods)
    }


    private fun encodeMethod(jcMethod: JcMethod) =
        mockCache.getOrPut(jcMethod) {
            JcInstructionTracer.encode(jcMethod)
        }

    companion object {
        private const val MOCK_BEGIN = "#mockBeginGenerated0"
        private const val MOCK_END = "#mockEndGenerated0"
        private const val IS_MOCKED = "%isMockedGenerated0"
        private const val IS_MOCKED_FUN_NAME = "isMocked"
        private const val RETURN_MOCK_LABEL = "#returnMockValueGenerated0"
        private const val MOCK_RETURN_VALUE_0 = "%mockReturnValueGenerated0"
        private const val MOCK_RETURN_VALUE_1 = "%mockReturnValueGenerated1"
        private const val NOT_MOCKED = "%notMockedException0"
    }


}

