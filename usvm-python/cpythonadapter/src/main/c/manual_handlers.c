#include "manual_handlers.h"
#include "symbolic_methods.h"

static SymbolicMethod *
extract_symbolic_method(ConcolicContext *ctx, PyObject *py_symbol) {
    if (!is_wrapped_java_object(py_symbol))
        return 0;
    jobject symbol = ((JavaPythonObject *) py_symbol)->reference;
    jlong ref = (*ctx->env)->GetLongField(ctx->env, symbol, ctx->symbol_tp_call_ref);
    return (SymbolicMethod *) ref;
}

PyObject *
handler_symbolic_tp_call(void *ctx_raw, PyObject *self, PyObject *args, PyObject *kwargs) {
    ConcolicContext *ctx = (ConcolicContext *) ctx_raw;
    SymbolicMethod *method = extract_symbolic_method(ctx, self);
    if (!method || !method->call)
        return Py_None;
    return call_symbolic_method(method, ctx, args, kwargs);
}

int
handler_is_pycfunction_with_approximation(void *ctx_raw, PyObject *self) {
    ConcolicContext *ctx = (ConcolicContext *) ctx_raw;
    SymbolicMethod *method = extract_symbolic_method(ctx, self);
    return method && method->approximation_check_ref && method->approximation_run_ref;
}

PyObject *
handler_approximate_pycfunction_call(void *ctx_raw, int *approximated, PyObject *callable, PyObject *self, PyObject *args, PyObject *kwargs) {
    ConcolicContext *ctx = (ConcolicContext *) ctx_raw;
    SymbolicMethod *method = extract_symbolic_method(ctx, callable);
    assert(method);
    return approximate_symbolic_method(method, ctx, approximated, self, args, kwargs);
}

PyObject *
handler_extract_self_from_method(void *ctx_raw, PyObject *callable) {
    ConcolicContext *ctx = (ConcolicContext *) ctx_raw;
    SymbolicMethod *method = extract_symbolic_method(ctx, callable);
    if (!method || !method->self_reference)
        return Py_None;
    return wrap_java_object(ctx->env, method->self_reference);
}