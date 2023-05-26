#include "symbolic_handler.h"

PyObject *handler(int signal_type, int signal_id, int nargs, PyObject *const *args, void *param) {
    // TODO
    printf("IN HANDLER. type: %d, id: %d\n", signal_type, signal_id);
    fflush(stdout);
    return Py_None;
}