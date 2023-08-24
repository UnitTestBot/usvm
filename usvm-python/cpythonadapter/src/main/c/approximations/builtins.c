#include <stdio.h>

#include "approximations.h"
#include "wrapper.h"
#include "virtual_objects.h"


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
        if (!symbolic) {
            assert(PyErr_Occurred());
            return 0;
        }
    } else if (is_virtual_object(concrete)) {
        symbolic = adapter->symbolic_virtual_unary_fun(adapter->handler_param, get_symbolic_or_none(o));
        if (!symbolic) {
            assert(PyErr_Occurred());
            return 0;
        }
    } else {
        sprintf(adapter->msg_buffer, "__len__ of %s", Py_TYPE(concrete)->tp_name);
        if (adapter->lost_symbolic_value(adapter->handler_param, adapter->msg_buffer)) return 0;
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