#include "approximations.h"
#include "wrapper.h"

/*
static int
get_len_event_id(lenfunc fun) {
    if (fun == PyList_Type.tp_as_sequence.sq_length || fun == PyList_Type.tp_as_mapping.mp_length)
        return
}

PyObject *
Approximation_len(PyObject *o) {
    assert(is_wrapped(o));
    SymbolicAdapter *adapter = get_adapter(o);

    PySequenceMethods *m = Py_TYPE(o)->tp_as_sequence;
    if (m && m->sq_length) {
        Py_ssize_t len = m->sq_length(o);
        PyObject *symbolic_len =
        return len;
    }

}
*/