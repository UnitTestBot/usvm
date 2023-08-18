package org.usvm.instrumentation.org.usvm.instrumentation.mock

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.methods
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
        mockedClassName,
        jcClass.access,
        jcClass.declaredFields.map { JcVirtualFieldImpl(it.name, it.access, it.type) },
        jcClass.methods.map {
            JcVirtualMethodImpl(
                it.name,
                it.access,
                it.returnType,
                it.parameters.map { JcVirtualParameter(it.index, it.type) },
                it.description
            )
        }
    )

    private lateinit var newMethod: JcVirtualMethodImpl

    fun createNewVirtualMethod(jcMethod: JcMethod, makeNotAbstract: Boolean): JcVirtualMethodImpl =
        JcVirtualMethodImpl(
            jcMethod.name,
            jcMethod.access.let { if (makeNotAbstract) it and Opcodes.ACC_ABSTRACT.inv() else it },
            jcMethod.returnType,
            jcMethod.parameters.map { JcVirtualParameter(it.index, it.type) },
            jcMethod.description
        ).apply { bind(mockedJcVirtualClass) }


    fun rebuildInstructions(jcMethod: JcMethod, makeNotAbstract: Boolean): Pair<JcMethod, JcMutableInstList<JcRawInst>> {
        newMethod = createNewVirtualMethod(jcMethod, makeNotAbstract)
        return newMethod to jcMethod.rawInstList.map(this).toMutableList()
    }

    private fun convertJcRawValue(jcRawValue: JcRawValue): JcRawValue =
        when (jcRawValue) {
            is JcRawArrayAccess -> JcRawArrayAccess(jcRawValue.array, jcRawValue.index, jcRawValue.typeName)
            is JcRawFieldRef -> JcRawFieldRef(
                jcRawValue.instance?.let { convertJcRawValue(it) },
                mockedJcVirtualClass.typename,
                jcRawValue.fieldName,
                jcRawValue.typeName
            )
            is JcRawBool -> JcRawBool(jcRawValue.value, jcRawValue.typeName)
            is JcRawByte -> JcRawByte(jcRawValue.value, jcRawValue.typeName)
            is JcRawChar -> JcRawChar(jcRawValue.value, jcRawValue.typeName)
            is JcRawClassConstant -> JcRawClassConstant(jcRawValue.className, jcRawValue.typeName)
            is JcRawDouble -> JcRawDouble(jcRawValue.value, jcRawValue.typeName)
            is JcRawFloat -> JcRawFloat(jcRawValue.value, jcRawValue.typeName)
            is JcRawInt -> JcRawInt(jcRawValue.value, jcRawValue.typeName)
            is JcRawLong -> JcRawLong(jcRawValue.value, jcRawValue.typeName)
            is JcRawMethodConstant -> JcRawMethodConstant(
                mockedJcVirtualClass.typename,
                jcRawValue.name,
                jcRawValue.argumentTypes,
                jcRawValue.returnType,
                jcRawValue.typeName
            )
            is JcRawMethodType -> JcRawMethodType(jcRawValue.argumentTypes, jcRawValue.returnType, jcRawValue.typeName)
            is JcRawNullConstant -> JcRawNullConstant(jcRawValue.typeName)
            is JcRawShort -> JcRawShort(jcRawValue.value, jcRawValue.typeName)
            is JcRawStringConstant -> JcRawStringConstant(jcRawValue.value, jcRawValue.typeName)
            is JcRawArgument -> JcRawArgument(jcRawValue.index, jcRawValue.name, jcRawValue.typeName)
            is JcRawLocalVar -> JcRawLocalVar(jcRawValue.name, jcRawValue.typeName)
            is JcRawThis -> JcRawThis(mockedJcVirtualClass.typename)
        }


    private fun convertBsm(bsmHandle: BsmHandle): BsmHandle {
        return BsmHandle(
            bsmHandle.tag,
            mockedJcVirtualClass.typename,
            bsmHandle.name,
            bsmHandle.argTypes,
            bsmHandle.returnType,
            bsmHandle.isInterface
        )
    }

    private fun convertBsmArg(bsmArg: BsmArg): BsmArg =
        when (bsmArg) {
            is BsmDoubleArg -> BsmDoubleArg(bsmArg.value)
            is BsmFloatArg -> BsmFloatArg(bsmArg.value)
            is BsmHandle -> convertBsm(bsmArg)
            is BsmIntArg -> BsmIntArg(bsmArg.value)
            is BsmLongArg -> BsmLongArg(bsmArg.value)
            is BsmMethodTypeArg -> BsmMethodTypeArg(bsmArg.argumentTypes, bsmArg.returnType)
            is BsmStringArg -> BsmStringArg(bsmArg.value)
            is BsmTypeArg -> BsmTypeArg(bsmArg.typeName)
        }


    override fun visitJcRawAssignInst(inst: JcRawAssignInst): JcRawInst {
        return JcRawAssignInst(newMethod, convertJcRawValue(inst.lhv), inst.rhv.accept(this))
    }

    override fun visitJcRawCallInst(inst: JcRawCallInst): JcRawInst {
        return JcRawCallInst(newMethod, inst.callExpr.accept(this) as JcRawCallExpr)
    }

    override fun visitJcRawCatchInst(inst: JcRawCatchInst): JcRawInst {
        return JcRawCatchInst(
            newMethod,
            convertJcRawValue(inst.throwable),
            JcRawLabelRef(inst.handler.name),
            inst.entries.map {
                JcRawCatchEntry(
                    it.acceptedThrowable,
                    JcRawLabelRef(it.startInclusive.name),
                    JcRawLabelRef(it.endExclusive.name)
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
            newMethod,
            inst.condition.accept(this) as JcRawConditionExpr,
            JcRawLabelRef(inst.trueBranch.name),
            JcRawLabelRef(inst.falseBranch.name)
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
            newMethod,
            convertJcRawValue(inst.key),
            inst.branches.map { convertJcRawValue(it.key) to JcRawLabelRef(it.value.name) }.toMap(),
            JcRawLabelRef(inst.default.name)
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

    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr): JcRawExpr {
        return JcRawDynamicCallExpr(
            convertBsm(expr.bsm),
            expr.bsmArgs.map { convertBsmArg(it) },
            expr.callSiteMethodName,
            expr.callSiteArgTypes,
            expr.callSiteReturnType,
            expr.callSiteArgs.map { convertJcRawValue(it) }
        )
    }

    override fun visitJcRawEqExpr(expr: JcRawEqExpr): JcRawExpr {
        return JcRawEqExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawFieldRef(value: JcRawFieldRef): JcRawExpr {
        return JcRawFieldRef(
            value.instance?.let { JcRawThis(mockedJcVirtualClass.typename) },
            mockedJcVirtualClass.typename,
            value.fieldName,
            value.typeName
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

    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr): JcRawExpr {
        return JcRawInterfaceCallExpr(
            mockedJcVirtualClass.typename,
            expr.methodName,
            expr.argumentTypes,
            expr.returnType,
            convertJcRawValue(expr.instance),
            expr.args.map { convertJcRawValue(it) }
        )
    }

    override fun visitJcRawLeExpr(expr: JcRawLeExpr): JcRawExpr {
        return JcRawLeExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr): JcRawExpr {
        return JcRawLengthExpr(expr.typeName, convertJcRawValue(expr.array))
    }

    override fun visitJcRawLocalVar(value: JcRawLocalVar): JcRawExpr {
        return JcRawLocalVar(value.name, value.typeName)
    }

    override fun visitJcRawLong(value: JcRawLong): JcRawExpr {
        return JcRawLong(value.value)
    }

    override fun visitJcRawLtExpr(expr: JcRawLtExpr): JcRawExpr {
        return JcRawLtExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }

    override fun visitJcRawMethodConstant(value: JcRawMethodConstant): JcRawExpr {
        return JcRawMethodConstant(
            mockedJcVirtualClass.typename,
            value.name,
            value.argumentTypes,
            value.returnType,
            value.typeName
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

    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr): JcRawExpr {
        return JcRawSpecialCallExpr(
            mockedJcVirtualClass.typename,
            expr.methodName,
            expr.argumentTypes,
            expr.returnType,
            convertJcRawValue(expr.instance),
            expr.args.map { convertJcRawValue(it) })
    }

    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr): JcRawExpr {
        return JcRawStaticCallExpr(
            mockedJcVirtualClass.typename,
            expr.methodName,
            expr.argumentTypes,
            expr.returnType,
            expr.args.map { convertJcRawValue(it) })
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

    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr): JcRawExpr {
        return JcRawVirtualCallExpr(
            mockedJcVirtualClass.typename,
            expr.methodName,
            expr.argumentTypes,
            expr.returnType,
            convertJcRawValue(expr.instance),
            expr.args.map { convertJcRawValue(it) })
    }

    override fun visitJcRawXorExpr(expr: JcRawXorExpr): JcRawExpr {
        return JcRawXorExpr(expr.typeName, convertJcRawValue(expr.lhv), convertJcRawValue(expr.rhv))
    }


}