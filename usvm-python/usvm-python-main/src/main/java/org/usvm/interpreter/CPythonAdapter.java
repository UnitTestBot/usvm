package org.usvm.interpreter;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.usvm.annotations.*;
import org.usvm.annotations.codegeneration.CType;
import org.usvm.annotations.codegeneration.ObjectConverter;
import org.usvm.annotations.ids.SymbolicMethodId;
import org.usvm.annotations.ids.ApproximationId;
import org.usvm.language.*;
import org.usvm.machine.MockHeader;
import org.usvm.machine.interpreters.PythonObject;
import org.usvm.machine.interpreters.operations.descriptors.*;
import org.usvm.machine.interpreters.operations.tracing.*;
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;

import static org.usvm.machine.interpreters.operations.basic.CommonKt.*;
import static org.usvm.machine.interpreters.operations.basic.ConstantsKt.handlerLoadConstKt;
import static org.usvm.machine.interpreters.operations.basic.ControlKt.handlerForkKt;
import static org.usvm.machine.interpreters.operations.basic.FloatKt.*;
import static org.usvm.machine.interpreters.operations.basic.ListKt.*;
import static org.usvm.machine.interpreters.operations.basic.LongKt.*;
import static org.usvm.machine.interpreters.operations.basic.MethodNotificationsKt.*;
import static org.usvm.machine.interpreters.operations.basic.RangeKt.*;
import static org.usvm.machine.interpreters.operations.basic.SliceKt.handlerCreateSliceKt;
import static org.usvm.machine.interpreters.operations.basic.TupleKt.*;
import static org.usvm.machine.interpreters.operations.basic.VirtualKt.*;
import static org.usvm.machine.interpreters.operations.symbolicmethods.BuiltinsKt.symbolicMethodFloatKt;
import static org.usvm.machine.interpreters.operations.symbolicmethods.BuiltinsKt.symbolicMethodIntKt;
import static org.usvm.machine.interpreters.operations.symbolicmethods.ListKt.*;
import static org.usvm.machine.interpreters.operations.tracing.PathTracingKt.withTracing;

@SuppressWarnings("unused")
public class CPythonAdapter {
    public boolean isInitialized = false;
    public long thrownException = 0L;
    public long thrownExceptionType = 0L;
    public long javaExceptionType = 0L;
    public long pyNoneRef = 0L;
    public int pyEQ;
    public int pyNE;
    public int pyLE;
    public int pyLT;
    public int pyGE;
    public int pyGT;

    public native void initializePython(String pythonHome);
    public native void initializeSpecialApproximations();
    public native void finalizePython();
    public static native int pythonExceptionOccurred();
    public native long getNewNamespace();  // returns reference to a new dict
    public native void addName(long dict, long object, String name);
    public native int concreteRun(long globals, String code, boolean printErrorMessage, boolean setHook);  // returns 0 on success
    public native long eval(long globals, String obj, boolean printErrorMessage, boolean setHook);  // returns PyObject *
    public native long concreteRunOnFunctionRef(long functionRef, long[] concreteArgs, boolean setHook);
    public native long concolicRun(long functionRef, long[] concreteArgs, long[] virtualArgs, SymbolForCPython[] symbolicArgs, ConcolicRunContext context, NamedSymbolForCPython[] global_clones, boolean print_error_message);
    public native void printPythonObject(long object);
    public native long[] getIterableElements(long iterable);
    public native String getPythonObjectRepr(long object, boolean print_error_message);
    public native String getPythonObjectStr(long object);
    public native long getAddressOfReprFunction(long object);
    public native String getPythonObjectTypeName(long object);
    public native long getPythonObjectType(long object);
    public native String getNameOfPythonType(long type);
    public static native int getInstructionFromFrame(long frameRef);
    public static native long getCodeFromFrame(long frameRef);
    public native long allocateVirtualObject(VirtualPythonObject object);
    public native long makeList(long[] elements);
    public native long allocateTuple(int size);
    public native void setTupleElement(long tuple, int index, long element);
    public native int typeHasNbBool(long type);
    public native int typeHasNbInt(long type);
    public native int typeHasNbAdd(long type);
    public native int typeHasNbSubtract(long type);
    public native int typeHasNbMultiply(long type);
    public native int typeHasNbMatrixMultiply(long type);
    public native int typeHasSqLength(long type);
    public native int typeHasMpLength(long type);
    public native int typeHasMpSubscript(long type);
    public native int typeHasMpAssSubscript(long type);
    public native int typeHasTpRichcmp(long type);
    public native int typeHasTpGetattro(long type);
    public native int typeHasTpSetattro(long type);
    public native int typeHasTpIter(long type);
    public native int typeHasTpCall(long type);
    public native int typeHasTpHash(long type);
    public native int typeHasTpDescrGet(long type);
    public native int typeHasTpDescrSet(long type);
    public native int typeHasStandardNew(long type);
    public native long callStandardNew(long type);
    public native int typeHasStandardTpGetattro(long type);
    public native int typeHasStandardTpSetattro(long type);
    public native Throwable extractException(long exception);
    public native void decref(long object);
    public native void incref(long object);
    public native String checkForIllegalOperation();
    public native long typeLookup(long typeRef, String name);
    @Nullable
    public native MemberDescriptor getSymbolicDescriptor(long concreteDescriptorRef);
    public native long constructPartiallyAppliedSymbolicMethod(SymbolForCPython self, long methodRef);
    public native long constructApproximation(SymbolForCPython self, long approximationRef);
    public native long constructPartiallyAppliedPythonMethod(SymbolForCPython self);
    static {
        System.loadLibrary("cpythonadapter");
    }

    @CPythonAdapterJavaMethod(cName = "instruction")
    @CPythonFunction(
            argCTypes = {CType.PyFrameObject},
            argConverters = {ObjectConverter.FrameConverter}
    )
    public static void handlerInstruction(@NotNull ConcolicRunContext context, long frameRef) {
        if (pythonExceptionOccurred() != 0)
            return;
        context.curOperation = null;
        int instruction = getInstructionFromFrame(frameRef);
        long codeRef = getCodeFromFrame(frameRef);
        PythonObject code = new PythonObject(codeRef);
        withTracing(context, new NextInstruction(new PythonInstruction(instruction), code), () -> Unit.INSTANCE);
    }

    private static SymbolForCPython wrap(UninterpretedSymbolicPythonObject obj) {
        if (obj == null)
            return null;
        return new SymbolForCPython(obj, 0);
    }

    private static SymbolForCPython methodWrapper(
            ConcolicRunContext context,
            SymbolicHandlerEventParameters<SymbolForCPython> params,
            Callable<UninterpretedSymbolicPythonObject> valueSupplier
    ) {
        return withTracing(
                context,
                params,
                () -> {
                    UninterpretedSymbolicPythonObject result = valueSupplier.call();
                    return wrap(result);
                }
        );
    }

    @NotNull
    private static Callable<Unit> unit(Runnable function) {
        return () -> {
            function.run();
            return Unit.INSTANCE;
        };
    }

    @CPythonAdapterJavaMethod(cName = "load_const")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.RefConverter}
    )
    public static SymbolForCPython handlerLoadConst(ConcolicRunContext context, long ref) {
        PythonObject obj = new PythonObject(ref);
        return withTracing(context, new LoadConstParameters(obj), () -> wrap(handlerLoadConstKt(context, obj)));
    }

    @CPythonAdapterJavaMethod(cName = "fork_notify")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static void handlerFork(ConcolicRunContext context, SymbolForCPython cond) {
        if (cond.obj == null)
            return;
        withTracing(context, new Fork(cond), unit(() -> handlerForkKt(context, cond.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "fork_result")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.CInt},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.IntConverter}
    )
    public static void handlerForkResult(ConcolicRunContext context, SymbolForCPython cond, boolean result) {
        PathTracingKt.handlerForkResultKt(context, cond, result);
    }

    @CPythonAdapterJavaMethod(cName = "unpack")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.CInt},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.IntConverter}
    )
    public static void handlerUnpack(ConcolicRunContext context, SymbolForCPython iterable, int count) {
        if (iterable.obj == null)
            return;
        withTracing(context, new Unpack(iterable, count), unit(() -> handlerUnpackKt(context, iterable.obj, count)));
    }

    @CPythonAdapterJavaMethod(cName = "is_op")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void handlerIsOp(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return;
        withTracing(context, new MethodParametersNoReturn("is_op", Arrays.asList(left, right)), unit(() -> handlerIsOpKt(context, left.obj, right.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "none_check")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static void handlerNoneCheck(ConcolicRunContext context, SymbolForCPython on) {
        if (on.obj == null)
            return;
        withTracing(context, new MethodParametersNoReturn("none_check", Collections.singletonList(on)), unit(() -> handlerNoneCheckKt(context, on.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "gt_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerGTLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("gt_long", Arrays.asList(left, right)), () -> handlerGTLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "lt_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerLTLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("lt_long", Arrays.asList(left, right)), () -> handlerLTLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "eq_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerEQLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("eq_long", Arrays.asList(left, right)), () -> handlerEQLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "ne_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerNELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("ne_long", Arrays.asList(left, right)), () -> handlerNELongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "ge_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerGELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("ge_long", Arrays.asList(left, right)), () -> handlerGELongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "le_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerLELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("le_long", Arrays.asList(left, right)), () -> handlerLELongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "add_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerADDLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("add_long", Arrays.asList(left, right)), () -> handlerADDLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "sub_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerSUBLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("sub_long", Arrays.asList(left, right)), () -> handlerSUBLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "mul_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerMULLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("mul_long", Arrays.asList(left, right)), () -> handlerMULLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "div_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerDIVLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("div_long", Arrays.asList(left, right)), () -> handlerDIVLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "rem_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerREMLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("rem_long", Arrays.asList(left, right)), () -> handlerREMLongKt(context, left.obj, right.obj));
    }

    // TODO: CPythonFunction
    @CPythonAdapterJavaMethod(cName = "pow_long")
    public static SymbolForCPython handlerPOWLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("pow_long", Arrays.asList(left, right)), () -> handlerPOWLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "true_div_long")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerTrueDivLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("true_div_long", Arrays.asList(left, right)), () -> handlerTrueDivLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "gt_float")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerGTFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("gt_float", Arrays.asList(left, right)), () -> handlerGTFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "lt_float")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerLTFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("lt_float", Arrays.asList(left, right)), () -> handlerLTFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "eq_float")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerEQFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("eq_float", Arrays.asList(left, right)), () -> handlerEQFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "ne_float")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerNEFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("ne_float", Arrays.asList(left, right)), () -> handlerNEFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "ge_float")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerGEFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("ge_float", Arrays.asList(left, right)), () -> handlerGEFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "le_float")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerLEFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("le_float", Arrays.asList(left, right)), () -> handlerLEFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "add_float")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerADDFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("add_float", Arrays.asList(left, right)), () -> handlerADDFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "sub_float")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerSUBFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("sub_float", Arrays.asList(left, right)), () -> handlerSUBFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "mul_float")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerMULFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("mul_float", Arrays.asList(left, right)), () -> handlerMULFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "div_float")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerDIVFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("div_float", Arrays.asList(left, right)), () -> handlerDIVFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "bool_and")
    /*@CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter},
            addToSymbolicAdapter = false
    )*/
    public static SymbolForCPython handlerAND(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("bool_and", Arrays.asList(left, right)), () -> handlerAndKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "create_list")
    @CPythonFunction(
            argCTypes = {CType.PyObjectArray},
            argConverters = {ObjectConverter.ArrayConverter}
    )
    public static SymbolForCPython handlerCreateList(ConcolicRunContext context, SymbolForCPython[] elements) {
        if (Arrays.stream(elements).anyMatch(elem -> elem.obj == null))
            return null;
        ListCreation event = new ListCreation(Arrays.asList(elements));
        return withTracing(context, event, () -> wrap(handlerCreateListKt(context, Arrays.stream(elements).map(s -> s.obj))));
    }

    @CPythonAdapterJavaMethod(cName = "create_tuple")
    @CPythonFunction(
            argCTypes = {CType.PyObjectArray},
            argConverters = {ObjectConverter.ArrayConverter}
    )
    public static SymbolForCPython handlerCreateTuple(ConcolicRunContext context, SymbolForCPython[] elements) {
        if (Arrays.stream(elements).anyMatch(elem -> elem.obj == null))
            return null;
        TupleCreation event = new TupleCreation(Arrays.asList(elements));
        return withTracing(context, event, () -> wrap(handlerCreateTupleKt(context, Arrays.stream(elements).map(s -> s.obj))));
    }

    @CPythonAdapterJavaMethod(cName = "create_range")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerCreateRange(ConcolicRunContext context, SymbolForCPython start, SymbolForCPython stop, SymbolForCPython step) {
        if (start.obj == null || stop.obj == null || step.obj == null)
            return null;
        MethodParameters event = new MethodParameters("create_range", Arrays.asList(start, stop, step));
        return withTracing(context, event, () -> wrap(handlerCreateRangeKt(context, start.obj, stop.obj, step.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "create_slice")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerCreateSlice(ConcolicRunContext context, SymbolForCPython start, SymbolForCPython stop, SymbolForCPython step) {
        if (start.obj == null || stop.obj == null || step.obj == null)
            return null;
        MethodParameters event = new MethodParameters("create_slice", Arrays.asList(start, stop, step));
        return withTracing(context, event, () -> wrap(handlerCreateSliceKt(context, start.obj, stop.obj, step.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "range_iter")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerRangeIter(ConcolicRunContext context, SymbolForCPython range) {
        if (range.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("range_iter", Collections.singletonList(range)), () -> handlerRangeIterKt(context, range.obj));
    }

    @CPythonAdapterJavaMethod(cName = "range_iterator_next")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerRangeIteratorNext(ConcolicRunContext context, SymbolForCPython rangeIterator) {
        if (rangeIterator.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("range_iterator_next", Collections.singletonList(rangeIterator)), () -> handlerRangeIteratorNextKt(context, rangeIterator.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_get_item")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerListGetItem(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython index) {
        if (list.obj == null || index.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_get_item", Arrays.asList(list, index)), () -> handlerListGetItemKt(context, list.obj, index.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_extend")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerListExtend(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython tuple) {
        if (list.obj == null || tuple.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_extend", Arrays.asList(list, tuple)), () -> handlerListExtendKt(context, list.obj, tuple.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_concat")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerListConcat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_concat", Arrays.asList(left, right)), () -> handlerListConcatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_inplace_concat")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerListInplaceConcat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_inplace_concat", Arrays.asList(left, right)), () -> handlerListInplaceConcatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_append")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerListAppend(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython elem) {
        if (list.obj == null || elem.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_append", Arrays.asList(list, elem)), () -> handlerListAppendKt(context, list.obj, elem.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_set_item")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void handlerListSetItem(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython index, SymbolForCPython value) {
        if (list.obj == null || index.obj == null || value.obj == null)
            return;
        withTracing(context, new MethodParametersNoReturn("list_set_item", Arrays.asList(list, index, value)), unit(() -> handlerListSetItemKt(context, list.obj, index.obj, value.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "list_get_size")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerListGetSize(ConcolicRunContext context, SymbolForCPython list) {
        if (list.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_get_size", Collections.singletonList(list)), () -> handlerListGetSizeKt(context, list.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_iter")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerListIter(ConcolicRunContext context, SymbolForCPython list) {
        if (list.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_iter", Collections.singletonList(list)), () -> handlerListIterKt(context, list.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_iterator_next")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerListIteratorNext(ConcolicRunContext context, SymbolForCPython iterator) {
        if (iterator.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_iterator_next", Collections.singletonList(iterator)), () -> handlerListIteratorNextKt(context, iterator.obj));
    }

    @CPythonAdapterJavaMethod(cName = "tuple_get_size")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerTupleGetSize(ConcolicRunContext context, SymbolForCPython tuple) {
        if (tuple.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("tuple_get_size", Collections.singletonList(tuple)), () -> handlerTupleGetSizeKt(context, tuple.obj));
    }

    @CPythonAdapterJavaMethod(cName = "tuple_get_item")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerTupleGetItem(ConcolicRunContext context, SymbolForCPython tuple, SymbolForCPython index) {
        if (tuple.obj == null || index.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("tuple_get_item", Arrays.asList(tuple, index)), () -> handlerTupleGetItemKt(context, tuple.obj, index.obj));
    }

    @CPythonAdapterJavaMethod(cName = "tuple_iter")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerTupleIter(ConcolicRunContext context, SymbolForCPython tuple) {
        if (tuple.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("tuple_iter", Collections.singletonList(tuple)), () -> handlerTupleIterKt(context, tuple.obj));
    }

    @CPythonAdapterJavaMethod(cName = "tuple_iterator_next")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerTupleIteratorNext(ConcolicRunContext context, SymbolForCPython iterator) {
        if (iterator.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("tuple_iterator_next", Collections.singletonList(iterator)), () -> handlerTupleIteratorNextKt(context, iterator.obj));
    }

    @CPythonAdapterJavaMethod(cName = "function_call")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.RefConverter}
    )
    public static void handlerFunctionCall(ConcolicRunContext context, long codeRef) {
        PythonObject code = new PythonObject(codeRef);
        withTracing(context, new PythonFunctionCall(code), () -> Unit.INSTANCE);
    }

    @CPythonAdapterJavaMethod(cName = "function_return")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.RefConverter}
    )
    public static void handlerReturn(ConcolicRunContext context, long codeRef) {
        withTracing(context, new PythonReturn(new PythonObject(codeRef)), () -> Unit.INSTANCE);
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_virtual_unary_fun")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerVirtualUnaryFun(ConcolicRunContext context, SymbolForCPython obj) {
        if (obj.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("virtual_unary_fun", Collections.singletonList(obj)), () -> virtualCallSymbolKt(context));
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_virtual_binary_fun")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerVirtualBinaryFun(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("virtual_binary_fun", Arrays.asList(left, right)), () -> virtualCallSymbolKt(context));
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_isinstance")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.RefConverter}
    )
    public static SymbolForCPython handlerIsinstance(ConcolicRunContext context, SymbolForCPython obj, long typeRef) {
        if (obj.obj == null)
            return null;
        PythonObject type = new PythonObject(typeRef);
        return methodWrapper(context, new IsinstanceCheck(obj, type), () -> handlerIsinstanceKt(context, obj.obj, type));
    }

    @CPythonAdapterJavaMethod(cName = "fixate_type")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static void fixateType(ConcolicRunContext context, SymbolForCPython obj) {
        if (obj.obj == null)
            return;
        fixateTypeKt(context, obj.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_bool")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static void notifyNbBool(ConcolicRunContext context, SymbolForCPython symbol) {
        if (symbol.obj == null)
            return;
        context.curOperation = new MockHeader(NbBoolMethod.INSTANCE, Collections.singletonList(symbol.obj), symbol.obj);
        nbBoolKt(context, symbol.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_int")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static void notifyNbInt(ConcolicRunContext context, SymbolForCPython symbol) {
        if (symbol.obj == null)
            return;
        context.curOperation = new MockHeader(NbIntMethod.INSTANCE, Collections.singletonList(symbol.obj), symbol.obj);
        nbIntKt(context, symbol.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_add")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void notifyNbAdd(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return;
        context.curOperation = new MockHeader(NbAddMethod.INSTANCE, Arrays.asList(left.obj, right.obj), null);
        nbAddKt(context, left.obj, right.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_subtract")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void notifyNbSubtract(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return;
        context.curOperation = new MockHeader(NbSubtractMethod.INSTANCE, Arrays.asList(left.obj, right.obj), left.obj);
        nbSubtractKt(context, left.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_multiply")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void notifyNbMultiply(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return;
        context.curOperation = new MockHeader(NbMultiplyMethod.INSTANCE, Arrays.asList(left.obj, right.obj), null);
        nbMultiplyKt(context, left.obj, right.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_matrix_multiply")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void notifyNbMatrixMultiply(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return;
        context.curOperation = new MockHeader(NbMatrixMultiplyMethod.INSTANCE, Arrays.asList(left.obj, right.obj), left.obj);
        nbMatrixMultiplyKt(context, left.obj);
    }

    @CPythonAdapterJavaMethod(cName = "sq_length")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static void notifySqLength(ConcolicRunContext context, SymbolForCPython on) {
        if (on.obj == null)
            return;
        context.curOperation = new MockHeader(SqLengthMethod.INSTANCE, Collections.singletonList(on.obj), on.obj);
        sqLengthKt(context, on.obj);
    }

    @CPythonAdapterJavaMethod(cName = "mp_subscript")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void notifyMpSubscript(ConcolicRunContext context, SymbolForCPython storage, SymbolForCPython item) {
        if (storage.obj == null || item.obj == null)
            return;
        context.curOperation = new MockHeader(MpSubscriptMethod.INSTANCE, Arrays.asList(storage.obj, item.obj), storage.obj);
        mpSubscriptKt(context, storage.obj);
    }

    @CPythonAdapterJavaMethod(cName = "mp_ass_subscript")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void notifyMpAssSubscript(ConcolicRunContext context, SymbolForCPython storage, SymbolForCPython item, SymbolForCPython value) {
        if (storage.obj == null || item.obj == null || value.obj == null)
            return;
        context.curOperation = new MockHeader(MpAssSubscriptMethod.INSTANCE, Arrays.asList(storage.obj, item.obj, value.obj), storage.obj);
        mpAssSubscriptKt(context, storage.obj);
    }

    @CPythonAdapterJavaMethod(cName = "tp_richcompare")
    @CPythonFunction(
            argCTypes = {CType.CInt, CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.IntConverter, ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void notifyTpRichcmp(ConcolicRunContext context, int op, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return;
        context.curOperation = new MockHeader(new TpRichcmpMethod(op), Arrays.asList(left.obj, right.obj), left.obj);
        tpRichcmpKt(context, left.obj);
    }

    @CPythonAdapterJavaMethod(cName = "tp_getattro")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void notifyTpGetattro(ConcolicRunContext context, SymbolForCPython on, SymbolForCPython name) {
        if (on.obj == null || name.obj == null)
            return;
        context.curOperation = new MockHeader(TpGetattro.INSTANCE, Arrays.asList(on.obj, name.obj), on.obj);
        tpGetattroKt(context, on.obj, name.obj);
    }

    @CPythonAdapterJavaMethod(cName = "tp_setattro")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void notifyTpSetattro(ConcolicRunContext context, SymbolForCPython on, SymbolForCPython name, SymbolForCPython value) {
        if (on.obj == null || name.obj == null)
            return;
        context.curOperation = new MockHeader(TpSetattro.INSTANCE, Arrays.asList(on.obj, name.obj, value.obj), on.obj);
        tpSetattroKt(context, on.obj, name.obj);
    }

    @CPythonAdapterJavaMethod(cName = "tp_iter")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static void notifyTpIter(ConcolicRunContext context, SymbolForCPython on) {
        if (on.obj == null)
            return;
        context.curOperation = new MockHeader(TpIterMethod.INSTANCE, Collections.singletonList(on.obj), on.obj);
        tpIterKt(context, on.obj);
    }

    @CPythonAdapterJavaMethod(cName = "tp_call")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static void notifyTpCall(ConcolicRunContext context, SymbolForCPython on) {
        if (on.obj == null)
            return;
        context.curOperation = new MockHeader(TpCallMethod.INSTANCE, Collections.singletonList(on.obj), on.obj);
        tpCallKt(context, on.obj);
    }

    @CPythonAdapterJavaMethod(cName = "tp_hash")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter}
    )
    public static void notifyTpHash(ConcolicRunContext context, SymbolForCPython on) {
        if (on.obj == null)
            return;
        tpHashKt(context, on.obj);
    }

    @CPythonAdapterJavaMethod(cName = "virtual_nb_bool")
    public static boolean virtualNbBool(ConcolicRunContext context, VirtualPythonObject obj) {
        return virtualNbBoolKt(context, obj);
    }

    @CPythonAdapterJavaMethod(cName = "virtual_sq_length")
    public static int virtualSqLength(ConcolicRunContext context, VirtualPythonObject obj) {
        return virtualSqLengthKt(context, obj);
    }

    @CPythonAdapterJavaMethod(cName = "virtual_call")
    public static long virtualCall(ConcolicRunContext context, int owner) {
        if (context.curOperation != null && owner != -1) {
            context.curOperation.setMethodOwner(context.curOperation.getArgs().get(owner));
        }
        return virtualCallKt(context).getAddress();
    }


    @CPythonAdapterJavaMethod(cName = "lost_symbolic_value")
    @CPythonFunction(
            argCTypes = {CType.CStr},
            argConverters = {ObjectConverter.StringConverter}
    )
    public static void lostSymbolicValue(ConcolicRunContext context, String description) {
        lostSymbolicValueKt(context, description);
    }

    @CPythonAdapterJavaMethod(cName = "standard_tp_getattro")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static SymbolForCPython handlerStandardTpGetattro(ConcolicRunContext context, SymbolForCPython obj, SymbolForCPython name) {
        if (obj.obj == null || name.obj == null)
            return null;
        return withTracing(context, new MethodParameters("tp_getattro", Arrays.asList(obj, name)), () -> handlerStandardTpGetattroKt(context, obj.obj, name.obj));
    }

    @CPythonAdapterJavaMethod(cName = "standard_tp_setattro")
    @CPythonFunction(
            argCTypes = {CType.PyObject, CType.PyObject, CType.PyObject},
            argConverters = {ObjectConverter.StandardConverter, ObjectConverter.StandardConverter, ObjectConverter.StandardConverter}
    )
    public static void handlerStandardTpSetattro(ConcolicRunContext context, SymbolForCPython obj, SymbolForCPython name, SymbolForCPython value) {
        if (obj.obj == null || name.obj == null || value.obj == null)
            return;
        withTracing(context, new MethodParametersNoReturn("tp_setattro", Arrays.asList(obj, name, value)), unit(() -> handlerStandardTpSetattroKt(context, obj.obj, name.obj, value.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "create_empty_object")
    @CPythonFunction(
            argCTypes = {CType.PyObject},
            argConverters = {ObjectConverter.RefConverter},
            addToSymbolicAdapter = false
    )
    public static SymbolForCPython handlerCreateEmptyObject(ConcolicRunContext context, long type_ref) {
        PythonObject ref = new PythonObject(type_ref);
        return methodWrapper(context, new EmptyObjectCreation(ref), () -> handlerCreateEmptyObjectKt(context, ref));
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_method_int")
    @SymbolicMethod(id = SymbolicMethodId.Int)
    public static SymbolForCPython symbolicMethodInt(ConcolicRunContext context, @Nullable SymbolForCPython self, SymbolForCPython[] args) {
        assert(self == null);
        return withTracing(context, new SymbolicMethodParameters("int", null, args), () -> symbolicMethodIntKt(context, args));
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_method_float")
    @SymbolicMethod(id = SymbolicMethodId.Float)
    public static SymbolForCPython symbolicMethodFloat(ConcolicRunContext context, @Nullable SymbolForCPython self, SymbolForCPython[] args) {
        assert(self == null);
        return withTracing(context, new SymbolicMethodParameters("float", null, args), () -> symbolicMethodFloatKt(context, args));
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_method_list_append")
    @SymbolicMethod(id = SymbolicMethodId.ListAppend)
    public static SymbolForCPython symbolicMethodListAppend(ConcolicRunContext context, @Nullable SymbolForCPython self, SymbolForCPython[] args) {
        return withTracing(context, new SymbolicMethodParameters("list_append", self, args), () -> symbolicMethodListAppendKt(context, self, args));
    }
    @SymbolicMethodDescriptor(nativeTypeName = "PyList_Type", nativeMemberName = "append")
    public MemberDescriptor listAppendDescriptor = new MethodDescriptor(SymbolicMethodId.ListAppend);

    @CPythonAdapterJavaMethod(cName = "symbolic_method_list_insert")
    @SymbolicMethod(id = SymbolicMethodId.ListInsert)
    public static SymbolForCPython symbolicMethodListInsert(ConcolicRunContext context, @Nullable SymbolForCPython self, SymbolForCPython[] args) {
        return withTracing(context, new SymbolicMethodParameters("list_insert", self, args), () -> symbolicMethodListInsertKt(context, self, args));
    }
    @SymbolicMethodDescriptor(nativeTypeName = "PyList_Type", nativeMemberName = "insert")
    public MemberDescriptor listInsertDescriptor = new MethodDescriptor(SymbolicMethodId.ListInsert);

    @CPythonAdapterJavaMethod(cName = "symbolic_method_list_pop")
    @SymbolicMethod(id = SymbolicMethodId.ListPop)
    public static SymbolForCPython symbolicMethodListPop(ConcolicRunContext context, @Nullable SymbolForCPython self, SymbolForCPython[] args) {
        return withTracing(context, new SymbolicMethodParameters("list_pop", self, args), () -> symbolicMethodListPopKt(context, self, args));
    }
    @SymbolicMethodDescriptor(nativeTypeName = "PyList_Type", nativeMemberName = "pop")
    public MemberDescriptor listPopDescriptor = new MethodDescriptor(SymbolicMethodId.ListPop);

    @CPythonAdapterJavaMethod(cName = "symbolic_method_list_extend")
    @SymbolicMethod(id = SymbolicMethodId.ListExtend)
    public static SymbolForCPython symbolicMethodListExtend(ConcolicRunContext context, @Nullable SymbolForCPython self, SymbolForCPython[] args) {
        return withTracing(context, new SymbolicMethodParameters("list_extend", self, args), () -> symbolicMethodListExtendKt(context, self, args));
    }
    @SymbolicMethodDescriptor(nativeTypeName = "PyList_Type", nativeMemberName = "extend")
    public MemberDescriptor listExtendDescriptor = new MethodDescriptor(SymbolicMethodId.ListExtend);

    @CPythonAdapterJavaMethod(cName = "symbolic_method_list_clear")
    @SymbolicMethod(id = SymbolicMethodId.ListClear)
    public static SymbolForCPython symbolicMethodListClear(ConcolicRunContext context, @Nullable SymbolForCPython self, SymbolForCPython[] args) {
        return withTracing(context, new SymbolicMethodParameters("list_clear", self, args), () -> symbolicMethodListClearKt(context, self, args));
    }
    @SymbolicMethodDescriptor(nativeTypeName = "PyList_Type", nativeMemberName = "clear")
    public MemberDescriptor listClearDescriptor = new MethodDescriptor(SymbolicMethodId.ListClear);

    @SymbolicMethodDescriptor(nativeTypeName = "PyList_Type", nativeMemberName = "index")
    public MemberDescriptor listIndexDescriptor = new ApproximationDescriptor(ApproximationId.ListIndex);

    @SymbolicMethodDescriptor(nativeTypeName = "PyList_Type", nativeMemberName = "reverse")
    public MemberDescriptor listReverseDescriptor = new ApproximationDescriptor(ApproximationId.ListReverse);

    @SymbolicMemberDescriptor(nativeTypeName = "PySlice_Type", nativeMemberName = "start")
    public MemberDescriptor sliceStartDescriptor = SliceStartDescriptor.INSTANCE;

    @SymbolicMemberDescriptor(nativeTypeName = "PySlice_Type", nativeMemberName = "stop")
    public MemberDescriptor sliceStopDescriptor = SliceStopDescriptor.INSTANCE;

    @SymbolicMemberDescriptor(nativeTypeName = "PySlice_Type", nativeMemberName = "step")
    public MemberDescriptor sliceStepDescriptor = SliceStepDescriptor.INSTANCE;

    public MemberDescriptor pythonMethodDescriptor = PythonMethodDescriptor.INSTANCE;
}
