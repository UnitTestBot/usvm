#include "utils.h"

/*
PyObject *
run_python_statement(const char *statement) {
    PyObject *m, *d, *v;
    m = PyImport_AddModule("__main__");
    if (m == NULL)
        return 0;
    d = PyModule_GetDict(m);
    v = PyRun_StringFlags(statements, Py_file_input, d, d, 0);
    if (v == NULL) {
        PyErr_Print();
        return 0;
    }
    v = PyRun_StringFlags(expression, Py_eval_input, d, d, 0);
    if (v == NULL) {
        PyErr_Print();
        return 0;
    }
    return v;
}
*/

static void
java_python_object_dealloc(PyObject *op) {
    JavaPythonObject *obj = (JavaPythonObject *) op;
    (*(obj->env->env))->DeleteGlobalRef(obj->env->env, obj->reference);
    Py_TYPE(op)->tp_free(op);
}

PyTypeObject JavaPythonObject_Type = {
    PyVarObject_HEAD_INIT(&PyType_Type, 0)
    JavaPythonObjectTypeName,                   /* tp_name */
    sizeof(JavaPythonObject),                   /* tp_basicsize */
    0,                                          /* tp_itemsize */
    java_python_object_dealloc,                 /* tp_dealloc */
    0,                                          /* tp_vectorcall_offset */
    0,                                          /* tp_getattr */
    0,                                          /* tp_setattr */
    0,                                          /* tp_as_async */
    0,                                          /* tp_repr */
    0,                                          /* tp_as_number */
    0,                                          /* tp_as_sequence */
    0,                                          /* tp_as_mapping */
    0,                                          /* tp_hash */
    0,                                          /* tp_call */
    0,                                          /* tp_str */
    0,                                          /* tp_getattro */
    0,                                          /* tp_setattro */
    0,                                          /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT,                         /* tp_flags */
    0,                                          /* tp_doc */
    0,                                          /* tp_traverse */
    0,                                          /* tp_clear */
    0,                                          /* tp_richcompare */
    0,                                          /* tp_weaklistoffset */
    0,                                          /* tp_iter */
    0,                                          /* tp_iternext */
    0,                                          /* tp_methods */
    0,                                          /* tp_members */
    0,                                          /* tp_getset */
    0,                                          /* tp_base */
    0,                                          /* tp_dict */
    0,                                          /* tp_descr_get */
    0,                                          /* tp_descr_set */
    0,                                          /* tp_dictoffset */
    0,                                          /* tp_init */
    0,                                          /* tp_alloc */
    0,                                          /* tp_new */
    PyObject_Free,                              /* tp_free */
};

PyObject *wrap_java_object(JavaEnvironment *env, jobject object) {
    JavaPythonObject *result = PyObject_New(JavaPythonObject, &JavaPythonObject_Type);
    result->env = env;
    result->object = object;
    result->reference = (*(env->env))->NewGlobalRef(env->env, object);
    return (PyObject*) result;
}