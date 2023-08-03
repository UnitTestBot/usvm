#include "Python.h"

PyObject *Approximation_len(PyObject *o);  // builtins.len
PyObject *Approximation_isinstance(PyObject *obj, PyObject *type);  // builtins.isinstance

PyObject *Approximation_list_richcompare(PyObject *, PyObject *, int op);  // PyList_Type.tp_richcompare