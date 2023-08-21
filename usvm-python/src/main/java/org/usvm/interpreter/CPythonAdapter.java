package org.usvm.interpreter;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.usvm.machine.MockHeader;
import org.usvm.machine.interpreters.PythonObject;
import org.usvm.machine.interpreters.operations.tracing.*;
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject;
import org.usvm.language.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;

import static org.usvm.machine.interpreters.operations.CommonKt.*;
import static org.usvm.machine.interpreters.operations.ListKt.*;
import static org.usvm.machine.interpreters.operations.LongKt.*;
import static org.usvm.machine.interpreters.operations.MethodNotificationsKt.*;
import static org.usvm.machine.interpreters.operations.RangeKt.*;
import static org.usvm.machine.interpreters.operations.TupleKt.*;
import static org.usvm.machine.interpreters.operations.VirtualKt.*;
import static org.usvm.machine.interpreters.operations.tracing.PathTracingKt.handlerForkResultKt;
import static org.usvm.machine.interpreters.operations.tracing.PathTracingKt.withTracing;

@SuppressWarnings("unused")
public class CPythonAdapter {
    public boolean isInitialized = false;
    public long thrownException = 0L;
    public long thrownExceptionType = 0L;
    public long javaExceptionType = 0L;
    public int pyEQ;
    public int pyNE;
    public int pyLE;
    public int pyLT;
    public int pyGE;
    public int pyGT;
    public native void initializePython();
    public native void finalizePython();
    public native long getNewNamespace();  // returns reference to a new dict
    public native void addName(long dict, long object, String name);
    public native int concreteRun(long globals, String code, boolean print_error_message);  // returns 0 on success
    public native long eval(long globals, String obj, boolean print_error_message);  // returns PyObject *
    public native long concreteRunOnFunctionRef(long functionRef, long[] concreteArgs);
    public native long concolicRun(long functionRef, long[] concreteArgs, long[] virtualArgs, SymbolForCPython[] symbolicArgs, ConcolicRunContext context, boolean print_error_message);
    public native void printPythonObject(long object);
    public native long[] getIterableElements(long iterable);
    public native String getPythonObjectRepr(long object);
    public native long getAddressOfReprFunction(long object);
    public native String getPythonObjectTypeName(long object);
    public native long getPythonObjectType(long object);
    public native String getNameOfPythonType(long type);
    public static native int getInstructionFromFrame(long frameRef);
    public static native long getFunctionFromFrame(long frameRef);
    public native long allocateVirtualObject(VirtualPythonObject object);
    public native long makeList(long[] elements);
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
    public native int typeHasTpIter(long type);
    public native int typeHasStandardNew(long type);
    public native long callStandardNew(long type);
    public native Throwable extractException(long exception);
    public native void decref(long object);

    static {
        System.loadLibrary("cpythonadapter");
    }

    public static void handlerInstruction(@NotNull ConcolicRunContext context, long frameRef) {
        context.curOperation = null;
        int instruction = getInstructionFromFrame(frameRef);
        long functionRef = getFunctionFromFrame(frameRef);
        PythonObject function = new PythonObject(functionRef);
        withTracing(context, new NextInstruction(new PythonInstruction(instruction), function), () -> Unit.INSTANCE);
    }

    private static SymbolForCPython wrap(UninterpretedSymbolicPythonObject obj) {
        if (obj == null)
            return null;
        return new SymbolForCPython(obj);
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

    public static SymbolForCPython handlerLoadConst(ConcolicRunContext context, long ref) {
        PythonObject obj = new PythonObject(ref);
        return withTracing(context, new LoadConstParameters(obj), () -> wrap(org.usvm.machine.interpreters.operations.ConstantsKt.handlerLoadConstKt(context, obj)));
    }

    public static void handlerFork(ConcolicRunContext context, SymbolForCPython cond) {
        withTracing(context, new Fork(cond), unit(() -> org.usvm.machine.interpreters.operations.ControlKt.handlerForkKt(context, cond.obj)));
    }

    public static void handlerForkResult(ConcolicRunContext context, SymbolForCPython cond, boolean result) {
        handlerForkResultKt(context, cond, result);
    }

    public static SymbolForCPython handlerGTLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("gt_long", Arrays.asList(left, right)), () -> handlerGTLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerLTLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("lt_long", Arrays.asList(left, right)), () -> handlerLTLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerEQLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("eq_long", Arrays.asList(left, right)), () -> handlerEQLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerNELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("ne_long", Arrays.asList(left, right)), () -> handlerNELongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerGELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("ge_long", Arrays.asList(left, right)), () -> handlerGELongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerLELong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("le_long", Arrays.asList(left, right)), () -> handlerLELongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerADDLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("add_long", Arrays.asList(left, right)), () -> handlerADDLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerSUBLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("sub_long", Arrays.asList(left, right)), () -> handlerSUBLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerMULLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("mul_long", Arrays.asList(left, right)), () -> handlerMULLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerDIVLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("div_long", Arrays.asList(left, right)), () -> handlerDIVLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerREMLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("rem_long", Arrays.asList(left, right)), () -> handlerREMLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerPOWLong(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("pow_long", Arrays.asList(left, right)), () -> handlerPOWLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerAND(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("bool_and", Arrays.asList(left, right)), () -> handlerAndKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerCreateList(ConcolicRunContext context, SymbolForCPython[] elements) {
        ListCreation event = new ListCreation(Arrays.asList(elements));
        return withTracing(context, event, () -> wrap(handlerCreateListKt(context, Arrays.stream(elements).map(s -> s.obj))));
    }

    public static SymbolForCPython handlerCreateTuple(ConcolicRunContext context, SymbolForCPython[] elements) {
        TupleCreation event = new TupleCreation(Arrays.asList(elements));
        return withTracing(context, event, () -> wrap(handlerCreateTupleKt(context, Arrays.stream(elements).map(s -> s.obj))));
    }

    public static SymbolForCPython handlerCreateRange(ConcolicRunContext context, SymbolForCPython start, SymbolForCPython stop, SymbolForCPython step) {
        RangeCreation event = new RangeCreation(start, stop, step);
        return withTracing(context, event, () -> wrap(handlerCreateRangeKt(context, start.obj, stop.obj, step.obj)));
    }

    public static SymbolForCPython handlerRangeIter(ConcolicRunContext context, SymbolForCPython range) {
        return methodWrapper(context, new MethodParameters("range_iter", Collections.singletonList(range)), () -> handlerRangeIterKt(context, range.obj));
    }

    public static SymbolForCPython handlerRangeIteratorNext(ConcolicRunContext context, SymbolForCPython rangeIterator) {
        return methodWrapper(context, new MethodParameters("range_iterator_next", Collections.singletonList(rangeIterator)), () -> handlerRangeIteratorNextKt(context, rangeIterator.obj));
    }

    public static SymbolForCPython handlerListGetItem(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython index) {
        return methodWrapper(context, new MethodParameters("list_get_item", Arrays.asList(list, index)), () -> handlerListGetItemKt(context, list.obj, index.obj));
    }

    public static SymbolForCPython handlerListExtend(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython tuple) {
        return methodWrapper(context, new MethodParameters("list_extend", Arrays.asList(list, tuple)), () -> handlerListExtendKt(context, list.obj, tuple.obj));
    }

    public static SymbolForCPython handlerListAppend(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython elem) {
        return methodWrapper(context, new MethodParameters("list_append", Arrays.asList(list, elem)), () -> handlerListAppendKt(context, list.obj, elem.obj));
    }

    public static void handlerListSetItem(ConcolicRunContext context, SymbolForCPython list, SymbolForCPython index, SymbolForCPython value) {
        withTracing(context, new MethodParametersNoReturn("list_set_item", Arrays.asList(list, index, value)), unit(() -> handlerListSetItemKt(context, list.obj, index.obj, value.obj)));
    }

    public static SymbolForCPython handlerListGetSize(ConcolicRunContext context, SymbolForCPython list) {
        return methodWrapper(context, new MethodParameters("list_get_size", Collections.singletonList(list)), () -> handlerListGetSizeKt(context, list.obj));
    }

    public static SymbolForCPython handlerListIter(ConcolicRunContext context, SymbolForCPython list) {
        return methodWrapper(context, new MethodParameters("list_iter", Collections.singletonList(list)), () -> handlerListIterKt(context, list.obj));
    }

    public static SymbolForCPython handlerListIteratorNext(ConcolicRunContext context, SymbolForCPython iterator) {
        return methodWrapper(context, new MethodParameters("list_iterator_next", Collections.singletonList(iterator)), () -> handlerListIteratorNextKt(context, iterator.obj));
    }

    public static SymbolForCPython handlerTupleIter(ConcolicRunContext context, SymbolForCPython tuple) {
        return methodWrapper(context, new MethodParameters("tuple_iter", Collections.singletonList(tuple)), () -> handlerTupleIterKt(context, tuple.obj));
    }

    public static SymbolForCPython handlerTupleIteratorNext(ConcolicRunContext context, SymbolForCPython iterator) {
        return methodWrapper(context, new MethodParameters("tuple_iterator_next", Collections.singletonList(iterator)), () -> handlerTupleIteratorNextKt(context, iterator.obj));
    }

    public static void handlerFunctionCall(ConcolicRunContext context, long codeRef) {
        PythonObject code = new PythonObject(codeRef);
        withTracing(context, new PythonFunctionCall(code), () -> Unit.INSTANCE);
    }

    public static void handlerReturn(ConcolicRunContext context, long codeRef) {
        withTracing(context, new PythonReturn(new PythonObject(codeRef)), () -> Unit.INSTANCE);
    }

    public static SymbolForCPython handlerVirtualUnaryFun(ConcolicRunContext context, SymbolForCPython obj) {
        return methodWrapper(context, new MethodParameters("virtual_unary_fun", Collections.singletonList(obj)), () -> org.usvm.machine.interpreters.operations.VirtualKt.virtualCallSymbolKt(context));
    }

    public static SymbolForCPython handlerVirtualBinaryFun(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("virtual_binary_fun", Arrays.asList(left, right)), () -> org.usvm.machine.interpreters.operations.VirtualKt.virtualCallSymbolKt(context));
    }

    public static SymbolForCPython handlerIsinstance(ConcolicRunContext context, SymbolForCPython obj, long typeRef) {
        PythonObject type = new PythonObject(typeRef);
        return methodWrapper(context, new IsinstanceCheck(obj, type), () -> org.usvm.machine.interpreters.operations.CommonKt.handlerIsinstanceKt(context, obj.obj, type));
    }

    public static void fixateType(ConcolicRunContext context, SymbolForCPython obj) {
        fixateTypeKt(context, obj.obj);
    }

    public static void notifyNbBool(@NotNull ConcolicRunContext context, SymbolForCPython symbol) {
        context.curOperation = new MockHeader(NbBoolMethod.INSTANCE, Collections.singletonList(symbol), symbol);
        nbBoolKt(context, symbol.obj);
    }

    public static void notifyNbInt(@NotNull ConcolicRunContext context, SymbolForCPython symbol) {
        context.curOperation = new MockHeader(NbIntMethod.INSTANCE, Collections.singletonList(symbol), symbol);
        nbIntKt(context, symbol.obj);
    }

    public static void notifyNbAdd(@NotNull ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        context.curOperation = new MockHeader(NbAddMethod.INSTANCE, Arrays.asList(left, right), null);
        nbAddKt(context, left.obj, right.obj);
    }

    public static void notifyNbSubtract(@NotNull ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        context.curOperation = new MockHeader(NbSubtractMethod.INSTANCE, Arrays.asList(left, right), left);
        nbSubtractKt(context, left.obj);
    }

    public static void notifyNbMultiply(@NotNull ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        context.curOperation = new MockHeader(NbMultiplyMethod.INSTANCE, Arrays.asList(left, right), null);
        nbMultiplyKt(context, left.obj, right.obj);
    }

    public static void notifyNbMatrixMultiply(@NotNull ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        context.curOperation = new MockHeader(NbMatrixMultiplyMethod.INSTANCE, Arrays.asList(left, right), left);
        nbMatrixMultiplyKt(context, left.obj);
    }

    public static void notifySqLength(@NotNull ConcolicRunContext context, SymbolForCPython on) {
        context.curOperation = new MockHeader(SqLengthMethod.INSTANCE, Collections.singletonList(on), on);
        sqLengthKt(context, on.obj);
    }

    public static void notifyMpSubscript(@NotNull ConcolicRunContext context, SymbolForCPython storage, SymbolForCPython item) {
        context.curOperation = new MockHeader(MpSubscriptMethod.INSTANCE, Arrays.asList(storage, item), storage);
        mpSubscriptKt(context, storage.obj);
    }

    public static void notifyMpAssSubscript(@NotNull ConcolicRunContext context, SymbolForCPython storage, SymbolForCPython item, SymbolForCPython value) {
        context.curOperation = new MockHeader(MpAssSubscriptMethod.INSTANCE, Arrays.asList(storage, item, value), storage);
        mpAssSubscriptKt(context, storage.obj);
    }

    public static void notifyTpRichcmp(@NotNull ConcolicRunContext context, int op, SymbolForCPython left, SymbolForCPython right) {
        context.curOperation = new MockHeader(new TpRichcmpMethod(op), Arrays.asList(left, right), left);
        tpRichcmpKt(context, left.obj);
    }

    public static void notifyTpIter(@NotNull ConcolicRunContext context, SymbolForCPython on) {
        context.curOperation = new MockHeader(TpIterMethod.INSTANCE, Collections.singletonList(on), on);
        tpIterKt(context, on.obj);
    }

    public static boolean virtualNbBool(ConcolicRunContext context, VirtualPythonObject obj) {
        return virtualNbBoolKt(context, obj);
    }

    public static long virtualNbInt(ConcolicRunContext context, VirtualPythonObject obj) {
        return virtualNbIntKt(context, obj).getAddress();
    }

    public static int virtualSqLength(ConcolicRunContext context, VirtualPythonObject obj) {
        return virtualSqLengthKt(context, obj);
    }

    public static long virtualCall(ConcolicRunContext context, int owner) {
        if (context.curOperation != null && owner != -1) {
            context.curOperation.setMethodOwner(context.curOperation.getArgs().get(owner));
        }
        return virtualCallKt(context).getAddress();
    }

    public static void lostSymbolicValue(ConcolicRunContext context, String description) {
        lostSymbolicValueKt(context, description);
    }
}
