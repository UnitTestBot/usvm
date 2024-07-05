#include <limits.h>

#include "utils.h"
#include "virtual_objects.h"
#include "approximations.h"
#include "classnames.h"

static void
java_python_object_dealloc(PyObject *op) {
    // JavaPythonObject *obj = (JavaPythonObject *) op;
    // (*(obj->env))->DeleteGlobalRef(obj->env, obj->reference);
    Py_TYPE(op)->tp_free(op);
}
PyType_Slot java_python_object_dealloc_slot = {Py_tp_dealloc, java_python_object_dealloc};

PyTypeObject *JavaPythonObject_Type = 0;

void
initialize_java_python_type() {
    PyType_Slot slots[] = {
        java_python_object_dealloc_slot,
        {0, 0}
    };
    PyType_Spec spec = {
        JavaPythonObjectTypeName,
        sizeof(JavaPythonObject),
        0,
        Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HEAPTYPE,
        slots
    };
    JavaPythonObject_Type = (PyTypeObject*) PyType_FromSpec(&spec);
}

PyObject *
wrap_java_object(JNIEnv *env, jobject object) {
    JavaPythonObject *result = PyObject_New(JavaPythonObject, JavaPythonObject_Type);
    // result->env = env;
    result->reference = create_global_ref(env, object);
    return (PyObject*) result;
}

int is_wrapped_java_object(PyObject *object) {
    return Py_TYPE(object) == JavaPythonObject_Type;
}

void construct_concolic_context(JNIEnv *env, jobject context, jobject cpython_adapter, ConcolicContext *dist) {
    dist->adapter = 0;
    dist->env = env;
    dist->context = context;
    dist->cpython_adapter = cpython_adapter;
    dist->cpython_adapter_cls = (*env)->GetObjectClass(env, cpython_adapter);
    dist->symbol_cls = (*env)->FindClass(env, symbol_for_cpython_cls);
    dist->virtual_cls = (*env)->FindClass(env, virtual_object_cls);
    dist->java_exception = PyErr_NewException("ibmviqhlye.JavaException", 0, 0);
    dist->cpython_thrown_exception_field = (*env)->GetFieldID(env, dist->cpython_adapter_cls, "thrownException", "J");
    dist->cpython_java_exception_field = (*env)->GetFieldID(env, dist->cpython_adapter_cls, "javaExceptionType", "J");
    dist->symbol_tp_call_ref = (*env)->GetFieldID(env, dist->symbol_cls, "symbolicTpCall", "J");
    DO_REGISTRATIONS(dist, env)
}

void register_approximations(SymbolicAdapter *adapter) {
    adapter->approximation_builtin_len = Approximation_len;
    adapter->approximation_builtin_isinstance = Approximation_isinstance;
    adapter->approximation_builtin_sum = Approximation_sum;
    adapter->approximation_list_richcompare = Approximation_list_richcompare;
    adapter->approximation_range = Approximation_range;
    adapter->approximation_list_repeat = Approximation_list_repeat;
    adapter->approximation_list_slice_get_item = Approximation_list_slice_get_item;
    adapter->approximation_contains_op = Approximation_contains_op;
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

long
extract_long_value(PyObject *int_object) {
    assert(PyLong_Check(int_object));
    int overflow;
    long value_as_long = PyLong_AsLongAndOverflow(int_object, &overflow);
    assert(!overflow);
    return value_as_long;
}

int
extract_int_value(PyObject *int_object) {
    long value_as_long = extract_long_value(int_object);
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
    "os.putenv",
    "os.unsetenv",
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

PyObject *
construct_global_clones_dict(JNIEnv *env, jobjectArray global_clones) {
    assert(!PyErr_Occurred());
    jsize n = (*env)->GetArrayLength(env, global_clones);
    PyObject *result = PyDict_New();
    jclass cls = (*env)->FindClass(env, named_symbol_for_cpython_cls);
    jfieldID name_field = (*env)->GetFieldID(env, cls, "name", "Ljava/lang/String;");
    jfieldID symbol_field = (*env)->GetFieldID(env, cls, "symbol", symbol_for_cpython_cls_sig);
    for (int i = 0; i < n; i++) {
        jobject named_symbol = (*env)->GetObjectArrayElement(env, global_clones, i);
        jstring name = (jstring) (*env)->GetObjectField(env, named_symbol, name_field);
        jobject symbol = (*env)->GetObjectField(env, named_symbol, symbol_field);
        const char *c_name = (*env)->GetStringUTFChars(env, name, 0);
        PyObject *symbol_object = wrap_java_object(env, symbol);
        PyDict_SetItemString(result, c_name, symbol_object);
        (*env)->ReleaseStringUTFChars(env, name, c_name);
    }
    assert(!PyErr_Occurred());
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        assert(0);
    }
    return result;
}

void
add_ref_to_list(RefHolderNode **holder, void *ref) {
    RefHolderNode *new_node = malloc(sizeof(RefHolderNode));
    new_node->prev = *holder;
    new_node->value = ref;
    *holder = new_node;
}

void
clean_list(RefHolderNode **holder, void *data, void (*release)(void *ref, void *data)) {
    while ((*holder)->value) {
        RefHolderNode *next = (RefHolderNode *) (*holder)->prev;
        assert(next);
        void *address = (*holder)->value;
        release(address, data);
        free(*holder);
        (*holder) = next;
    }
}

RefHolderNode global_ref_root = {
    NULL,
    NULL
};

RefHolderNode *global_ref_holder = &global_ref_root;

jobject
create_global_ref(JNIEnv *env, jobject local_ref) {
    if (!local_ref)
        return 0;
    jobject result = (*env)->NewGlobalRef(env, local_ref);
    add_ref_to_list(&global_ref_holder, result);
    return result;
}

static void
release_global_ref(void *address, void *env_raw) {
    JNIEnv *env = (JNIEnv *) env_raw;
    (*env)->DeleteGlobalRef(env, (jobject) address);
}

void
release_global_refs(JNIEnv *env) {
    clean_list(&global_ref_holder, env, release_global_ref);
}