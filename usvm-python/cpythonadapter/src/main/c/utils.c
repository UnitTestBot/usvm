#include "utils.h"

static void
java_python_object_dealloc(PyObject *op) {
    JavaPythonObject *obj = (JavaPythonObject *) op;
    (*(obj->env))->DeleteGlobalRef(obj->env, obj->reference);
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

PyObject *wrap_java_object(JNIEnv *env, jobject object) {
    JavaPythonObject *result = PyObject_New(JavaPythonObject, &JavaPythonObject_Type);
    result->env = env;
    result->object = object;
    result->reference = (*env)->NewGlobalRef(env, object);
    return (PyObject*) result;
}

int is_wrapped_java_object(PyObject *object) {
    return Py_TYPE(object) == &JavaPythonObject_Type;
}

void construct_concolic_context(JNIEnv *env, jobject context, jobject cpython_adapter, ConcolicContext *dist) {
    dist->env = env;
    dist->context = context;
    dist->cpython_adapter = cpython_adapter;
    dist->cpython_adapter_cls = (*env)->GetObjectClass(env, cpython_adapter);
    DO_REGISTRATIONS(dist, env)
}

void construct_args_for_symbolic_adapter(
    JNIEnv *env,
    jlongArray *concrete_args,
    jobjectArray symbolic_args,
    PyObjectArray *dist
) {
    int n = (*env)->GetArrayLength(env, *concrete_args);
    assert(n == (*env)->GetArrayLength(env, symbolic_args));
    jlong *addresses = (*env)->GetLongArrayElements(env, *concrete_args, 0);
    PyObject **args = malloc(sizeof(PyObject *) * n);
    for (int i = 0; i < n; i++) {
        PyObject *tuple = PyTuple_New(2);
        PyTuple_SetItem(tuple, 0, (PyObject *) addresses[i]);
        PyObject *symbolic = wrap_java_object(env, (*env)->GetObjectArrayElement(env, symbolic_args, i));
        PyTuple_SetItem(tuple, 1, symbolic);
        args[i] = tuple;
    }
    (*env)->ReleaseLongArrayElements(env, *concrete_args, addresses, 0);

    dist->size = n;
    dist->ptr = args;
}

int take_instruction_from_frame(PyObject *frame) {
    PyObject *res = PyObject_GetAttrString(frame, "f_lasti");
    int overflow;
    long value_as_long = PyLong_AsLongAndOverflow(res, &overflow);
    assert(!overflow);
    return (int) value_as_long;
}