#include "virtual_objects.h"

static void
virtual_object_dealloc(PyObject *op) {
    //printf("DELETING: %p\n", op);
    //fflush(stdout);
    VirtualPythonObject *obj = (VirtualPythonObject *) op;
    (*(obj->ctx->env))->DeleteGlobalRef(obj->ctx->env, obj->reference);
    Py_TYPE(op)->tp_free(op);
}

#define MAKE_USVM_VIRUAL_CALL(obj, owner_id) \
    SymbolicAdapter *adapter = (obj)->adapter; \
    ConcolicContext *ctx = (obj)->ctx; \
    adapter->ignore = 1; \
    jobject virtual_object = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_virtual_call, ctx->context, owner_id); \
    adapter->ignore = 0; \
    CHECK_FOR_EXCEPTION(ctx, 0) \
    return (PyObject *) create_new_virtual_object(ctx, virtual_object, adapter);


#define MAKE_USVM_VIRUAL_CALL_NO_RETURN(obj, owner_id) \
    SymbolicAdapter *adapter = (obj)->adapter; \
    ConcolicContext *ctx = (obj)->ctx; \
    adapter->ignore = 1; \
    (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_virtual_call, ctx->context, owner_id); \
    adapter->ignore = 0; \
    CHECK_FOR_EXCEPTION(ctx, -1) \
    return 0;

static PyObject *
tp_richcompare(PyObject *o1, PyObject *o2, int op) {
    assert(is_virtual_object(o1));
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) o1, 0)
}

static PyObject *
tp_iter(PyObject *o1) {
    assert(is_virtual_object(o1));
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) o1, 0)
}

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

static PyObject *
nb_int(PyObject *self) {
    VirtualPythonObject *obj = (VirtualPythonObject *) self;
    obj->adapter->ignore = 1;
    jlong result = (*obj->ctx->env)->CallStaticLongMethod(obj->ctx->env, obj->ctx->cpython_adapter_cls, obj->ctx->handle_virtual_nb_int, obj->ctx->context, obj->reference);
    obj->adapter->ignore = 0;
    CHECK_FOR_EXCEPTION(obj->ctx, 0)
    return (PyObject *) result;
}

static PyObject *
nb_add(PyObject *first, PyObject *second) {
    PyObject *owner = 0;
    int owner_id = -1;
    if (is_virtual_object(first)) {
        owner = first;
        owner_id = 0;
    } else if (is_virtual_object(second)) {
        owner = second;
        owner_id = 1;
    } else {
        assert(0);  // Not reachable
    }
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) owner, owner_id)
}

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

static PyObject *
mp_subscript(PyObject *self, PyObject *item) {
    assert(is_virtual_object(self));
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) self, 0)
}

static int
mp_ass_subscript(PyObject *self, PyObject *item, PyObject *value) {
    assert(is_virtual_object(self));
    MAKE_USVM_VIRUAL_CALL_NO_RETURN((VirtualPythonObject *) self, 0)
}

static PyNumberMethods virtual_as_number = {
    nb_add,                     /*nb_add*/
    0,                          /*nb_subtract*/
    0,                          /*nb_multiply*/
    0,                          /*nb_remainder*/
    0,                          /*nb_divmod*/
    0,                          /*nb_power*/
    0,                          /*nb_negative*/
    0,                          /*nb_positive*/
    0,                          /*nb_absolute*/
    nb_bool,                    /*nb_bool*/
    0,                          /*nb_invert*/
    0,                          /*nb_lshift*/
    0,                          /*nb_rshift*/
    0,                          /*nb_and*/
    0,                          /*nb_xor*/
    0,                          /*nb_or*/
    nb_int,                     /*nb_int*/
    0,                          /*nb_reserved*/
    0,                          /*nb_float*/
    0,                          /* nb_inplace_add */
    0,                          /* nb_inplace_subtract */
    0,                          /* nb_inplace_multiply */
    0,                          /* nb_inplace_remainder */
    0,                          /* nb_inplace_power */
    0,                          /* nb_inplace_lshift */
    0,                          /* nb_inplace_rshift */
    0,                          /* nb_inplace_and */
    0,                          /* nb_inplace_xor */
    0,                          /* nb_inplace_or */
    0,                          /* nb_floor_divide */
    0,                          /* nb_true_divide */
    0,                          /* nb_inplace_floor_divide */
    0,                          /* nb_inplace_true_divide */
    0,                          /* nb_index */
};

static PySequenceMethods virtual_as_sequence = {
    sq_length,                  /* sq_length */
    0,                          /* sq_concat */
    0,                          /* sq_repeat */
    0,                          /* sq_item */
    0,                          /* sq_slice */
    0,                          /* sq_ass_item */
    0,                          /* sq_ass_slice */
    0,                          /* sq_contains */
    0,                          /* sq_inplace_concat */
    0,                          /* sq_inplace_repeat */
};

PyMappingMethods virtual_as_mapping = {
    0,                          /* mp_length */
    mp_subscript,               /* mp_subscript */
    mp_ass_subscript            /* mp_ass_subscript */
};

PyTypeObject VirtualPythonObject_Type = {
    PyVarObject_HEAD_INIT(&PyType_Type, 0)
    VirtualObjectTypeName,
    sizeof(VirtualPythonObject),
    0,
    virtual_object_dealloc,                  /*tp_dealloc*/
    0,                                       /*tp_vectorcall_offset*/
    0,                                       /*tp_getattr*/
    0,                                       /*tp_setattr*/
    0,                                       /*tp_as_async*/
    0,                                       /*tp_repr*/
    &virtual_as_number,                      /*tp_as_number*/
    &virtual_as_sequence,                    /*tp_as_sequence*/
    &virtual_as_mapping,                     /*tp_as_mapping*/
    0,                                       /*tp_hash */
    0,                                       /*tp_call */
    0,                                       /*tp_str */
    0,                                       /*tp_getattro */
    0,                                       /*tp_setattro */
    0,                                       /*tp_as_buffer */
    Py_TPFLAGS_DEFAULT,                      /*tp_flags */
    0,                                       /*tp_doc */
    0,                                       /*tp_traverse */
    0,                                       /*tp_clear */
    tp_richcompare,                          /*tp_richcompare */
    0,                                       /*tp_weaklistoffset */
    tp_iter,                                 /*tp_iter */
    0,                                       /*tp_iternext */
    0,                                       /*tp_methods */
    0,                                       /*tp_members */
    0,                                       /*tp_getset */
    0,                                       /*tp_base */
    0,                                       /*tp_dict */
    0,                                       /*tp_descr_get */
    0,                                       /*tp_descr_set */
    0,                                       /*tp_dictoffset */
    0,                                       /*tp_init */
    0,                                       /*tp_alloc */
    0,                                       /*tp_new */
};

PyObject *
allocate_raw_virtual_object(JNIEnv *env, jobject object) {
    VirtualPythonObject *result = PyObject_New(VirtualPythonObject, &VirtualPythonObject_Type);

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
    return Py_TYPE(obj) == &VirtualPythonObject_Type;
}

void register_virtual_methods(SymbolicAdapter *adapter) {
    adapter->virtual_tp_richcompare = tp_richcompare;
    adapter->virtual_tp_iter = tp_iter;
    adapter->virtual_nb_add = nb_add;
    adapter->virtual_mp_subscript = mp_subscript;
}