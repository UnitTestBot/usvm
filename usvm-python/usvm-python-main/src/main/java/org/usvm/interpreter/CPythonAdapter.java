package org.usvm.interpreter;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.usvm.annotations.CPythonAdapterJavaMethod;
import org.usvm.language.*;
import org.usvm.machine.MockHeader;
import org.usvm.machine.interpreters.PythonObject;
import org.usvm.machine.interpreters.operations.descriptors.*;
import org.usvm.machine.interpreters.operations.tracing.*;
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;

import static org.usvm.machine.interpreters.operations.CommonKt.*;
import static org.usvm.machine.interpreters.operations.ConstantsKt.handlerLoadConstKt;
import static org.usvm.machine.interpreters.operations.ControlKt.handlerForkKt;
import static org.usvm.machine.interpreters.operations.FloatKt.*;
import static org.usvm.machine.interpreters.operations.ListKt.*;
import static org.usvm.machine.interpreters.operations.LongKt.*;
import static org.usvm.machine.interpreters.operations.MethodNotificationsKt.*;
import static org.usvm.machine.interpreters.operations.RangeKt.*;
import static org.usvm.machine.interpreters.operations.SliceKt.handlerCreateSliceKt;
import static org.usvm.machine.interpreters.operations.TupleKt.*;
import static org.usvm.machine.interpreters.operations.VirtualKt.*;

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
    public long symbolicIntConstructorRef;
    public long symbolicFloatConstructorRef;
    public MemberDescriptor listAppendDescriptor = ListAppendDescriptor.INSTANCE;
    public MemberDescriptor listPopDescriptor = ListPopDescriptor.INSTANCE;
    public MemberDescriptor listInsertDescriptor = ListInsertDescriptor.INSTANCE;
    public MemberDescriptor sliceStartDescriptor = SliceStartDescriptor.INSTANCE;
    public MemberDescriptor sliceStopDescriptor = SliceStopDescriptor.INSTANCE;
    public MemberDescriptor sliceStepDescriptor = SliceStepDescriptor.INSTANCE;
    public native void initializePython(String pythonHome);
    public native void finalizePython();
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
    public static native long getFunctionFromFrame(long frameRef);
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
    public native int typeHasTpIter(long type);
    public native int typeHasStandardNew(long type);
    public native long callStandardNew(long type);
    public native Throwable extractException(long exception);
    public native void decref(long object);
    public native void incref(long object);
    public native String checkForIllegalOperation();
    public native long typeLookup(long typeRef, String name);
    @Nullable
    public native MemberDescriptor getSymbolicDescriptor(long concreteDescriptorRef);
    public native long constructListAppendMethod(SymbolForCPython symbolicList);
    public native long constructListPopMethod(SymbolForCPython symbolicList);
    public native long constructListInsertMethod(SymbolForCPython symbolicList);

    static {
        System.loadLibrary("cpythonadapter");
    }

    @CPythonAdapterJavaMethod(cName = "instruction")
    public static void handlerInstruction(@NotNull ConcolicRunContext context, long frameRef) {
        context.curOperation = null;
        int instruction = getInstructionFromFrame(frameRef);
        long functionRef = getFunctionFromFrame(frameRef);
        PythonObject function = new PythonObject(functionRef);
        PathTracingKt.withTracing(context, new NextInstruction(new PythonInstruction(instruction), function), () -> Unit.INSTANCE);
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
        return PathTracingKt.withTracing(
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
    public static SymbolForCPython handlerLoadConst(ConcolicRunContext context, long ref) {
        PythonObject obj = new PythonObject(ref);
        return PathTracingKt.withTracing(context, new LoadConstParameters(obj), () -> wrap(handlerLoadConstKt(context, obj)));
    }

    @CPythonAdapterJavaMethod(cName = "fork_notify")
    public static void handlerFork(ConcolicRunContext context, SymbolForCPython cond) {
        if (cond.obj == null)
            return;
        PathTracingKt.withTracing(context, new Fork(cond), unit(() -> handlerForkKt(context, cond.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "fork_result")
    public static void handlerForkResult(ConcolicRunContext context, SymbolForCPython cond, boolean result) {
        PathTracingKt.handlerForkResultKt(context, cond, result);
    }

    @CPythonAdapterJavaMethod(cName = "unpack")
    public static void handlerUnpack(ConcolicRunContext context, SymbolForCPython iterable, int count) {
        if (iterable.obj == null)
            return;
        PathTracingKt.withTracing(context, new Unpack(iterable, count), unit(() -> handlerUnpackKt(context, iterable.obj, count)));
    }

    @CPythonAdapterJavaMethod(cName = "is_op")
    public static void handlerIsOp(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return;
        PathTracingKt.withTracing(context, new MethodParametersNoReturn("is_op", Arrays.asList(left, right)), unit(() -> handlerIsOpKt(context, left.obj, right.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "none_check")
    public static void handlerNoneCheck(ConcolicRunContext context, SymbolForCPython on) {
        if (on.obj == null)
            return;
        PathTracingKt.withTracing(context, new MethodParametersNoReturn("none_check", Collections.singletonList(on)), unit(() -> handlerNoneCheckKt(context, on.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "gt_long")
    public static SymbolForCPython handlerGTLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("gt_long", Arrays.asList(left, right)), () -> handlerGTLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "lt_long")
    public static SymbolForCPython handlerLTLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("lt_long", Arrays.asList(left, right)), () -> handlerLTLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "eq_long")
    public static SymbolForCPython handlerEQLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("eq_long", Arrays.asList(left, right)), () -> handlerEQLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "ne_long")
    public static SymbolForCPython handlerNELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("ne_long", Arrays.asList(left, right)), () -> handlerNELongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "ge_long")
    public static SymbolForCPython handlerGELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("ge_long", Arrays.asList(left, right)), () -> handlerGELongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "le_long")
    public static SymbolForCPython handlerLELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("le_long", Arrays.asList(left, right)), () -> handlerLELongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "add_long")
    public static SymbolForCPython handlerADDLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("add_long", Arrays.asList(left, right)), () -> handlerADDLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "sub_long")
    public static SymbolForCPython handlerSUBLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("sub_long", Arrays.asList(left, right)), () -> handlerSUBLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "mul_long")
    public static SymbolForCPython handlerMULLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("mul_long", Arrays.asList(left, right)), () -> handlerMULLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "div_long")
    public static SymbolForCPython handlerDIVLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("div_long", Arrays.asList(left, right)), () -> handlerDIVLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "rem_long")
    public static SymbolForCPython handlerREMLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("rem_long", Arrays.asList(left, right)), () -> handlerREMLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "pow_long")
    public static SymbolForCPython handlerPOWLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("pow_long", Arrays.asList(left, right)), () -> handlerPOWLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "true_div_long")
    public static SymbolForCPython handlerTrueDivLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("true_div_long", Arrays.asList(left, right)), () -> handlerTrueDivLongKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_int_cast")
    public static SymbolForCPython handlerIntCast(ConcolicRunContext context, SymbolForCPython obj) {
        if (obj.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("int_cast", Collections.singletonList(obj)), () -> handlerIntCastKt(context, obj.obj));
    }

    @CPythonAdapterJavaMethod(cName = "gt_float")
    public static SymbolForCPython handlerGTFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("gt_float", Arrays.asList(left, right)), () -> handlerGTFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "lt_float")
    public static SymbolForCPython handlerLTFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("lt_float", Arrays.asList(left, right)), () -> handlerLTFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "eq_float")
    public static SymbolForCPython handlerEQFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("eq_float", Arrays.asList(left, right)), () -> handlerEQFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "ne_float")
    public static SymbolForCPython handlerNEFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("ne_float", Arrays.asList(left, right)), () -> handlerNEFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "ge_float")
    public static SymbolForCPython handlerGEFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("ge_float", Arrays.asList(left, right)), () -> handlerGEFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "le_float")
    public static SymbolForCPython handlerLEFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("le_float", Arrays.asList(left, right)), () -> handlerLEFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "add_float")
    public static SymbolForCPython handlerADDFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("add_float", Arrays.asList(left, right)), () -> handlerADDFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "sub_float")
    public static SymbolForCPython handlerSUBFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("sub_float", Arrays.asList(left, right)), () -> handlerSUBFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "mul_float")
    public static SymbolForCPython handlerMULFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("mul_float", Arrays.asList(left, right)), () -> handlerMULFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "div_float")
    public static SymbolForCPython handlerDIVFloat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("div_float", Arrays.asList(left, right)), () -> handlerDIVFloatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_float_cast")
    public static SymbolForCPython handlerFloatCast(ConcolicRunContext context, SymbolForCPython obj) {
        if (obj.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("float_cast", Collections.singletonList(obj)), () -> handlerFloatCastKt(context, obj.obj));
    }

    @CPythonAdapterJavaMethod(cName = "bool_and")
    public static SymbolForCPython handlerAND(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("bool_and", Arrays.asList(left, right)), () -> handlerAndKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "create_list")
    public static SymbolForCPython handlerCreateList(ConcolicRunContext context, SymbolForCPython[] elements) {
        if (Arrays.stream(elements).anyMatch(elem -> elem.obj == null))
            return null;
        ListCreation event = new ListCreation(Arrays.asList(elements));
        return PathTracingKt.withTracing(context, event, () -> wrap(handlerCreateListKt(context, Arrays.stream(elements).map(s -> s.obj))));
    }

    @CPythonAdapterJavaMethod(cName = "create_tuple")
    public static SymbolForCPython handlerCreateTuple(ConcolicRunContext context, SymbolForCPython[] elements) {
        if (Arrays.stream(elements).anyMatch(elem -> elem.obj == null))
            return null;
        TupleCreation event = new TupleCreation(Arrays.asList(elements));
        return PathTracingKt.withTracing(context, event, () -> wrap(handlerCreateTupleKt(context, Arrays.stream(elements).map(s -> s.obj))));
    }

    @CPythonAdapterJavaMethod(cName = "create_range")
    public static SymbolForCPython handlerCreateRange(ConcolicRunContext context, SymbolForCPython start, SymbolForCPython stop, SymbolForCPython step) {
        if (start.obj == null || stop.obj == null || step.obj == null)
            return null;
        MethodParameters event = new MethodParameters("create_range", Arrays.asList(start, stop, step));
        return PathTracingKt.withTracing(context, event, () -> wrap(handlerCreateRangeKt(context, start.obj, stop.obj, step.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "create_slice")
    public static SymbolForCPython handlerCreateSlice(ConcolicRunContext context, SymbolForCPython start, SymbolForCPython stop, SymbolForCPython step) {
        if (start.obj == null || stop.obj == null || step.obj == null)
            return null;
        MethodParameters event = new MethodParameters("create_slice", Arrays.asList(start, stop, step));
        return PathTracingKt.withTracing(context, event, () -> wrap(handlerCreateSliceKt(context, start.obj, stop.obj, step.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "range_iter")
    public static SymbolForCPython handlerRangeIter(ConcolicRunContext context, SymbolForCPython range) {
        if (range.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("range_iter", Collections.singletonList(range)), () -> handlerRangeIterKt(context, range.obj));
    }

    @CPythonAdapterJavaMethod(cName = "range_iterator_next")
    public static SymbolForCPython handlerRangeIteratorNext(ConcolicRunContext context, SymbolForCPython rangeIterator) {
        if (rangeIterator.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("range_iterator_next", Collections.singletonList(rangeIterator)), () -> handlerRangeIteratorNextKt(context, rangeIterator.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_get_item")
    public static SymbolForCPython handlerListGetItem(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython index) {
        if (list.obj == null || index.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_get_item", Arrays.asList(list, index)), () -> handlerListGetItemKt(context, list.obj, index.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_extend")
    public static SymbolForCPython handlerListExtend(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython tuple) {
        if (list.obj == null || tuple.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_extend", Arrays.asList(list, tuple)), () -> handlerListExtendKt(context, list.obj, tuple.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_concat")
    public static SymbolForCPython handlerListConcat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_concat", Arrays.asList(left, right)), () -> handlerListConcatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_inplace_concat")
    public static SymbolForCPython handlerListInplaceConcat(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_inplace_concat", Arrays.asList(left, right)), () -> handlerListInplaceConcatKt(context, left.obj, right.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_append")
    public static SymbolForCPython handlerListAppend(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython elem) {
        if (list.obj == null || elem.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_append", Arrays.asList(list, elem)), () -> handlerListAppendKt(context, list.obj, elem.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_set_item")
    public static void handlerListSetItem(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython index, SymbolForCPython value) {
        if (list.obj == null || index.obj == null || value.obj == null)
            return;
        PathTracingKt.withTracing(context, new MethodParametersNoReturn("list_set_item", Arrays.asList(list, index, value)), unit(() -> handlerListSetItemKt(context, list.obj, index.obj, value.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "list_get_size")
    public static SymbolForCPython handlerListGetSize(ConcolicRunContext context, SymbolForCPython list) {
        if (list.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_get_size", Collections.singletonList(list)), () -> handlerListGetSizeKt(context, list.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_iter")
    public static SymbolForCPython handlerListIter(ConcolicRunContext context, SymbolForCPython list) {
        if (list.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_iter", Collections.singletonList(list)), () -> handlerListIterKt(context, list.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_iterator_next")
    public static SymbolForCPython handlerListIteratorNext(ConcolicRunContext context, SymbolForCPython iterator) {
        if (iterator.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_iterator_next", Collections.singletonList(iterator)), () -> handlerListIteratorNextKt(context, iterator.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_pop")
    public static SymbolForCPython handlerListPop(ConcolicRunContext context, SymbolForCPython list) {
        if (list.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_pop", Collections.singletonList(list)), () -> handlerListPopKt(context, list.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_pop_ind")
    public static SymbolForCPython handlerListPopInd(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython ind) {
        if (list.obj == null || ind.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("list_pop", Arrays.asList(list, ind)), () -> handlerListPopIndKt(context, list.obj, ind.obj));
    }

    @CPythonAdapterJavaMethod(cName = "list_insert")
    public static void handlerListInsert(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython index, SymbolForCPython value) {
        if (list.obj == null || index.obj == null || value.obj == null)
            return;
        PathTracingKt.withTracing(context, new MethodParametersNoReturn("list_insert", Arrays.asList(list, index, value)), unit(() -> handlerListInsertKt(context, list.obj, index.obj, value.obj)));
    }

    @CPythonAdapterJavaMethod(cName = "tuple_get_size")
    @Nullable
    public static SymbolForCPython handlerTupleGetSize(ConcolicRunContext context, @NotNull SymbolForCPython tuple) {
        if (tuple.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("tuple_get_size", Collections.singletonList(tuple)), () -> handlerTupleGetSizeKt(context, tuple.obj));
    }

    @CPythonAdapterJavaMethod(cName = "tuple_get_item")
    public static SymbolForCPython handlerTupleGetItem(ConcolicRunContext context, SymbolForCPython tuple, SymbolForCPython index) {
        if (tuple.obj == null || index.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("tuple_get_item", Arrays.asList(tuple, index)), () -> handlerTupleGetItemKt(context, tuple.obj, index.obj));
    }

    @CPythonAdapterJavaMethod(cName = "tuple_iter")
    public static SymbolForCPython handlerTupleIter(ConcolicRunContext context, SymbolForCPython tuple) {
        if (tuple.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("tuple_iter", Collections.singletonList(tuple)), () -> handlerTupleIterKt(context, tuple.obj));
    }

    @CPythonAdapterJavaMethod(cName = "tuple_iterator_next")
    public static SymbolForCPython handlerTupleIteratorNext(ConcolicRunContext context, SymbolForCPython iterator) {
        if (iterator.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("tuple_iterator_next", Collections.singletonList(iterator)), () -> handlerTupleIteratorNextKt(context, iterator.obj));
    }

    @CPythonAdapterJavaMethod(cName = "function_call")
    public static void handlerFunctionCall(ConcolicRunContext context, long codeRef) {
        PythonObject code = new PythonObject(codeRef);
        PathTracingKt.withTracing(context, new PythonFunctionCall(code), () -> Unit.INSTANCE);
    }

    @CPythonAdapterJavaMethod(cName = "function_return")
    public static void handlerReturn(ConcolicRunContext context, long codeRef) {
        PathTracingKt.withTracing(context, new PythonReturn(new PythonObject(codeRef)), () -> Unit.INSTANCE);
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_virtual_unary_fun")
    public static SymbolForCPython handlerVirtualUnaryFun(ConcolicRunContext context, SymbolForCPython obj) {
        if (obj.obj == null)
            return null;
        return methodWrapper(context, new MethodParameters("virtual_unary_fun", Collections.singletonList(obj)), () -> virtualCallSymbolKt(context));
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_virtual_binary_fun")
    public static SymbolForCPython handlerVirtualBinaryFun(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("virtual_binary_fun", Arrays.asList(left, right)), () -> virtualCallSymbolKt(context));
    }

    @CPythonAdapterJavaMethod(cName = "symbolic_isinstance")
    public static SymbolForCPython handlerIsinstance(ConcolicRunContext context, SymbolForCPython obj, long typeRef) {
        if (obj.obj == null)
            return null;
        PythonObject type = new PythonObject(typeRef);
        return methodWrapper(context, new IsinstanceCheck(obj, type), () -> handlerIsinstanceKt(context, obj.obj, type));
    }

    @CPythonAdapterJavaMethod(cName = "fixate_type")
    public static void fixateType(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython obj) {
        if (obj.obj == null)
            return;
        fixateTypeKt(context, obj.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_bool")
    public static void notifyNbBool(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython symbol) {
        if (symbol.obj == null)
            return;
        context.curOperation = new MockHeader(NbBoolMethod.INSTANCE, Collections.singletonList(symbol.obj), symbol.obj);
        nbBoolKt(context, symbol.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_int")
    public static void notifyNbInt(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython symbol) {
        if (symbol.obj == null)
            return;
        context.curOperation = new MockHeader(NbIntMethod.INSTANCE, Collections.singletonList(symbol.obj), symbol.obj);
        nbIntKt(context, symbol.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_add")
    public static void notifyNbAdd(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython left, @NotNull SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return;
        context.curOperation = new MockHeader(NbAddMethod.INSTANCE, Arrays.asList(left.obj, right.obj), null);
        nbAddKt(context, left.obj, right.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_subtract")
    public static void notifyNbSubtract(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null)
            return;
        context.curOperation = new MockHeader(NbSubtractMethod.INSTANCE, Arrays.asList(left.obj, right.obj), left.obj);
        nbSubtractKt(context, left.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_multiply")
    public static void notifyNbMultiply(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython left, @NotNull SymbolForCPython right) {
        if (left.obj == null || right.obj == null)
            return;
        context.curOperation = new MockHeader(NbMultiplyMethod.INSTANCE, Arrays.asList(left.obj, right.obj), null);
        nbMultiplyKt(context, left.obj, right.obj);
    }

    @CPythonAdapterJavaMethod(cName = "nb_matrix_multiply")
    public static void notifyNbMatrixMultiply(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython left, SymbolForCPython right) {
        if (left.obj == null)
            return;
        context.curOperation = new MockHeader(NbMatrixMultiplyMethod.INSTANCE, Arrays.asList(left.obj, right.obj), left.obj);
        nbMatrixMultiplyKt(context, left.obj);
    }

    @CPythonAdapterJavaMethod(cName = "sq_length")
    public static void notifySqLength(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython on) {
        if (on.obj == null)
            return;
        context.curOperation = new MockHeader(SqLengthMethod.INSTANCE, Collections.singletonList(on.obj), on.obj);
        sqLengthKt(context, on.obj);
    }

    @CPythonAdapterJavaMethod(cName = "mp_subscript")
    public static void notifyMpSubscript(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython storage, SymbolForCPython item) {
        if (storage.obj == null)
            return;
        context.curOperation = new MockHeader(MpSubscriptMethod.INSTANCE, Arrays.asList(storage.obj, item.obj), storage.obj);
        mpSubscriptKt(context, storage.obj);
    }

    @CPythonAdapterJavaMethod(cName = "mp_ass_subscript")
    public static void notifyMpAssSubscript(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython storage, SymbolForCPython item, SymbolForCPython value) {
        if (storage.obj == null)
            return;
        context.curOperation = new MockHeader(MpAssSubscriptMethod.INSTANCE, Arrays.asList(storage.obj, item.obj, value.obj), storage.obj);
        mpAssSubscriptKt(context, storage.obj);
    }

    @CPythonAdapterJavaMethod(cName = "tp_richcompare")
    public static void notifyTpRichcmp(@NotNull ConcolicRunContext context, int op, @NotNull SymbolForCPython left, @NotNull SymbolForCPython right) {
        if (left.obj == null)
            return;
        context.curOperation = new MockHeader(new TpRichcmpMethod(op), Arrays.asList(left.obj, right.obj), left.obj);
        tpRichcmpKt(context, left.obj);
    }

    @CPythonAdapterJavaMethod(cName = "tp_getattro")
    public static void notifyTpGetattro(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython on, @NotNull SymbolForCPython name) {
        if (on.obj == null || name.obj == null)
            return;
        context.curOperation = new MockHeader(TpGetattro.INSTANCE, Arrays.asList(on.obj, name.obj), on.obj);
        tpGetattroKt(context, on.obj, name.obj);
    }

    @CPythonAdapterJavaMethod(cName = "tp_iter")
    public static void notifyTpIter(@NotNull ConcolicRunContext context, @NotNull SymbolForCPython on) {
        if (on.obj == null)
            return;
        context.curOperation = new MockHeader(TpIterMethod.INSTANCE, Collections.singletonList(on.obj), on.obj);
        tpIterKt(context, on.obj);
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
    public static void lostSymbolicValue(ConcolicRunContext context, String description) {
        lostSymbolicValueKt(context, description);
    }

    @CPythonAdapterJavaMethod(cName = "standard_tp_getattro")
    public static SymbolForCPython handlerStandardTpGetattro(ConcolicRunContext context, @NotNull SymbolForCPython obj, SymbolForCPython name) {
        if (obj.obj == null || name.obj == null)
            return null;
        return PathTracingKt.withTracing(context, new MethodParameters("tp_getattro", Arrays.asList(obj, name)), () -> handlerStandardTpGetattroKt(context, obj.obj, name.obj));
    }
}
