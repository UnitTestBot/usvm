#ifndef _Included_CPythonAdapter_tp_call_approximation
#define _Included_CPythonAdapter_tp_call_approximation
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "Python.h"
#include "symbolicadapter.h"

#include "utils.h"

typedef PyObject *(*call_type)(void *ctx, jobject self_reference, PyObject *args);  //, PyObject *kwargs);

typedef struct {
    call_type call;
    jobject self_reference;
    PyObject *approximation_check_ref;
    PyObject *approximation_run_ref;
} SymbolicMethod;

void clean_methods();
SymbolicMethod *construct_symbolic_method_with_self(JNIEnv *env, jobject symbolic_self, call_type call);
SymbolicMethod *construct_approximation(JNIEnv *env, jobject symbolic_self, call_type call, PyObject *approximation_ref);
SymbolicMethod *construct_python_method_with_self(JNIEnv *env, jobject symbolic_self);
PyObject *call_symbolic_method(SymbolicMethod *method, ConcolicContext *ctx, PyObject *args, PyObject *kwargs);
PyObject *approximate_symbolic_method(SymbolicMethod *method, ConcolicContext *ctx, int *approximated, PyObject *wrapped_self, PyObject *args, PyObject *kwargs);

#ifdef __cplusplus
}
#endif
#endif