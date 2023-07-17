#include "virtual_objects.h"

static void
virtual_object_dealloc(PyObject *op) {
    VirtualPythonObject *obj = (VirtualPythonObject *) op;
    (*(obj->ctx->env))->DeleteGlobalRef(obj->ctx->env, obj->reference);
    Py_TYPE(op)->tp_free(op);
}

static int
nb_bool(PyObject *self) {
    VirtualPythonObject *obj = (VirtualPythonObject *) self;
    jboolean result = (*obj->ctx->env)->CallStaticBooleanMethod(obj->ctx->env, obj->ctx->cpython_adapter_cls, obj->ctx->handle_virtual_nb_bool, obj->ctx->context, obj->object);
    CHECK_FOR_EXCEPTION(obj->ctx, -1)
    return (int) result;
}

static PyNumberMethods virtual_as_number = {
    0,                          /*nb_add*/
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
    0,                          /*nb_int*/
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
    0,                                       /*tp_as_sequence*/
    0,                                       /*tp_as_mapping*/
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
    0,                                       /*tp_richcompare */
    0,                                       /*tp_weaklistoffset */
    0,                                       /*tp_iter */
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
create_new_virtual_object(ConcolicContext *ctx, jobject object) {
    VirtualPythonObject *result = PyObject_New(VirtualPythonObject, &VirtualPythonObject_Type);
    result->ctx = ctx;
    result->object = object;
    result->reference = (*ctx->env)->NewGlobalRef(ctx->env, object);

    return (PyObject *) result;
}
