#include "manual_handlers.h"
#include "symbolic_methods.h"

#include "CPythonFunctions.h"  // generated

#include "approximation_defs.h"

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

PyObject *
handler_approximate_type_call(void *ctx_raw, int *approximated, PyObject *wrapped_type, PyObject *args, PyObject *kwargs) {
    assert(is_wrapped(wrapped_type));
    PyObject *type_raw = unwrap(wrapped_type);
    assert(PyType_Check(type_raw));
    assert(args && PyTuple_Check(args));
    ConcolicContext *ctx = (ConcolicContext *) ctx_raw;
    SymbolicAdapter *adapter = ctx->adapter;
    PyTypeObject *type = (PyTypeObject *) type_raw;
    if (type->tp_init == EXPORT_OBJECT_INIT && PyTuple_Size(args) == 0 && !kwargs) {
        PyObject *symbolic_obj = create_empty_object(ctx_raw, type_raw);
        PyObject *concrete_obj = PyBaseObject_Type.tp_new(type, args, 0);
        *approximated = 1;
        return wrap(concrete_obj, symbolic_obj, adapter);
    } else if (type->tp_init == EXPORT_SLOT_INIT && type->tp_new == PyBaseObject_Type.tp_new) {
        PyObject *descr = _PyType_Lookup(type, PyUnicode_FromString("__init__"));
        if (descr && PyFunction_Check(descr)) {
            PyObject *tmp_args = PyTuple_Pack(0);
            PyObject *concrete_obj = PyBaseObject_Type.tp_new(type, tmp_args, 0);
            Py_DECREF(tmp_args);
            if (!concrete_obj)
                return 0;
            PyObject *symbolic_obj = create_empty_object(ctx_raw, type_raw);
            if (!symbolic_obj)
                return 0;
            PyObject *self = wrap(concrete_obj, symbolic_obj, adapter);
            assert(self);
            PyObject *tuple = PyTuple_Pack(1, self);
            PyObject *new_args = PySequence_Concat(tuple, args);
            Py_DECREF(tuple);
            *approximated = 1;
            PyObject *init_res = call_function_with_symbolic_tracing(adapter, descr, new_args, kwargs);
            if (!init_res)
                return 0;
            return self;
        }
    } else if (type == &PySet_Type && PyTuple_Size(args) == 0 && !kwargs) {
        *approximated = 1;
        PyObject *concrete_result = Py_TYPE(type)->tp_call(type_raw, args, kwargs);
        if (!concrete_result)
            return 0;
        PyObject *symbolic_result = create_empty_set(adapter->handler_param);
        if (!symbolic_result)
            return 0;
        return wrap(concrete_result, symbolic_result, adapter);
    }
    PyObject *symbolic_type = get_symbolic_or_none(wrapped_type);
    SymbolicMethod *symbolic_method = extract_symbolic_method(ctx, symbolic_type);
    if (symbolic_method && symbolic_method->approximation_check_ref && symbolic_method->approximation_run_ref) {
        return approximate_symbolic_method(symbolic_method, ctx, approximated, 0, args, kwargs);
    }
    *approximated = 0;
    return Py_None;
}
