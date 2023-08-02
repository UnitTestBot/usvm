package org.usvm.interpreter;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.usvm.interpreter.operations.tracing.*;
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject;
import org.usvm.language.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;

import static org.usvm.interpreter.operations.CommonKt.handlerIsinstanceKt;
import static org.usvm.interpreter.operations.ConstantsKt.*;
import static org.usvm.interpreter.operations.ControlKt.*;
import static org.usvm.interpreter.operations.ListKt.*;
import static org.usvm.interpreter.operations.LongKt.*;
import static org.usvm.interpreter.operations.MethodNotificationsKt.*;
import static org.usvm.interpreter.operations.VirtualKt.*;
import static org.usvm.interpreter.operations.tracing.PathTracingKt.handlerForkResultKt;
import static org.usvm.interpreter.operations.tracing.PathTracingKt.withTracing;

@SuppressWarnings("unused")
public class CPythonAdapter {
    public boolean isInitialized = false;
    public long thrownException = 0L;
    public long thrownExceptionType = 0L;
    public long javaExceptionType = 0L;
    public native void initializePython();
    public native void finalizePython();
    public native long getNewNamespace();  // returns reference to a new dict
    public native void addName(long dict, long object, String name);
    public native int concreteRun(long globals, String code);  // returns 0 on success
    public native long eval(long globals, String obj);  // returns PyObject *
    public native long concreteRunOnFunctionRef(long functionRef, long[] concreteArgs);
    public native long concolicRun(long functionRef, long[] concreteArgs, long[] virtualArgs, SymbolForCPython[] symbolicArgs, ConcolicRunContext context, boolean print_error_message);
    public native void printPythonObject(long object);
    public native long[] getIterableElements(long iterable);
    public native String getPythonObjectRepr(long object);
    public native String getPythonObjectTypeName(long object);
    public native long getPythonObjectType(long object);
    public native String getNameOfPythonType(long type);
    public native long allocateVirtualObject(VirtualPythonObject object);
    public native long makeList(long[] elements);
    public native int typeHasNbBool(long type);
    public native int typeHasNbInt(long type);
    public native int typeHasNbAdd(long type);
    public native int typeHasSqLength(long type);
    public native int typeHasMpLength(long type);
    public native int typeHasMpSubscript(long type);
    public native int typeHasTpRichcmp(long type);
    public native Throwable extractException(long exception);

    static {
        System.loadLibrary("cpythonadapter");
    }

    public static void handlerInstruction(@NotNull ConcolicRunContext context, int instruction) {
        context.curOperation = null;
        withTracing(context, new NextInstruction(new PythonInstruction(instruction)), () -> Unit.INSTANCE);
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
        return withTracing(context, new LoadConstParameters(obj), () -> wrap(handlerLoadConstKt(context, obj)));
    }

    public static void handlerFork(ConcolicRunContext context, SymbolForCPython cond) {
        withTracing(context, new Fork(cond), unit(() -> handlerForkKt(context, cond.obj)));
    }

    public static void handlerForkResult(ConcolicRunContext context, boolean result) {
        handlerForkResultKt(context, result);
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

    public static SymbolForCPython handlerCreateList(ConcolicRunContext context, SymbolForCPython[] elements) {
        ListCreation event = new ListCreation(Arrays.asList(elements));
        return withTracing(context, event, () -> wrap(handlerCreateListKt(context, Arrays.stream(elements).map(s -> s.obj))));
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

    public static void handlerFunctionCall(ConcolicRunContext context, long function) {
        PythonPinnedCallable callable = new PythonPinnedCallable(new PythonObject(function));
        withTracing(context, new PythonFunctionCall(callable), unit(() -> handlerFunctionCallKt(context, callable)));
    }

    public static void handlerReturn(ConcolicRunContext context) {
        withTracing(context, PythonReturn.INSTANCE, unit(() -> handlerReturnKt(context)));
    }

    public static SymbolForCPython handlerVirtualBinaryFun(ConcolicRunContext context, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, new MethodParameters("virtual_binary_fun", Arrays.asList(left, right)), () -> virtualCallSymbolKt(context));
    }

    public static SymbolForCPython handlerIsinstance(ConcolicRunContext context, SymbolForCPython obj, long typeRef) {
        PythonObject type = new PythonObject(typeRef);
        return methodWrapper(context, new IsinstanceCheck(obj, type), () -> handlerIsinstanceKt(context, obj.obj, type));
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

    public static void notifyMpSubscript(@NotNull ConcolicRunContext context, SymbolForCPython storage, SymbolForCPython item) {
        context.curOperation = new MockHeader(MpSubscriptMethod.INSTANCE, Arrays.asList(storage, item), storage);
        mpSubscriptKt(context, storage.obj);
    }

    public static void notifyTpRichcmp(@NotNull ConcolicRunContext context, int op, SymbolForCPython left, SymbolForCPython right) {
        context.curOperation = new MockHeader(new TpRichcmpMethod(op), Arrays.asList(left, right), left);
        tpRichcmpKt(context, left.obj);
    }

    public static boolean virtualNbBool(ConcolicRunContext context, VirtualPythonObject obj) {
        return virtualNbBoolKt(context, obj);
    }

    public static long virtualNbInt(ConcolicRunContext context, VirtualPythonObject obj) {
        return virtualNbIntKt(context, obj).getAddress();
    }

    @NotNull
    public static VirtualPythonObject virtualCall(ConcolicRunContext context, int owner) {
        if (context.curOperation != null && owner != -1) {
            context.curOperation.setMethodOwner(context.curOperation.getArgs().get(owner));
        }
        return virtualCallKt(context);
    }
}
