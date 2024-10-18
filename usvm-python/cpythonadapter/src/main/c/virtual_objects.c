#include "virtual_objects.h"

#define DEBUG_OUTPUT(name) \
    /*printf("Virtual " name "\n"); \
    fflush(stdout);*/

static void
virtual_object_dealloc(PyObject *op) {
    //printf("DELETING: %p\n", op);
    //fflush(stdout);
    //VirtualPythonObject *obj = (VirtualPythonObject *) op;
    //(*(obj->ctx->env))->DeleteGlobalRef(obj->ctx->env, obj->reference);
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

#define CHECK_IF_ACTIVATED(self, fail_value) \
    if (!((VirtualPythonObject *)self)->ctx) { \
        PyErr_Format(PyExc_RuntimeError, "ConcolicRunContext is not yet activated"); \
        return fail_value; \
    }

static PyObject *
tp_richcompare(PyObject *o1, PyObject *o2, int op) {
    DEBUG_OUTPUT("tp_richcompare")
    assert(is_virtual_object(o1));
    CHECK_IF_ACTIVATED(o1, 0)
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) o1, 0)
}
PyType_Slot Virtual_tp_richcompare = {Py_tp_richcompare, tp_richcompare};

static int
is_special_attribute(PyObject *name) {
    if (!PyUnicode_Check(name))
        return 0;
    return PyUnicode_CompareWithASCIIString(name, "__instancecheck__") == 0 ||
           PyUnicode_CompareWithASCIIString(name, "__class__") == 0;
}

static PyObject *
tp_getattro(PyObject *self, PyObject *name) {
    DEBUG_OUTPUT("tp_getattro")
    assert(is_virtual_object(self));
    CHECK_IF_ACTIVATED(self, 0)
    /*printf("tp_getattro on ");
    PyObject_Print(name, stdout, 0);
    printf("\n");
    fflush(stdout);*/
    if (is_special_attribute(name)) {
        PyErr_Format(PyExc_AttributeError, "special attribute on virtual object");
        return 0;
    }
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) self, 0)
}
PyType_Slot Virtual_tp_getattro = {Py_tp_getattro, tp_getattro};

static PyObject *
tp_iter(PyObject *o1) {
    DEBUG_OUTPUT("tp_iter")
    assert(is_virtual_object(o1));
    CHECK_IF_ACTIVATED(o1, 0)
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) o1, 0)
}
PyType_Slot Virtual_tp_iter = {Py_tp_iter, tp_iter};

static Py_hash_t
tp_hash(PyObject *o1) {
    DEBUG_OUTPUT("tp_hash")
    assert(is_virtual_object(o1));
    return PyBaseObject_Type.tp_hash(o1);
}
PyType_Slot Virtual_tp_hash = {Py_tp_hash, tp_hash};

static PyObject *
tp_call(PyObject *o1, PyObject *args, PyObject *kwargs) {
    DEBUG_OUTPUT("tp_call")
    assert(is_virtual_object(o1));
    CHECK_IF_ACTIVATED(o1, 0)
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) o1, 0)
}
PyType_Slot Virtual_tp_call = {Py_tp_call, tp_call};

static int
nb_bool(PyObject *self) {
    DEBUG_OUTPUT("nb_bool")
    assert(is_virtual_object(self));
    CHECK_IF_ACTIVATED(self, -1)
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
nb_negative(PyObject *self) {
    DEBUG_OUTPUT("nb_negative")
    assert(is_virtual_object(self));
    CHECK_IF_ACTIVATED(self, 0)
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) self, 0)
}
PyType_Slot Virtual_nb_negative = {Py_nb_negative, nb_negative};

static PyObject *
nb_positive(PyObject *self) {
    DEBUG_OUTPUT("nb_positive")
    assert(is_virtual_object(self));
    CHECK_IF_ACTIVATED(self, 0)
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) self, 0)
}
PyType_Slot Virtual_nb_positive = {Py_nb_positive, nb_positive};

#define BINARY_FUNCTION \
    if (is_virtual_object(first)) { \
        CHECK_IF_ACTIVATED(first, 0) \
    } \
    if (is_virtual_object(second)) { \
        CHECK_IF_ACTIVATED(second, 0) \
    } \
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
    DEBUG_OUTPUT("nb_add")
    BINARY_FUNCTION
}
PyType_Slot Virtual_nb_add = {Py_nb_add, nb_add};

static PyObject *
nb_subtract(PyObject *first, PyObject *second) {
    DEBUG_OUTPUT("nb_subtract")
    BINARY_FUNCTION
}
PyType_Slot Virtual_nb_subtract = {Py_nb_subtract, nb_subtract};

static PyObject *
nb_multiply(PyObject *first, PyObject *second) {
    DEBUG_OUTPUT("nb_multiply")
    BINARY_FUNCTION
}
PyType_Slot Virtual_nb_multiply = {Py_nb_multiply, nb_multiply};

static PyObject *
nb_matrix_multiply(PyObject *first, PyObject *second) {
    DEBUG_OUTPUT("nb_matrix_multiply")
    BINARY_FUNCTION
}
PyType_Slot Virtual_nb_matrix_multiply = {Py_nb_matrix_multiply, nb_matrix_multiply};

static PyObject *
sq_concat(PyObject *first, PyObject *second) {
    DEBUG_OUTPUT("sq_concat")
    BINARY_FUNCTION
}
PyType_Slot Virtual_sq_concat = {Py_sq_concat, sq_concat};

static Py_ssize_t
sq_length(PyObject *self) {
    DEBUG_OUTPUT("sq_length")
    assert(is_virtual_object(self));
    CHECK_IF_ACTIVATED(self, -1)
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
    DEBUG_OUTPUT("mp_subscript")
    assert(is_virtual_object(self));
    CHECK_IF_ACTIVATED(self, 0)
    MAKE_USVM_VIRUAL_CALL((VirtualPythonObject *) self, 0)
}
PyType_Slot Virtual_mp_subscript = {Py_mp_subscript, mp_subscript};

static int
mp_ass_subscript(PyObject *self, PyObject *item, PyObject *value) {
    DEBUG_OUTPUT("mp_ass_subscript")
    assert(is_virtual_object(self));
    CHECK_IF_ACTIVATED(self, -1)
    MAKE_USVM_VIRUAL_CALL_NO_RETURN((VirtualPythonObject *) self, 0)
}
PyType_Slot Virtual_mp_ass_subscript = {Py_mp_ass_subscript, mp_ass_subscript};

static int
tp_setattro(PyObject *self, PyObject *attr, PyObject *value) {
    DEBUG_OUTPUT("tp_setattro")
    assert(is_virtual_object(self));
    CHECK_IF_ACTIVATED(self, -1)
    MAKE_USVM_VIRUAL_CALL_NO_RETURN((VirtualPythonObject *) self, 0)
}
PyType_Slot Virtual_tp_setattro = {Py_tp_setattro, tp_setattro};

PyType_Slot final_slot = {0, NULL};


PyType_Slot *AVAILABLE_SLOTS = 0;
PyObject *ready_virtual_object_types = 0;


void
initialize_virtual_object_ready_types() {
    ready_virtual_object_types = PyDict_New();
}

void
deinitialize_virtual_object_ready_types() {
    Py_DECREF(ready_virtual_object_types);
    ready_virtual_object_types = 0;
}

void
initialize_virtual_object_available_slots() {
    AVAILABLE_SLOT_INITIALIZATION
}

void
deinitialize_virtual_object_available_slots() {
    PyMem_RawFree(AVAILABLE_SLOTS);
    AVAILABLE_SLOTS = 0;
}

#define MASK_SIZE (sizeof(unsigned char) * CHAR_BIT)

int mask_count_ones(const unsigned char mask) {
    unsigned char copy = mask;
    int count = 0;
    for (size_t i=0; i < MASK_SIZE; i++) {
        count += copy & 1;
        copy >>= 1;
    }
    return count;
}

/*
The length of the mask may be less than the number
of slots available.
In that case blank_byte will be used as the continuation
of the mask.
*/
PyType_Slot *
create_slot_list(const unsigned char *mask, size_t length) {
    PyType_Slot *slots = 0;
    int counter = 1;
    for (size_t i = 0; i < length; i++) {
        counter += mask_count_ones(mask[i]);
    }
    slots = PyMem_RawMalloc(sizeof(PyType_Slot) * counter);
    const unsigned char *current_byte = mask + length - 1;
    int i = 0, j = 0, k = 0;
    const unsigned char blank_byte = 0;

    while (AVAILABLE_SLOTS[k].slot) {
        if (*current_byte & (1 << j)) {
            slots[i++] = AVAILABLE_SLOTS[k];
        }
        j++;
        k++;
        if (j >= MASK_SIZE) {
            j = 0;
            if (k < MASK_SIZE * length) {
                current_byte--;
            } else {
                current_byte = &blank_byte;
            }
        }
    }
    slots[i++] = final_slot;
    return slots;
}

static PyTypeObject *
create_new_virtual_object_type(const unsigned char *mask, size_t length) {
    PyType_Slot *slots = create_slot_list(mask, length);
    PyType_Spec spec = {
        VirtualObjectTypeName,
        sizeof(VirtualPythonObject),
        0,
        Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HEAPTYPE,
        slots
    };
    PyTypeObject *result = (PyTypeObject*) PyType_FromSpec(&spec);
    PyMem_RawFree(slots);
    return result;
}

PyObject *
_allocate_raw_virtual_object(JNIEnv *env, jobject object, const unsigned char *mask, size_t length) {
    PyObject *mask_as_number = _PyLong_FromByteArray(mask, length, 0, 0);
    PyTypeObject *virtual_object_type = (PyTypeObject *) PyDict_GetItem(ready_virtual_object_types, mask_as_number);
    if (!virtual_object_type)
        virtual_object_type = create_new_virtual_object_type(mask, length);
    if (!virtual_object_type) {
        char err_str[200];
        sprintf(err_str, "Something went wrong in virtual object type creation");
        PyErr_SetString(PyExc_AssertionError, err_str);
        return 0;
    }

    PyDict_SetItem(ready_virtual_object_types, mask_as_number, (PyObject *)virtual_object_type);
    Py_DECREF(mask_as_number);

    VirtualPythonObject *result = PyObject_New(VirtualPythonObject, virtual_object_type);

    if (!result)
        return 0;

    result->reference = create_global_ref(env, object);
    result->ctx = 0;
    result->adapter = 0;

    return (PyObject *) result;
}

PyObject *
allocate_raw_virtual_object(JNIEnv *env, jobject object, jbyteArray mask) {
    unsigned char *mask_as_char_array = (unsigned char *) (*env)->GetByteArrayElements(env, mask, 0);
    const unsigned char *mask_as_array = (const unsigned char *) mask_as_char_array;
    size_t mask_length = (*env)->GetArrayLength(env, mask);
    PyObject *result = _allocate_raw_virtual_object(env, object, mask_as_array, mask_length);
    (*env)->ReleaseByteArrayElements(env, mask, (jbyte*) mask_as_char_array, 0);
    return result;
}

// Since there are about 80 slots, a mask with 96 bits (12 bytes) in it
// should be enough to cover all of them
#define MAX_NEEDED_MASK_BYTE_NUMBER 12

PyObject *
allocate_raw_virtual_object_with_all_slots(JNIEnv *env, jobject object) {
    const unsigned char all = 0b11111111; // This byte enables all 8 slots that сorrespond to it.
    const unsigned char mask[MAX_NEEDED_MASK_BYTE_NUMBER] = {all, all, all, all, all, all, all, all, all, all, all, all};
    return _allocate_raw_virtual_object(env, object, mask, MAX_NEEDED_MASK_BYTE_NUMBER);
}

void
finish_virtual_object_initialization(VirtualPythonObject *object, ConcolicContext *ctx, SymbolicAdapter *adapter) {
    object->ctx = ctx;
    object->adapter = adapter;
}

PyObject *
create_new_virtual_object(ConcolicContext *ctx, jobject object, SymbolicAdapter *adapter) {
    VirtualPythonObject *result = (VirtualPythonObject *) allocate_raw_virtual_object_with_all_slots(ctx->env, object);
    finish_virtual_object_initialization(result, ctx, adapter);

    return (PyObject *) result;
}

int
is_virtual_object(PyObject *obj) {
    if (!obj)
        return 0;
    return strcmp(Py_TYPE(obj)->tp_name, VirtualObjectTypeName) == 0; // Temporary solution
}   

void register_virtual_methods(SymbolicAdapter *adapter) {
    adapter->virtual_tp_richcompare = tp_richcompare;
    adapter->virtual_tp_getattro = tp_getattro;
    adapter->virtual_tp_iter = tp_iter;
    adapter->virtual_tp_call = tp_call;
    adapter->virtual_nb_add = nb_add;
    adapter->virtual_nb_subtract = nb_subtract;
    adapter->virtual_nb_multiply = nb_multiply;
    adapter->virtual_nb_matrix_multiply = nb_matrix_multiply;
    adapter->virtual_mp_subscript = mp_subscript;
    adapter->virtual_nb_positive = nb_positive;
    adapter->virtual_nb_negative = nb_negative;
}