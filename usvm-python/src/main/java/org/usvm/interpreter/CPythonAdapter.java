package org.usvm.interpreter;

import kotlin.Unit;
import org.usvm.interpreter.operations.tracing.*;
import org.usvm.interpreter.symbolicobjects.UninterpretedSymbolicPythonObject;
import org.usvm.language.PythonInstruction;
import org.usvm.language.PythonPinnedCallable;
import org.usvm.language.SymbolForCPython;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.usvm.interpreter.operations.ConstantsKt.handlerLoadConstLongKt;
import static org.usvm.interpreter.operations.ControlKt.*;
import static org.usvm.interpreter.operations.LongKt.*;
import static org.usvm.interpreter.operations.tracing.PathTracingKt.withTracing;

@SuppressWarnings("unused")
public class CPythonAdapter {
    public boolean isInitialized = false;
    public native void initializePython();
    public native void finalizePython();
    public native long getNewNamespace();  // returns reference to a new dict
    public native int concreteRun(long globals, String code);  // returns 0 on success
    public native long eval(long globals, String obj);  // returns PyObject *
    public native long concreteRunOnFunctionRef(long globals, long functionRef, long[] concreteArgs);
    public native long concolicRun(long globals, long functionRef, long[] concreteArgs, SymbolForCPython[] symbolicArgs, ConcolicRunContext context, boolean print_error_message);
    public native void printPythonObject(long object);
    public native String getPythonObjectRepr(long object);
    public native String getPythonObjectTypeName(long object);
    public native long createInvestigatorObject();

    static {
        System.loadLibrary("cpythonadapter");
    }

    public static void handlerInstruction(ConcolicRunContext context, int instruction) {
        withTracing(context, new NextInstruction(new PythonInstruction(instruction)), () -> Unit.INSTANCE);
    }

    private static SymbolForCPython wrap(UninterpretedSymbolicPythonObject obj) {
        if (obj == null)
            return null;
        return new SymbolForCPython(obj);
    }

    private static SymbolForCPython methodWrapper(
            ConcolicRunContext context,
            int methodId,
            List<SymbolForCPython> operands,
            Supplier<UninterpretedSymbolicPythonObject> valueSupplier
    ) {
        return withTracing(
                context,
                new MethodQueryParameters(methodId, operands),
                () -> {
                    UninterpretedSymbolicPythonObject result = valueSupplier.get();
                    return wrap(result);
                }
        );
    }

    private static Supplier<Unit> unit(Runnable function) {
        return () -> {
            function.run();
            return Unit.INSTANCE;
        };
    }

    public static SymbolForCPython handlerLoadConstLong(ConcolicRunContext context, long value) {
        return withTracing(context, new LoadConstParameters(value), () -> wrap(handlerLoadConstLongKt(context, value)));
    }

    public static void handlerFork(ConcolicRunContext context, SymbolForCPython cond) {
        withTracing(context, new Fork(cond), unit(() -> handlerForkKt(context, cond.obj)));
    }

    public static SymbolForCPython handlerGTLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerGTLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerLTLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerLTLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerEQLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerEQLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerNELong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerNELongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerGELong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerGELongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerLELong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerLELongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerADDLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerADDLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerSUBLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerSUBLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerMULLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerMULLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerDIVLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerDIVLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerREMLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerREMLongKt(context, left.obj, right.obj));
    }

    public static SymbolForCPython handlerPOWLong(ConcolicRunContext context, int methodId, SymbolForCPython left, SymbolForCPython right) {
        return methodWrapper(context, methodId, Arrays.asList(left, right), () -> handlerPOWLongKt(context, left.obj, right.obj));
    }

    public static void handlerFunctionCall(ConcolicRunContext context, long function) {
        PythonPinnedCallable callable = new PythonPinnedCallable(new PythonObject(function));
        withTracing(context, new PythonFunctionCall(callable), unit(() -> handlerFunctionCallKt(context, callable)));
    }

    public static void handlerReturn(ConcolicRunContext context) {
        withTracing(context, PythonReturn.INSTANCE, unit(() -> handlerReturnKt(context)));
    }
}
