#ifndef _Included_CPythonAdapter_manual_handlers
#define _Included_CPythonAdapter_manual_handlers
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "Python.h"

PyObject *handler_symbolic_tp_call(void *ctx_raw, PyObject *self, PyObject *args, PyObject *kwargs);
int handler_is_pycfunction_with_approximation(void *ctx_raw, PyObject *self);
PyObject *handler_approximate_pycfunction_call(void *ctx_raw, int *approximated, PyObject *callable, PyObject *self, PyObject *args, PyObject *kwargs);
PyObject *handler_extract_self_from_method(void *ctx_raw, PyObject *callable);
PyObject *handler_approximate_type_call(void *, int *approximated, PyObject *type, PyObject *args, PyObject *kwargs);

#ifdef __cplusplus
}
#endif
#endif