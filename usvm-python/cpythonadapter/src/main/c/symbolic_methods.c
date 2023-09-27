#include "symbolic_methods.h"
#include "utils.h"
#include "approximations.h"
#include "converters.h"

PyObject *methods_holder = 0;

void initialize_symbolic_methods_holder() {
    methods_holder = PyList_New(0);
}

void clean_methods() {
    Py_ssize_t size = PyList_Size(methods_holder);
    for (Py_ssize_t i = 0; i < size; i++) {
        PyObject *item = PyList_GetItem(methods_holder, i);
        long address = extract_long_value(item);
        free((SymbolicMethod *) address);
    }
    Py_DECREF(methods_holder);
    methods_holder = 0;
}

SymbolicMethod *
construct_list_append_method(JNIEnv *env, jobject symbolic_self) {
    assert(methods_holder);
    SymbolicMethod *result = malloc(sizeof(SymbolicMethod));
    result->call = SymbolicMethod_list_append;
    result->self_reference = create_global_ref(env, symbolic_self);
    add_ref_to_list(methods_holder, result);
    return result;
}

SymbolicMethod *
construct_symbolic_method_without_self(call_type call) {
    assert(methods_holder);
    SymbolicMethod *result = malloc(sizeof(SymbolicMethod));
    result->call = call;
    result->self_reference = 0;
    add_ref_to_list(methods_holder, result);
    return result;
}

PyObject *
call_symbolic_method(SymbolicMethod *method, SymbolicAdapter *adapter, PyObject *args, PyObject *kwargs) {
    return method->call(adapter, method->self_reference, args, kwargs);
}