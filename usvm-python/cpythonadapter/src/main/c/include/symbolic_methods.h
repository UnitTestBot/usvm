#ifndef _Included_CPythonAdapter_tp_call_approximation
#define _Included_CPythonAdapter_tp_call_approximation
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "Python.h"
#include "symbolicadapter.h"

typedef PyObject *(call_type)(SymbolicAdapter *adapter, PyObject *self, PyObject *args, PyObject *kwargs);

typedef struct {
    call_type *call;
    PyObject *self;
    SymbolicAdapter *adapter;
} SymbolicMethod;

void initialize_symbolic_methods_holder();
// void clean_methods();
SymbolicMethod *construct_list_append_method(JNIEnv *env, SymbolicAdapter *adapter, jobject symbolic_self);
PyObject *call_symbolic_method(SymbolicMethod *method, PyObject *args, PyObject *kwargs);

#ifdef __cplusplus
}
#endif
#endif