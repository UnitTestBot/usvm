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

#define list_multiply_impl \
    "def list_multiply_impl(x, y): \n" \
    "    result = []               \n" \
    "    for _ in range(y):        \n" \
    "        result += x           \n" \
    "    return result             \n" \

PyObject *list_multiply = 0;

void
initialize_list_python_impls() {
    PyObject *globals = PyDict_New();

    PyRun_StringFlags(list_richcmp_impl("<"), Py_file_input, globals, globals, 0);
    list_richcompare_lt = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    Py_INCREF(list_richcompare_lt);

    PyRun_StringFlags(list_richcmp_impl(">"), Py_file_input, globals, globals, 0);
    list_richcompare_gt = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    Py_INCREF(list_richcompare_gt);

    PyRun_StringFlags(list_richcmp_impl("=="), Py_file_input, globals, globals, 0);
    list_richcompare_eq = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    Py_INCREF(list_richcompare_eq);

    PyRun_StringFlags(list_richcmp_impl("!="), Py_file_input, globals, globals, 0);
    list_richcompare_ne = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    Py_INCREF(list_richcompare_ne);

    PyRun_StringFlags(list_richcmp_impl("<="), Py_file_input, globals, globals, 0);
    list_richcompare_le = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    Py_INCREF(list_richcompare_le);

    PyRun_StringFlags(list_richcmp_impl(">="), Py_file_input, globals, globals, 0);
    list_richcompare_ge = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    Py_INCREF(list_richcompare_ge);

    PyRun_StringFlags(list_multiply_impl, Py_file_input, globals, globals, 0);
    list_multiply = PyRun_StringFlags("list_multiply_impl", Py_eval_input, globals, globals, 0);
    Py_INCREF(list_multiply);

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
    PyObject *args = PyTuple_Pack(2, v, w);
    PyObject *result = Py_TYPE(wrapped)->tp_call(wrapped, args, 0);
    Py_DECREF(args);
    return result;
}

PyObject *
Approximation_list_append(PyObject *append_method, PyObject *symbolic_list, PyObject *wrapped_elem) {
    assert(PyCFunction_Check(append_method) && symbolic_list && is_wrapped(wrapped_elem));
    SymbolicAdapter *adapter = get_adapter(wrapped_elem);
    PyObject *concrete_elem = unwrap(wrapped_elem);
    PyObject *symbolic_elem = get_symbolic_or_none(wrapped_elem);
    PyObject *concrete_args = PyTuple_Pack(1, concrete_elem);
    PyObject *concrete_result = Py_TYPE(append_method)->tp_call(append_method, concrete_args, 0);
    Py_DECREF(concrete_args);
    if (!concrete_result) {
        return 0;
    }
    PyObject *self = adapter->list_append(adapter->handler_param, symbolic_list, symbolic_elem);
    if (!self)
        return 0;

    return wrap(concrete_result, Py_None, adapter);
}

PyObject *
Approximation_list_repeat(PyObject *self, PyObject *n) {
    assert(is_wrapped(self) && is_wrapped(n));
    SymbolicAdapter *adapter = get_adapter(self);
    if (adapter->fixate_type(adapter->handler_param, get_symbolic_or_none(self)))
        return 0;
    if (adapter->fixate_type(adapter->handler_param, get_symbolic_or_none(n)))
        return 0;
    PyObject *wrapped = wrap(list_multiply, Py_None, adapter);
    PyObject *args = PyTuple_Pack(2, self, n);
    PyObject *result = Py_TYPE(wrapped)->tp_call(wrapped, args, 0);
    Py_DECREF(args);
    return result;
}