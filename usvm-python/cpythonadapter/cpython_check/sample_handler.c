#include "Python.h"
#include "symbolicadapter.h"


PyObject *handler(int event_type, int event_id, int nargs, PyObject *const *args, void *param) {
    return 0;
}

int main() {
    Py_Initialize();

    PyObject *dict = PyDict_New();
    PyObject *function = PyRun_StringFlags("lambda x: (x*2 if x > 0 else -x*2)", Py_eval_input, dict, dict, 0);
    PyObject *arg = PyLong_FromLong(0);
    PyObject *arg_packed = PyTuple_New(2);
    PyTuple_SetItem(arg_packed, 0, arg);
    PyTuple_SetItem(arg_packed, 1, Py_None);
    PyObject *args[] = {arg_packed};

    SymbolicAdapter *adapter = create_new_adapter(handler, 0);
    PyObject *result = SymbolicAdapter_run((PyObject *) adapter, function, 1, args);

    if (result == NULL) {
        PyErr_Print();
    }

    Py_FinalizeEx();
}