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
} SymbolicMethod;

void clean_methods();
SymbolicMethod *construct_symbolic_method_with_self(JNIEnv *env, jobject symbolic_self, call_type call);
PyObject *call_symbolic_method(SymbolicMethod *method, ConcolicContext *ctx, PyObject *args, PyObject *kwargs);

#ifdef __cplusplus
}
#endif
#endif