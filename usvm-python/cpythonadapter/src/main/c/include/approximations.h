#include "Python.h"

/* initializations of Python functions */
void initialize_list_python_impls();

#define INITIALIZE_PYTHON_APPROXIMATIONS \
    initialize_list_python_impls();


PyObject *Approximation_len(PyObject *o);  // builtins.len
PyObject *Approximation_isinstance(PyObject *obj, PyObject *type);  // builtins.isinstance

PyObject *Approximation_list_richcompare(PyObject *, PyObject *, int op);  // PyList_Type.tp_richcompare