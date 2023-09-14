#include "approximations.h"
#include "converters.h"
#include "utils.h"

#include "wrapper.h"

#define list_richcmp_impl(OP)                      \
    "def list_richcompare_impl(x, y):             \n" \
    "    #print(\"INSIDE RICHCMP\", flush=True)   \n" \
    "    i = 0                                    \n" \
    "    while i < len(x) and i < len(y):         \n" \
    "        xitem = x[i]                         \n" \
    "        yitem = y[i]                         \n" \
    "        if xitem is yitem:                   \n" \
    "            i += 1                           \n" \
    "            continue                         \n" \
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
    "    return result             \n"

PyObject *list_multiply = 0;

#define slice_unpack_impl \
    "def slice_unpack_impl(s: slice): \n"\
    "    start, stop, step = None, None, None \n"\
    "    min_, max_ = -10**18, 10**18 \n"\
    "    if s.step is None: \n"\
    "        step = 1 \n"\
    "    else: \n"\
    "        step = s.step \n"\
    "        if step == 0: \n"\
    "            raise ValueError('slice step cannot be zero') \n"\
    "    if s.start is None: \n"\
    "        start = max_ if step < 0 else 0 \n"\
    "    else: \n"\
    "        start = s.start \n"\
    "    if s.stop is None: \n"\
    "        stop = min_ if step < 0 else max_\n"\
    "    else: \n"\
    "        stop = s.stop \n"\
    "    return start, stop, step \n"

PyObject *slice_unpack = 0;

#define slice_adjust_indices_impl \
    "def slice_adjust_indices_impl(length, start, stop, step): \n"\
    "    result_length = 0 \n"\
    "    if start < 0: \n"\
    "        start += length \n"\
    "        if start < 0: \n"\
    "            start = -1 if step < 0 else 0 \n"\
    "    elif start >= length: \n"\
    "        start = length - 1 if step < 0 else length \n"\
    "    if stop < 0: \n"\
    "        stop += length \n"\
    "        if stop < 0: \n"\
    "            stop = -1 if step < 0 else 0 \n"\
    "    elif stop >= length: \n"\
    "        stop = length - 1 if step < 0 else length \n"\
    "    if step < 0: \n"\
    "        if stop < start: \n"\
    "            result_length = (start - stop - 1) // (-step) + 1; \n"\
    "    else: \n"\
    "        if start < stop: \n"\
    "            result_length = (stop - start - 1) // step + 1 \n"\
    "    return result_length, start, stop, step \n"

PyObject *slice_adjust_indices = 0;

#define slice_get_item_impl \
    "def slice_get_item_impl(self: list, item: slice): \n"\
    "    start, stop, step = slice_unpack_impl(item) \n"\
    "    slicelength, start, stop, step = slice_adjust_indices_impl(len(self), start, stop, step) \n"\
    "    if slicelength <= 0: \n"\
    "        return [] \n"\
    "    else: \n"\
    "        result = [None] * slicelength \n"\
    "        cur = start \n"\
    "        for i in range(slicelength): \n"\
    "            result[i] = self[cur] \n"\
    "            cur += step \n"\
    "        return result \n"

PyObject *slice_get_item = 0;

void
initialize_list_python_impls() {
    PyObject *globals = PyDict_New();

    PyRun_StringFlags(list_richcmp_impl("<"), Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());
    list_richcompare_lt = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && list_richcompare_lt);
    Py_INCREF(list_richcompare_lt);

    PyRun_StringFlags(list_richcmp_impl(">"), Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());
    list_richcompare_gt = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && list_richcompare_gt);
    Py_INCREF(list_richcompare_gt);

    PyRun_StringFlags(list_richcmp_impl("=="), Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());
    list_richcompare_eq = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && list_richcompare_eq);
    Py_INCREF(list_richcompare_eq);

    PyRun_StringFlags(list_richcmp_impl("!="), Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());
    list_richcompare_ne = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && list_richcompare_ne);
    Py_INCREF(list_richcompare_ne);

    PyRun_StringFlags(list_richcmp_impl("<="), Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());
    list_richcompare_le = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && list_richcompare_le);
    Py_INCREF(list_richcompare_le);

    PyRun_StringFlags(list_richcmp_impl(">="), Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());
    list_richcompare_ge = PyRun_StringFlags("list_richcompare_impl", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && list_richcompare_ge);
    Py_INCREF(list_richcompare_ge);

    PyRun_StringFlags(list_multiply_impl, Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());
    list_multiply = PyRun_StringFlags("list_multiply_impl", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && list_multiply);
    Py_INCREF(list_multiply);

    PyRun_StringFlags(slice_unpack_impl, Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());
    slice_unpack = PyRun_StringFlags("slice_unpack_impl", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && slice_unpack);
    Py_INCREF(slice_unpack);

    PyRun_StringFlags(slice_adjust_indices_impl, Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());
    slice_adjust_indices = PyRun_StringFlags("slice_adjust_indices_impl", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && slice_adjust_indices);
    Py_INCREF(slice_adjust_indices);

    PyRun_StringFlags(slice_get_item_impl, Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());
    slice_get_item = PyRun_StringFlags("slice_get_item_impl", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && slice_get_item);
    Py_INCREF(slice_get_item);

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

PyObject *SymbolicMethod_list_append(SymbolicAdapter *adapter, jobject self_reference_list, PyObject *args, PyObject *kwargs) {
    if (args == 0 || !PyTuple_Check(args) || PyTuple_GET_SIZE(args) != 1 || kwargs)
        return Py_None;
    PyObject *symbolic_elem = PyTuple_GetItem(args, 0);
    PyObject *symbolic_list = object_wrapper((ConcolicContext *) adapter->handler_param, self_reference_list);
    PyObject *self = adapter->list_append(adapter->handler_param, symbolic_list, symbolic_elem);
    Py_DECREF(symbolic_list);
    if (!self)
        return 0;
    PyObject *result = adapter->load_const(adapter->handler_param, Py_None);
    if (!result)
        return 0;
    return result;
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

PyObject *
Approximation_list_slice_get_item(PyObject *self, PyObject *slice) {
    assert(is_wrapped(self) && is_wrapped(slice));
    SymbolicAdapter *adapter = get_adapter(self);
    if (adapter->fixate_type(adapter->handler_param, get_symbolic_or_none(self)))
        return 0;
    if (adapter->fixate_type(adapter->handler_param, get_symbolic_or_none(slice)))
        return 0;
    PyObject *wrapped = wrap(slice_get_item, Py_None, adapter);
    PyObject *args = PyTuple_Pack(2, self, slice);
    PyObject *result = Py_TYPE(wrapped)->tp_call(wrapped, args, 0);
    Py_DECREF(args);
    return result;
}