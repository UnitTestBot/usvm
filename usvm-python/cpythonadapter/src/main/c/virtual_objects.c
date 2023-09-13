#include "virtual_objects.h"

static void
virtual_object_dealloc(PyObject *op) {
    //printf("DELETING: %p\n", op);
    //fflush(stdout);
    VirtualPythonObject *obj = (VirtualPythonObject *) op;
    (*(obj->ctx->env))->DeleteGlobalRef(obj->ctx->env, obj->reference);
    Py_TYPE(op)->tp_free(op);
}

PyType_Slot Virtual_tp_dealloc = {Py_tp_dealloc, virtual_object_dealloc};

#define MAKE_USVM_VIRUAL_CALL(obj, owner_id) \
    SymbolicAdapter *adapter = (obj)->adapter; \
    ConcolicContext *ctx = (obj)->ctx; \
    adapter->ignore = 1; \
    jlong result_address = (*ctx->env)->CallStaticLongMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_virtual_call, ctx->context, owner_id); \
    adapter->ignore = 0; \
    CHECK_FOR_EXCEPTION(ctx, 0) \
    if (is_virtual_object((PyObject *) result_address)) { \
        finish_virtual_object_initialization((VirtualPythonObject *) result_address, ctx, adapter); \
    } \
    return (PyObject *) result_address;


#define MAKE_USVM_VIRUAL_CALL_NO_RETURN(obj, owner_id) \
    SymbolicAdapter *adapter = (obj)->adapter; \
    ConcolicContext *ctx = (obj)->ctx; \
    adapter->ignore = 1; \
    (*ctx->env)->CallStaticLongMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_virtual_call, ctx->context, owner_id); \
    adapter->ignore = 0; \
    CHECK_FOR_EXCEPTION(ctx, -1) \
    return 0;

static PyObject *
tp_richcompare(PyObject *o1, PyObject *o2, int op) {
    assert(is_virtual_object(o1));
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) o1, 0)
}
PyType_Slot Virtual_tp_richcompare = {Py_tp_richcompare, tp_richcompare};

static PyObject *
tp_iter(PyObject *o1) {
    assert(is_virtual_object(o1));
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) o1, 0)
}
PyType_Slot Virtual_tp_iter = {Py_tp_iter, tp_iter};

static int
nb_bool(PyObject *self) {
    VirtualPythonObject *obj = (VirtualPythonObject *) self;
    SymbolicAdapter *adapter = obj->adapter;
    ConcolicContext *ctx = obj->ctx;
    adapter->ignore = 1;
    jboolean result = (*ctx->env)->CallStaticBooleanMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_virtual_nb_bool, ctx->context, obj->reference);
    CHECK_FOR_EXCEPTION(obj->ctx, -1)
    adapter->ignore = 0;
    return (int) result;
}
PyType_Slot Virtual_nb_bool = {Py_nb_bool, nb_bool};

static PyObject *
nb_int(PyObject *self) {
    VirtualPythonObject *obj = (VirtualPythonObject *) self;
    obj->adapter->ignore = 1;
    jlong result = (*obj->ctx->env)->CallStaticLongMethod(obj->ctx->env, obj->ctx->cpython_adapter_cls, obj->ctx->handle_virtual_nb_int, obj->ctx->context, obj->reference);
    obj->adapter->ignore = 0;
    CHECK_FOR_EXCEPTION(obj->ctx, 0)
    return (PyObject *) result;
}
PyType_Slot Virtual_nb_int = {Py_nb_int, nb_int};

#define BINARY_FUNCTION \
    PyObject *owner = 0; \
    int owner_id = -1; \
    if (is_virtual_object(first)) { \
        owner = first; \
        owner_id = 0; \
    } else if (is_virtual_object(second)) { \
        owner = second; \
        owner_id = 1; \
    } else { \
        assert(0);  /* Not reachable */ \
    } \
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) owner, owner_id)


static PyObject *
nb_add(PyObject *first, PyObject *second) {
    BINARY_FUNCTION
}
PyType_Slot Virtual_nb_add = {Py_nb_add, nb_add};

static PyObject *
nb_subtract(PyObject *first, PyObject *second) {
    BINARY_FUNCTION
}
PyType_Slot Virtual_nb_subtract = {Py_nb_subtract, nb_subtract};

static PyObject *
nb_multiply(PyObject *first, PyObject *second) {
    BINARY_FUNCTION
}
PyType_Slot Virtual_nb_multiply = {Py_nb_multiply, nb_multiply};

static PyObject *
nb_matrix_multiply(PyObject *first, PyObject *second) {
    BINARY_FUNCTION
}
PyType_Slot Virtual_nb_matrix_multiply = {Py_nb_matrix_multiply, nb_matrix_multiply};

static Py_ssize_t
sq_length(PyObject *self) {
    VirtualPythonObject *obj = (VirtualPythonObject *) self;
    SymbolicAdapter *adapter = obj->adapter;
    ConcolicContext *ctx = obj->ctx;
    adapter->ignore = 1;
    jint result = (*ctx->env)->CallStaticIntMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_virtual_sq_length, ctx->context, obj->reference);
    CHECK_FOR_EXCEPTION(obj->ctx, -1)
    adapter->ignore = 0;
    return result;
}
PyType_Slot Virtual_sq_length = {Py_sq_length, sq_length};

static PyObject *
mp_subscript(PyObject *self, PyObject *item) {
    assert(is_virtual_object(self));
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) self, 0)
}
PyType_Slot Virtual_mp_subscript = {Py_mp_subscript, mp_subscript};

static int
mp_ass_subscript(PyObject *self, PyObject *item, PyObject *value) {
    assert(is_virtual_object(self));
    MAKE_USVM_VIRUAL_CALL_NO_RETURN((VirtualPythonObject *) self, 0)
}
PyType_Slot Virtual_mp_ass_subscript = {Py_mp_ass_subscript, mp_ass_subscript};


PyTypeObject *VirtualPythonObject_Type = 0;

void
initialize_virtual_object_type() {
    PyType_Slot slots[] = {
        Virtual_tp_dealloc,
        Virtual_tp_richcompare,
        Virtual_tp_iter,
        Virtual_nb_bool,
        Virtual_nb_int,
        Virtual_nb_add,
        Virtual_nb_subtract,
        Virtual_nb_multiply,
        Virtual_nb_matrix_multiply,
        Virtual_sq_length,
        Virtual_mp_subscript,
        Virtual_mp_ass_subscript,
        {0, 0}
    };
    PyType_Spec spec = {
        VirtualObjectTypeName,
        sizeof(VirtualPythonObject),
        0,
        Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HEAPTYPE,
        slots
    };
    VirtualPythonObject_Type = (PyTypeObject*) PyType_FromSpec(&spec);
}

PyObject *
allocate_raw_virtual_object(JNIEnv *env, jobject object) {
    VirtualPythonObject *result = PyObject_New(VirtualPythonObject, VirtualPythonObject_Type);

    if (!result)
        return 0;

    result->reference = (*env)->NewGlobalRef(env, object);
    result->ctx = 0;
    result->adapter = 0;

    return (PyObject *) result;
}

void
finish_virtual_object_initialization(VirtualPythonObject *object, ConcolicContext *ctx, SymbolicAdapter *adapter) {
    object->ctx = ctx;
    object->adapter = adapter;
}

PyObject *
create_new_virtual_object(ConcolicContext *ctx, jobject object, SymbolicAdapter *adapter) {
    VirtualPythonObject *result = (VirtualPythonObject *) allocate_raw_virtual_object(ctx->env, object);
    finish_virtual_object_initialization(result, ctx, adapter);

    return (PyObject *) result;
}

int
is_virtual_object(PyObject *obj) {
    if (!obj)
        return 0;
    return Py_TYPE(obj) == VirtualPythonObject_Type;
}

void register_virtual_methods(SymbolicAdapter *adapter) {
    adapter->virtual_tp_richcompare = tp_richcompare;
    adapter->virtual_tp_iter = tp_iter;
    adapter->virtual_nb_add = nb_add;
    adapter->virtual_nb_subtract = nb_subtract;
    adapter->virtual_nb_multiply = nb_multiply;
    adapter->virtual_nb_matrix_multiply = nb_matrix_multiply;
    adapter->virtual_mp_subscript = mp_subscript;
}