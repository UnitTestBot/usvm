#include "approximations.h"
#include "wrapper.h"

#define list_richcmp_impl(OP)                      \
    "def list_richcompare_impl(x, y):             \n" \
    "    #print(\"INSIDE RICHCMP\", flush=True)   \n" \
    "    i = 0                                    \n" \
    "    while i < len(x) and i < len(y):         \n" \
    "        xitem = x[i]                         \n" \
    "        yitem = y[i]                         \n" \
    "        #if xitem is yitem:                  \n" \
    "        #    i += 1                          \n" \
    "        #    continue                        \n" \
    "        if xitem != yitem:                   \n" \
    "            break                            \n" \
    "        i += 1                               \n" \
    "    if i >= len(x) or i >= len(y):           \n" \
    "        return len(x) " OP " len(y)          \n" \
    "    return x[i] " OP " y[i]                  \n"


PyObject *list_richcompare_lt = 0;
PyObject *list_richcompare_gt = 0;
PyObject *list_richcompare_eq = 0;
PyObject *list_richcompare_ne = 0;
PyObject *list_richcompare_le = 0;
PyObject *list_richcompare_ge = 0;

void
initialize_list_python_impls() {
    PyObject *globals = PyDict_New();
    if (!list_richcompare_lt) {
        PyRun_StringFlags(list_richcmp_impl("<"), Py_file_input, globals, globals, 0);
        list_richcompare_lt = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
        Py_INCREF(list_richcompare_lt);
    }
    if (!list_richcompare_gt) {
        PyRun_StringFlags(list_richcmp_impl(">"), Py_file_input, globals, globals, 0);
        list_richcompare_gt = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
        Py_INCREF(list_richcompare_gt);
    }
    if (!list_richcompare_eq) {
        PyRun_StringFlags(list_richcmp_impl("=="), Py_file_input, globals, globals, 0);
        list_richcompare_eq = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
        Py_INCREF(list_richcompare_eq);
    }
    if (!list_richcompare_ne) {
        PyRun_StringFlags(list_richcmp_impl("!="), Py_file_input, globals, globals, 0);
        list_richcompare_ne = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
        Py_INCREF(list_richcompare_ne);
    }
    if (!list_richcompare_le) {
        PyRun_StringFlags(list_richcmp_impl("<="), Py_file_input, globals, globals, 0);
        list_richcompare_le = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
        Py_INCREF(list_richcompare_le);
    }
    if (!list_richcompare_ge) {
        PyRun_StringFlags(list_richcmp_impl(">="), Py_file_input, globals, globals, 0);
        list_richcompare_ge = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
        Py_INCREF(list_richcompare_ge);
    }
    Py_DECREF(globals);
}

PyObject *
Approximation_list_richcompare(PyObject *v, PyObject *w, int op) {
    assert(is_wrapped(v) && is_wrapped(w));
    PyObject *concrete_v = unwrap(v);
    PyObject *concrete_w = unwrap(w);
    if (!PyList_Check(concrete_v) || !PyList_Check(concrete_w))
        Py_RETURN_NOTIMPLEMENTED;

    SymbolicAdapter *adapter = get_adapter(v);
    if (adapter->fixate_type(adapter->handler_param, get_symbolic_or_none(v)))
        return 0;
    if (adapter->fixate_type(adapter->handler_param, get_symbolic_or_none(w)))
        return 0;
    PyObject *wrapped = 0;
    if (op == Py_LT) {
        wrapped = wrap(list_richcompare_lt, Py_None, adapter);
    } else if (op == Py_GT) {
        wrapped = wrap(list_richcompare_gt, Py_None, adapter);
    } else if (op == Py_EQ) {
        wrapped = wrap(list_richcompare_eq, Py_None, adapter);
    } else if (op == Py_NE) {
        wrapped = wrap(list_richcompare_ne, Py_None, adapter);
    } else if (op == Py_LE) {
        wrapped = wrap(list_richcompare_le, Py_None, adapter);
    } else if (op == Py_GE) {
        wrapped = wrap(list_richcompare_ge, Py_None, adapter);
    }
    assert(wrapped);
    return Py_TYPE(wrapped)->tp_call(wrapped, PyTuple_Pack(2, v, w), 0);
}