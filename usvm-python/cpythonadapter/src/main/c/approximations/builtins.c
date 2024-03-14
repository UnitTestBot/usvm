#include <stdio.h>

#include "approximations.h"
#include "wrapper.h"
#include "virtual_objects.h"

#include "CPythonFunctions.h"  // generated


PyObject *
Approximation_len(PyObject *o) {
    assert(is_wrapped(o));
    SymbolicAdapter *adapter = get_adapter(o);
    PyObject *concrete = unwrap(o);
    long concrete_result_long = PyObject_Size(o);
    if (concrete_result_long < 0) {
        assert(PyErr_Occurred());
        return 0;
    }
    PyObject *concrete_result = PyLong_FromLong(concrete_result_long);
    assert(concrete_result);
    PyObject *symbolic = Py_None;
    if (PyList_Check(concrete)) {
        symbolic = adapter->list_get_size(adapter->handler_param, get_symbolic_or_none(o));
    } else if (PyTuple_Check(concrete)) {
        symbolic = adapter->tuple_get_size(adapter->handler_param, get_symbolic_or_none(o));
    } else if (is_virtual_object(concrete)) {
        symbolic = adapter->symbolic_virtual_unary_fun(adapter->handler_param, get_symbolic_or_none(o));
    } else if (PyDict_Check(concrete)) {
        symbolic = dict_get_size(adapter->handler_param, get_symbolic_or_none(o));
    } else {
        sprintf(adapter->msg_buffer, "__len__ of %s", Py_TYPE(concrete)->tp_name);
        if (adapter->lost_symbolic_value(adapter->handler_param, adapter->msg_buffer)) return 0;
    }
    if (!symbolic) {
        assert(PyErr_Occurred());
        return 0;
    }
    return wrap(concrete_result, symbolic, adapter);
}


PyObject *
Approximation_isinstance(PyObject *obj, PyObject *type_wrapped) {
    assert(is_wrapped(obj));
    PyObject *type = unwrap(type_wrapped);
    SymbolicAdapter *adapter = get_adapter(obj);
    PyObject *concrete = unwrap(obj);
    int concrete_result_long = PyObject_IsInstance(concrete, type);
    if (concrete_result_long < 0) {
        assert(PyErr_Occurred());
        return 0;
    }
    PyObject *concrete_result = PyLong_FromLong(concrete_result_long);
    PyObject *symbolic = Py_None;
    if (PyType_Check(type)) {
        symbolic = adapter->symbolic_isinstance(adapter->handler_param, get_symbolic_or_none(obj), type);
        if (!symbolic) return 0;
    }

    return wrap(concrete_result, symbolic, adapter);
}


PyObject *
Approximation_range(void *adapter_raw, PyObject *args) {
    SymbolicAdapter *adapter = (SymbolicAdapter *) adapter_raw;
    assert(PyTuple_Check(args));
    Py_ssize_t size = PyTuple_Size(args);

    PyObject *start, *stop, *step;
    int bad_args = 0;
    if (size == 1) {
        start = adapter->load_const(adapter->handler_param, PyLong_FromLong(0));
        if (!start) return 0;
        stop = get_symbolic_or_none(PyTuple_GetItem(args, 0));
        step = adapter->load_const(adapter->handler_param, PyLong_FromLong(1));
        if (!step) return 0;

    } else if (size == 2) {
        start = get_symbolic_or_none(PyTuple_GetItem(args, 0));
        stop = get_symbolic_or_none(PyTuple_GetItem(args, 1));
        step = adapter->load_const(adapter->handler_param, PyLong_FromLong(1));
        if (!step) return 0;

    } else if (size == 3) {
        start = get_symbolic_or_none(PyTuple_GetItem(args, 0));
        stop = get_symbolic_or_none(PyTuple_GetItem(args, 1));
        step = get_symbolic_or_none(PyTuple_GetItem(args, 2));

    } else {
        bad_args = 1;
    }

    PyObject *symbolic = Py_None;

    if (!bad_args) {
        symbolic = adapter->create_range(adapter->handler_param, start, stop, step);
        if (!symbolic) return 0;
    }

    PyObject *unwrapped = PyTuple_New(size);
    for (int i = 0; i < size; i++) {
        PyTuple_SET_ITEM(unwrapped, i, unwrap(PyTuple_GetItem(args, i)));
        Py_XINCREF(PyTuple_GetItem(unwrapped, i));
    }
    PyObject *concrete = PyType_Type.tp_call((PyObject *) &PyRange_Type, unwrapped, 0);
    if (!concrete) return 0;

    return wrap(concrete, symbolic, adapter);
}

#define builtin_sum_impl \
    "def builtin_sum_impl(x): \n" \
    "    result = 0           \n" \
    "    for elem in x:       \n" \
    "        result += elem   \n" \
    "    return result        \n" \

PyObject *builtin_sum = 0;

#define contains_op_name "ContainsOpApproximation"
PyObject *contains_op = 0;

#define builtins_approximation_module "approximations.implementations"

void
initialize_builtin_python_impls() {
    PyObject *globals = PyDict_New();

    PyRun_StringFlags(builtin_sum_impl, Py_file_input, globals, globals, 0);
    builtin_sum = PyRun_StringFlags("builtin_sum_impl", Py_eval_input, globals, globals, 0);
    Py_INCREF(builtin_sum);

    PyRun_StringFlags("import " builtins_approximation_module, Py_file_input, globals, globals, 0);
    assert(!PyErr_Occurred());

    contains_op = PyRun_StringFlags(builtins_approximation_module "." contains_op_name ".run", Py_eval_input, globals, globals, 0);
    assert(!PyErr_Occurred() && contains_op);
    Py_INCREF(contains_op);

    Py_DECREF(globals);
}

int
Approximation_contains_op(PyObject *storage, PyObject *item, int *approximated) {
    assert(is_wrapped(storage) && is_wrapped(item));
    PyObject *concrete_storage = unwrap(storage);
    SymbolicAdapter *adapter = get_adapter(storage);
    if (PyList_Check(concrete_storage) || PyTuple_Check(concrete_storage)) {
        *approximated = 1;
        PyObject *wrapped = wrap(contains_op, Py_None, adapter);
        PyObject *args = PyTuple_Pack(2, storage, item);
        PyObject *result = Py_TYPE(wrapped)->tp_call(wrapped, args, 0);
        Py_DECREF(args);
        if (!result)
            return -1;
        PyObject *concrete_result = unwrap(result);
        Py_DECREF(result);
        return concrete_result == Py_True ? 1 : 0;
    } else if (PyDict_Check(concrete_storage)) {
        *approximated = 1;
        if (dict_contains(adapter->handler_param, get_symbolic_or_none(storage), get_symbolic_or_none(item)))
            return -1;
        return PySequence_Contains(concrete_storage, unwrap(item));
    } else if (PySet_Check(concrete_storage)) {
        *approximated = 1;
        if (set_contains(adapter->handler_param, get_symbolic_or_none(storage), get_symbolic_or_none(item)))
            return -1;
        return PySequence_Contains(concrete_storage, unwrap(item));
    }
    *approximated = 0;
    return 0;
}

PyObject *
Approximation_sum(PyObject *iterable) {
    assert(is_wrapped(iterable));
    SymbolicAdapter *adapter = get_adapter(iterable);
    PyObject *wrapped_func = wrap(builtin_sum, Py_None, adapter);
    assert(wrapped_func);
    PyObject *args = PyTuple_Pack(1, iterable);
    PyObject *res = Py_TYPE(wrapped_func)->tp_call(wrapped_func, args, 0);
    Py_DECREF(args);
    return res;
}