#ifndef _Included_CPythonAdapter_approximations
#define _Included_CPythonAdapter_approximations
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "Python.h"
#include "symbolicadapter.h"

/* initializations of Python functions */
void initialize_list_python_impls();
void initialize_builtin_python_impls();

#define INITIALIZE_PYTHON_APPROXIMATIONS \
    initialize_list_python_impls(); \
    initialize_builtin_python_impls();


PyObject *Approximation_len(PyObject *o);  // builtins.len
PyObject *Approximation_isinstance(PyObject *obj, PyObject *type);  // builtins.isinstance
PyObject *Approximation_range(void *adapter_raw, PyObject *args);  // builtins.range
PyObject *Approximation_sum(PyObject *iterable);  // builtins.sum
PyObject *SymbolicMethod_int(SymbolicAdapter *adapter, jobject self, PyObject *args, PyObject *kwargs);  // int()
PyObject *SymbolicMethod_float(SymbolicAdapter *adapter, jobject self, PyObject *args, PyObject *kwargs);  // float()

PyObject *Approximation_list_richcompare(PyObject *, PyObject *, int op);  // PyList_Type.tp_richcompare
PyObject *Approximation_list_repeat(PyObject *self, PyObject *n);  // PyList_Type.tp_as_sequence.sq_repeat
PyObject *SymbolicMethod_list_append(SymbolicAdapter *adapter, jobject self, PyObject *args, PyObject *kwargs); // list.append
PyObject *SymbolicMethod_list_pop(SymbolicAdapter *adapter, jobject self, PyObject *args, PyObject *kwargs); // list.pop
PyObject *Approximation_list_slice_get_item(PyObject *self, PyObject *slice);  // list[slice]

#ifdef __cplusplus
}
#endif
#endif