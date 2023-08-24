#include <limits.h>

#include "utils.h"
#include "virtual_objects.h"
#include "approximations.h"

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
    // result->reference = object;
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
    dist->symbol_cls = (*env)->FindClass(env, "org/usvm/language/SymbolForCPython");
    dist->virtual_cls = (*env)->FindClass(env, "org/usvm/language/VirtualPythonObject");
    dist->java_exception = PyErr_NewException("ibmviqhlye.JavaException", 0, 0);
    dist->cpython_thrown_exception_field = (*env)->GetFieldID(env, dist->cpython_adapter_cls, "thrownException", "J");
    dist->cpython_java_exception_field = (*env)->GetFieldID(env, dist->cpython_adapter_cls, "javaExceptionType", "J");
    DO_REGISTRATIONS(dist, env)
}

void register_approximations(SymbolicAdapter *adapter) {
    adapter->approximation_builtin_len = Approximation_len;
    adapter->approximation_builtin_isinstance = Approximation_isinstance;
    adapter->approximation_list_richcompare = Approximation_list_richcompare;
    adapter->approximation_range = Approximation_range;
    adapter->approximation_list_append = Approximation_list_append;
}

static void
finish_virtual_objects_initialization(
    SymbolicAdapter *adapter,
    ConcolicContext *ctx,
    jlongArray *virtual_args
) {
    JNIEnv *env = ctx->env;
    int n = (*env)->GetArrayLength(env, *virtual_args);
    jlong *addresses = (*env)->GetLongArrayElements(env, *virtual_args, 0);
    for (int i = 0; i < n; i++) {
        finish_virtual_object_initialization((VirtualPythonObject *) addresses[i], ctx, adapter);
    }
    (*env)->ReleaseLongArrayElements(env, *virtual_args, addresses, 0);
}

void
construct_args_for_symbolic_adapter(
    SymbolicAdapter *adapter,
    ConcolicContext *ctx,
    jlongArray *concrete_args,
    jlongArray *virtual_args,
    jobjectArray *symbolic_args,
    PyObjectArray *dist
) {
    finish_virtual_objects_initialization(adapter, ctx, virtual_args);
    JNIEnv *env = ctx->env;
    int n = (*env)->GetArrayLength(env, *concrete_args);
    assert(n == (*env)->GetArrayLength(env, *symbolic_args));
    jlong *addresses = (*env)->GetLongArrayElements(env, *concrete_args, 0);
    PyObject **args = malloc(sizeof(PyObject *) * n);
    for (int i = 0; i < n; i++) {
        PyObject *tuple = PyTuple_New(2);

        PyObject *concrete_arg = (PyObject *) addresses[i];
        PyTuple_SetItem(tuple, 0, concrete_arg);

        jobject java_symbolic_object = (*env)->GetObjectArrayElement(env, *symbolic_args, i);
        PyObject *symbolic = Py_None;
        if (java_symbolic_object)
            symbolic = wrap_java_object(env, java_symbolic_object);
        PyTuple_SetItem(tuple, 1, symbolic);

        args[i] = tuple;
    }
    (*env)->ReleaseLongArrayElements(env, *concrete_args, addresses, 0);

    dist->size = n;
    dist->ptr = args;
}

int
extract_int_value(PyObject *int_object) {
    assert(PyLong_Check(int_object));
    int overflow;
    long value_as_long = PyLong_AsLongAndOverflow(int_object, &overflow);
    assert(!overflow);
    assert(value_as_long < INT_MAX);
    return (int) value_as_long;
}

int
take_instruction_from_frame(PyFrameObject *frame) {
    return extract_int_value(PyObject_GetAttrString((PyObject *) frame, "f_lasti"));
}

char *white_list[] = {
    "builtins.id",
    "import",
    "object.__delattr__",
    "object.__getattr__",
    "object.__setattr__",
    "compile",
    "exec",
    "os.listdir",
    "marshal.loads",
    "marshal.load",
    "marshal.dumps",
    "sys._getframe",
    "code.__new__",
    NULL
};

static int
is_pycache_file(PyObject *filename) {
    assert(PyUnicode_Check(filename));
    PyObject *substr = PyUnicode_FromString("__pycache__");
    int r = PyUnicode_Find(filename, substr, 0, PyObject_Size(filename), 1);
    assert(r != -2);
    if (r >= 0)
        return 1;
    return 0;
}

int
audit_hook(const char *event, PyObject *args, void *data) {
    char const **illegal_event_holder = (char const **) data;

    // printf("EVENT: %s %s\n", event, *illegal_event_holder);
    // fflush(stdout);

    if ((*illegal_event_holder) == 0 || strcmp(*illegal_event_holder, "active") != 0) {
        return 0;
    }

    int i = -1;
    while (white_list[++i]) {
        if (strcmp(white_list[i], event) == 0)
            return 0;
    }

    if (strcmp(event, "open") == 0) {
        PyObject *mode = PyTuple_GetItem(args, 1);
        if (mode == Py_None)
            return 0;
        assert(PyUnicode_Check(mode));
        if (PyUnicode_CompareWithASCIIString(mode, "r"))
            return 0;
        if (PyUnicode_CompareWithASCIIString(mode, "rb"))
            return 0;
    }

    if (strcmp(event, "os.rename") == 0 || strcmp(event, "os.mkdir") == 0) {
        if (is_pycache_file(PyTuple_GetItem(args, 0)))
            return 0;
    }

    // printf("EVENT: %s\n", event);
    // PyObject_Print(args, stdout, 0);
    // fflush(stdout);

    *illegal_event_holder = event;
    PyErr_SetString(PyExc_RuntimeError, "Illegal operation");
    return -1;
}