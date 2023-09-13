#ifndef _Included_CPythonAdapter_tp_call_approximation
#define _Included_CPythonAdapter_tp_call_approximation
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "Python.h"
#include "symbolicadapter.h"

typedef PyObject *(call_type)(SymbolicAdapter *adapter, jobject self_reference, PyObject *args, PyObject *kwargs);

typedef struct {
    call_type *call;
    jobject self_reference;
} SymbolicMethod;

void initialize_symbolic_methods_holder();
// void clean_methods();
SymbolicMethod *construct_list_append_method(JNIEnv *env, jobject symbolic_self);
PyObject *call_symbolic_method(SymbolicMethod *method, SymbolicAdapter *adapter, PyObject *args, PyObject *kwargs);

#ifdef __cplusplus
}
#endif
#endif