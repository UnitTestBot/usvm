#include "symbolic_methods.h"
#include "utils.h"
#include "approximations.h"
#include "converters.h"

PyObject *methods_holder = 0;

void initialize_symbolic_methods_holder() {
    methods_holder = PyList_New(0);
}

/*
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
*/

SymbolicMethod *
construct_list_append_method(JNIEnv *env, SymbolicAdapter *adapter, jobject symbolic_self) {
    assert(methods_holder);
    SymbolicMethod *result = malloc(sizeof(SymbolicMethod));
    result->call = SymbolicMethod_list_append;
    result->self = object_wrapper_env(env, symbolic_self);
    result->adapter = adapter;
    PyList_Append(methods_holder, PyLong_FromLong((long) result));
    return result;
}

PyObject *
call_symbolic_method(SymbolicMethod *method, PyObject *args, PyObject *kwargs) {
    return method->call(method->adapter, method->self, args, kwargs);
}