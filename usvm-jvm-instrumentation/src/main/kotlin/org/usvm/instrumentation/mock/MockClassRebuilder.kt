package org.usvm.instrumentation.mock

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.*
import org.jacodb.api.jvm.ext.methods
import org.jacodb.impl.cfg.*
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualFieldImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.objectweb.asm.Opcodes
import org.usvm.instrumentation.util.typename

class MockClassRebuilder(
    val jcClass: JcClassOrInterface,
    mockedClassName: String
) : JcRawInstVisitor<JcRawInst>, JcRawExprVisitor<JcRawExpr> {

    val mockedJcVirtualClass = JcVirtualClassImpl(
        name = mockedClassName,
        access = jcClass.access,
        initialFields = jcClass.declaredFields.map { JcVirtualFieldImpl(it.name, it.access, it.type) },
        initialMethods = jcClass.methods.map {
            JcVirtualMethodImpl(
                name = it.name,
                access = it.access,
                returnType = it.returnType,
                parameters = it.parameters.map { JcVirtualParameter(it.index, it.type) },
                description = it.description
            )
        }
    )

    private lateinit var newMethod: JcVirtualMethodImpl

    fun createNewVirtualMethod(jcMethod: JcMethod, makeNotAbstract: Boolean): JcVirtualMethodImpl =
        JcVirtualMethodImpl(
            name = jcMethod.name,
            access = jcMethod.access.let { if (makeNotAbstract) it and Opcodes.ACC_ABSTRACT.inv() else it },
            returnType = jcMethod.returnType,
            parameters = jcMethod.parameters.map { JcVirtualParameter(it.index, it.type) },
            description = jcMethod.description
        ).apply { bind(mockedJcVirtualClass) }


    fun rebuildInstructions(
        jcMethod: JcMethod,
        makeNotAbstract: Boolean
    ): Pair<JcMethod, JcMutableInstList<JcRawInst>> {
        newMethod = createNewVirtualMethod(jcMethod, makeNotAbstract)
        return newMethod to jcMethod.rawInstList.map(this).toMutableList()
    }

    private fun convertJcRawValue(jcRawValue: JcRawValue): JcRawValue = with(jcRawValue) {
        when (this) {
            is JcRawArrayAccess -> JcRawArrayAccess(array, index, typeName)
            is JcRawFieldRef -> JcRawFieldRef(
                instance = instance?.let { convertJcRawValue(it) },
                declaringClass = mockedJcVirtualClass.typename,
                fieldName = fieldName,
                typeName = typeName
            )

            is JcRawBool -> JcRawBool(value, typeName)
            is JcRawByte -> JcRawByte(value, typeName)
            is JcRawChar -> JcRawChar(value, typeName)
            is JcRawClassConstant -> JcRawClassConstant(className, typeName)
            is JcRawDouble -> JcRawDouble(value, typeName)
            is JcRawFloat -> JcRawFloat(value, typeName)
            is JcRawInt -> JcRawInt(value, typeName)
            is JcRawLong -> JcRawLong(value, typeName)
            is JcRawMethodConstant -> JcRawMethodConstant(
                declaringClass = mockedJcVirtualClass.typename,
                name = name,
                argumentTypes = argumentTypes,
                returnType = returnType,
                typeName = typeName
            )

            is JcRawMethodType -> JcRawMethodType(argumentTypes, returnType, typeName)
            is JcRawNullConstant -> JcRawNullConstant(typeName)
            is JcRawShort -> JcRawShort(value, typeName)
            is JcRawStringConstant -> JcRawStringConstant(value, typeName)
            is JcRawArgument -> JcRawArgument(index, name, typeName)
            is JcRawLocalVar -> JcRawLocalVar(index, name, typeName)
            is JcRawThis -> JcRawThis(mockedJcVirtualClass.typename)
        }
    }


    private fun convertBsm(bsmHandle: BsmHandle): BsmHandle {
        return BsmHandle(
            tag = bsmHandle.tag,
            declaringClass = mockedJcVirtualClass.typename,
            name = bsmHandle.name,
            argTypes = bsmHandle.argTypes,
            returnType = bsmHandle.returnType,
            isInterface = bsmHandle.isInterface
        )
    }

    private fun convertBsmArg(bsmArg: BsmArg): BsmArg = with(bsmArg) {
        when (this) {
            is BsmDoubleArg -> BsmDoubleArg(value)
            is BsmFloatArg -> BsmFloatArg(value)
            is BsmHandle -> convertBsm(this)
            is BsmIntArg -> BsmIntArg(value)
            is BsmLongArg -> BsmLongArg(value)
            is BsmMethodTypeArg -> BsmMethodTypeArg(argumentTypes, returnType)
            is BsmStringArg -> BsmStringArg(value)
            is BsmTypeArg -> BsmTypeArg(typeName)
        }
    }


    override fun visitJcRawAssignInst(inst: JcRawAssignInst): JcRawInst {
        return JcRawAssignInst(newMethod, convertJcRawValue(inst.lhv), inst.rhv.accept(this))
    }

    override fun visitJcRawCallInst(inst: JcRawCallInst): JcRawInst {
        return JcRawCallInst(newMethod, inst.callExpr.accept(this) as JcRawCallExpr)
    }

    override fun visitJcRawCatchInst(inst: JcRawCatchInst): JcRawInst {
        return JcRawCatchInst(
            owner = newMethod,
            throwable = convertJcRawValue(inst.throwable),
            handler = JcRawLabelRef(inst.handler.name),
            entries = inst.entries.map {
                JcRawCatchEntry(
                    acceptedThrowable = it.acceptedThrowable,
                    startInclusive = JcRawLabelRef(it.startInclusive.name),
                    endExclusive = JcRawLabelRef(it.endExclusive.name)
                )
            })
    }

    override fun visitJcRawEnterMonitorInst(inst: JcRawEnterMonitorInst): JcRawInst {
        return JcRawEnterMonitorInst(newMethod, convertJcRawValue(inst.monitor) as JcRawSimpleValue)
    }

    override fun visitJcRawExitMonitorInst(inst: JcRawExitMonitorInst): JcRawInst {
        return JcRawExitMonitorInst(newMethod, convertJcRawValue(inst.monitor) as JcRawSimpleValue)
    }

    override fun visitJcRawGotoInst(inst: JcRawGotoInst): JcRawInst {
        return JcRawGotoInst(newMethod, JcRawLabelRef(inst.target.name))
    }

    override fun visitJcRawIfInst(inst: JcRawIfInst): JcRawInst {
        return JcRawIfInst(
            owner = newMethod,
            condition = inst.condition.accept(this) as JcRawConditionExpr,
            trueBranch = JcRawLabelRef(inst.trueBranch.name),
            falseBranch = JcRawLabelRef(inst.falseBranch.name)
        )
    }

    override fun visitJcRawLabelInst(inst: JcRawLabelInst): JcRawInst {
        return JcRawLabelInst(newMethod, inst.name)
    }

    override fun visitJcRawLineNumberInst(inst: JcRawLineNumberInst): JcRawInst {
        return JcRawLineNumberInst(newMethod, inst.lineNumber, JcRawLabelRef(inst.start.name))
    }

    override fun visitJcRawReturnInst(inst: JcRawReturnInst): JcRawInst {
        return JcRawReturnInst(newMethod, inst.returnValue?.let { convertJcRawValue(it) })
    }

    override fun visitJcRawSwitchInst(inst: JcRawSwitchInst): JcRawInst {
        return JcRawSwitchInst(
            owner = newMethod,
            key = convertJcRawValue(inst.key),
            branches = inst.branches.map { convertJcRawValue(it.key) to JcRawLabelRef(it.value.name) }.toMap(),
            default = JcRawLabelRef(inst.default.name)
        )
    }

    override fun visitJcRawThrowInst(inst: JcRawThrowInst): JcRawInst {
        return JcRawThrowInst(newMethod, convertJcRawValue(inst.throwable))
    }

    override fun visitJcRawAddExpr(expr: JcRawAddExpr): JcRawExpr {
        return JcRawAddExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawAndExpr(expr: JcRawAndExpr): JcRawExpr {
        return JcRawAndExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawArgument(value: JcRawArgument): JcRawExpr {
        return JcRawArgument(value.index, value.name, value.typeName)
    }

    override fun visitJcRawArrayAccess(value: JcRawArrayAccess): JcRawExpr {
        return JcRawArrayAccess(convertJcRawValue(value.array), convertJcRawValue(value.index), value.typeName)
    }

    override fun visitJcRawBool(value: JcRawBool): JcRawExpr {
        return JcRawBool(value.value)
    }

    override fun visitJcRawByte(value: JcRawByte): JcRawExpr {
        return JcRawByte(value.value)
    }

    override fun visitJcRawCastExpr(expr: JcRawCastExpr): JcRawExpr {
        return JcRawCastExpr(expr.typeName, convertJcRawValue(expr.operand))
    }

    override fun visitJcRawChar(value: JcRawChar): JcRawExpr {
        return JcRawChar(value.value)
    }

    override fun visitJcRawClassConstant(value: JcRawClassConstant): JcRawExpr {
        return JcRawClassConstant(value.className, value.typeName)
    }

    override fun visitJcRawCmpExpr(expr: JcRawCmpExpr): JcRawExpr {
        return JcRawCmpExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr): JcRawExpr {
        return JcRawCmpgExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawCmplExpr(expr: JcRawCmplExpr): JcRawExpr {
        return JcRawCmplExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawDivExpr(expr: JcRawDivExpr): JcRawExpr {
        return JcRawDivExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawDouble(value: JcRawDouble): JcRawExpr {
        return JcRawDouble(value.value)
    }

    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr): JcRawExpr = with(expr) {
        JcRawDynamicCallExpr(
            bsm = convertBsm(bsm),
            bsmArgs = bsmArgs.map { convertBsmArg(it) },
            callSiteMethodName = callSiteMethodName,
            callSiteArgTypes = callSiteArgTypes,
            callSiteReturnType = callSiteReturnType,
            callSiteArgs = callSiteArgs.map { convertJcRawValue(it) }
        )
    }

    override fun visitJcRawEqExpr(expr: JcRawEqExpr): JcRawExpr {
        return JcRawEqExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawFieldRef(value: JcRawFieldRef): JcRawExpr {
        return JcRawFieldRef(
            instance = value.instance?.let { JcRawThis(mockedJcVirtualClass.typename) },
            declaringClass = mockedJcVirtualClass.typename,
            fieldName = value.fieldName,
            typeName = value.typeName
        )
    }

    override fun visitJcRawFloat(value: JcRawFloat): JcRawExpr {
        return JcRawFloat(value.value)
    }

    override fun visitJcRawGeExpr(expr: JcRawGeExpr): JcRawExpr {
        return JcRawGeExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawGtExpr(expr: JcRawGtExpr): JcRawExpr {
        return JcRawGtExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr): JcRawExpr {
        return JcRawInstanceOfExpr(expr.typeName, convertJcRawValue(expr.operand), expr.targetType)
    }

    override fun visitJcRawInt(value: JcRawInt): JcRawExpr {
        return JcRawInt(value.value)
    }

    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr): JcRawExpr = with(expr) {
        JcRawInterfaceCallExpr(
            declaringClass = mockedJcVirtualClass.typename,
            methodName = methodName,
            argumentTypes = argumentTypes,
            returnType = returnType,
            instance = convertJcRawValue(instance),
            args = args.map { convertJcRawValue(it) }
        )
    }

    override fun visitJcRawLeExpr(expr: JcRawLeExpr): JcRawExpr {
        return JcRawLeExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr): JcRawExpr {
        return JcRawLengthExpr(expr.typeName, convertJcRawValue(expr.array))
    }

    override fun visitJcRawLocalVar(value: JcRawLocalVar): JcRawExpr {
        return JcRawLocalVar(value.index, value.name, value.typeName)
    }

    override fun visitJcRawLong(value: JcRawLong): JcRawExpr {
        return JcRawLong(value.value)
    }

    override fun visitJcRawLtExpr(expr: JcRawLtExpr): JcRawExpr {
        return JcRawLtExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawMethodConstant(value: JcRawMethodConstant): JcRawExpr = with(value) {
        JcRawMethodConstant(
            declaringClass = mockedJcVirtualClass.typename,
            name = name,
            argumentTypes = argumentTypes,
            returnType = returnType,
            typeName = typeName
        )
    }

    override fun visitJcRawMethodType(value: JcRawMethodType): JcRawExpr {
        return JcRawMethodType(value.argumentTypes, value.returnType, value.typeName)
    }

    override fun visitJcRawMulExpr(expr: JcRawMulExpr): JcRawExpr {
        return JcRawMulExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawNegExpr(expr: JcRawNegExpr): JcRawExpr {
        return JcRawNegExpr(expr.typeName, convertJcRawValue(expr.operand))
    }

    override fun visitJcRawNeqExpr(expr: JcRawNeqExpr): JcRawExpr {
        return JcRawNeqExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr): JcRawExpr {
        return JcRawNewArrayExpr(expr.typeName, expr.dimensions.map { convertJcRawValue(it) })
    }

    override fun visitJcRawNewExpr(expr: JcRawNewExpr): JcRawExpr {
        return JcRawNewExpr(expr.typeName)
    }

    override fun visitJcRawNullConstant(value: JcRawNullConstant): JcRawExpr {
        return JcRawNullConstant(value.typeName)
    }

    override fun visitJcRawOrExpr(expr: JcRawOrExpr): JcRawExpr {
        return JcRawOrExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawRemExpr(expr: JcRawRemExpr): JcRawExpr {
        return JcRawRemExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawShlExpr(expr: JcRawShlExpr): JcRawExpr {
        return JcRawShlExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawShort(value: JcRawShort): JcRawExpr {
        return JcRawShort(value.value)
    }

    override fun visitJcRawShrExpr(expr: JcRawShrExpr): JcRawExpr {
        return JcRawShrExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr): JcRawExpr = with(expr) {
        JcRawSpecialCallExpr(
            declaringClass = mockedJcVirtualClass.typename,
            methodName = methodName,
            argumentTypes = argumentTypes,
            returnType = returnType,
            instance = convertJcRawValue(instance),
            args = args.map { convertJcRawValue(it) })
    }

    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr): JcRawExpr = with(expr) {
        JcRawStaticCallExpr(
            declaringClass = mockedJcVirtualClass.typename,
            methodName = methodName,
            argumentTypes = argumentTypes,
            returnType = returnType,
            args = args.map { convertJcRawValue(it) })
    }

    override fun visitJcRawStringConstant(value: JcRawStringConstant): JcRawExpr {
        return JcRawStringConstant(value.value, value.typeName)
    }

    override fun visitJcRawSubExpr(expr: JcRawSubExpr): JcRawExpr {
        return JcRawSubExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawThis(value: JcRawThis): JcRawExpr {
        return JcRawThis(mockedJcVirtualClass.typename)
    }

    override fun visitJcRawUshrExpr(expr: JcRawUshrExpr): JcRawExpr {
        return JcRawUshrExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr): JcRawExpr = with(expr) {
        JcRawVirtualCallExpr(
            declaringClass = mockedJcVirtualClass.typename,
            methodName = methodName,
            argumentTypes = argumentTypes,
            returnType = returnType,
            instance = convertJcRawValue(instance),
            args = args.map { convertJcRawValue(it) })
    }

    override fun visitJcRawXorExpr(expr: JcRawXorExpr): JcRawExpr {
        return JcRawXorExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }


}