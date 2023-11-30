#include <string.h>

#include "symbolic_methods.h"
#include "utils.h"
#include "approximations.h"
#include "converters.h"

RefHolderNode methods_holder_root = {
    NULL,
    NULL
};

RefHolderNode *methods_holder = &methods_holder_root;

static void
release_method(void *address, void *data) {
    free((SymbolicMethod *) address);
}

void clean_methods() {
    clean_list(&methods_holder, NULL, release_method);
}

SymbolicMethod *
construct_symbolic_method_with_self(JNIEnv *env, jobject symbolic_self, call_type call) {
    assert(methods_holder);
    SymbolicMethod *result = malloc(sizeof(SymbolicMethod));
    result->call = call;
    result->self_reference = create_global_ref(env, symbolic_self);
    result->approximation_check_ref = 0;
    result->approximation_run_ref = 0;
    add_ref_to_list(&methods_holder, result);
    return result;
}

SymbolicMethod *
construct_python_method_with_self(JNIEnv *env, jobject symbolic_self) {
    assert(methods_holder);
    SymbolicMethod *result = malloc(sizeof(SymbolicMethod));
    result->self_reference = create_global_ref(env, symbolic_self);
    result->call = 0;
    result->approximation_check_ref = 0;
    result->approximation_run_ref = 0;
    add_ref_to_list(&methods_holder, result);
    return result;
}

SymbolicMethod *
construct_approximation(JNIEnv *env, jobject symbolic_self, call_type call, PyObject *approximation_ref) {
    assert(PyType_Check(approximation_ref));
    PyObject *check_ref = PyObject_GetAttrString(approximation_ref, "accept");
    assert(check_ref && !PyErr_Occurred());
    PyObject *run_ref = PyObject_GetAttrString(approximation_ref, "run");
    assert(run_ref && !PyErr_Occurred());
    SymbolicMethod *result = malloc(sizeof(SymbolicMethod));
    result->call = call;
    result->self_reference = create_global_ref(env, symbolic_self);
    result->approximation_check_ref = check_ref;
    result->approximation_run_ref = run_ref;
    add_ref_to_list(&methods_holder, result);
    return result;
}

PyObject *
call_symbolic_method(SymbolicMethod *method, ConcolicContext *ctx, PyObject *args, PyObject *kwargs) {
    assert(method->call);
    if (kwargs)
        return Py_None;  // TODO
    return method->call(ctx, method->self_reference, args);
}

PyObject *
approximate_symbolic_method(SymbolicMethod *method, ConcolicContext *ctx, int *approximated, PyObject *wrapped_self, PyObject *args, PyObject *kwargs) {
    assert(method->approximation_check_ref && method->approximation_run_ref);
    assert(PyFunction_Check(method->approximation_check_ref) && PyFunction_Check(method->approximation_run_ref));
    assert((method->self_reference == 0) == (wrapped_self == 0));
    if (kwargs) {  // TODO
        *approximated = 0;
        return Py_None;
    }
    PyObject *full_args = 0;
    if (wrapped_self) {
        PyObject *self = PyTuple_Pack(1, wrapped_self);
        full_args = PySequence_Concat(self, args);
        Py_DECREF(self);
    } else {
        full_args = args;
    }
    PyObject *accepts = PyFunction_Type.tp_call(method->approximation_check_ref, full_args, 0);
    assert(accepts == Py_False || accepts == Py_True);
    if (accepts == Py_False) {
        *approximated = 0;
        return Py_None;
    }
    *approximated = 1;
    return call_function_with_symbolic_tracing(ctx->adapter, method->approximation_run_ref, full_args, 0);
}