#include <stdlib.h>

#include "org_usvm_interpreter_CPythonAdapter.h"
#include "utils.h"
#include "converters.h"
#include "virtual_objects.h"
#include "approximations.h"
#include "descriptors.h"
#include "symbolic_methods.h"
#include "manual_handlers.h"
#include "CPythonFunctions.h"  // generated
#include "SymbolicMethods.h"  // generated

#include "symbolicadapter.h"

#define SET_BOOLEAN_FIELD(field_name, value) \
    f = (*env)->GetFieldID(env, cls, field_name, "Z"); \
    (*env)->SetBooleanField(env, cpython_adapter, f, value);

#define SET_INTEGER_FIELD(field_name, value) \
    f = (*env)->GetFieldID(env, cls, field_name, "I"); \
    (*env)->SetIntField(env, cpython_adapter, f, value);

#define SET_LONG_FIELD(field_name, value) \
    f = (*env)->GetFieldID(env, cls, field_name, "J"); \
    (*env)->SetLongField(env, cpython_adapter, f, value);

#define SET_EXCEPTION_IN_CPYTHONADAPTER \
    PyObject *type, *value, *traceback; \
    PyErr_Fetch(&type, &value, &traceback); \
    jclass cls = (*env)->GetObjectClass(env, cpython_adapter); \
    jfieldID f = (*env)->GetFieldID(env, cls, "thrownException", "J"); \
    jfieldID f_type = (*env)->GetFieldID(env, cls, "thrownExceptionType", "J"); \
    (*env)->SetLongField(env, cpython_adapter, f, (jlong) value); \
    (*env)->SetLongField(env, cpython_adapter, f_type, (jlong) type); \
    Py_INCREF(value); \
    Py_INCREF(type); \
    PyErr_Restore(type, value, traceback); \

const char *illegal_operation = 0;

static void
turn_on_audit_hook() {
    illegal_operation = "active";
}

static void
turn_off_audit_hook() {
    if (strcmp(illegal_operation, "active") == 0)
        illegal_operation = 0;
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_initializePython(JNIEnv *env, jobject cpython_adapter, jstring python_home) {
    PyPreConfig pre_config;
    PyPreConfig_InitIsolatedConfig(&pre_config);
    // pre_config.allocator = PYMEM_ALLOCATOR_MALLOC_DEBUG;
    Py_PreInitialize(&pre_config);

    PyConfig config;
    PyConfig_InitIsolatedConfig(&config);
    const char *python_home_str = (*env)->GetStringUTFChars(env, python_home, 0);
    PyConfig_SetBytesString(&config, &config.home, python_home_str);
    (*env)->ReleaseStringUTFChars(env, python_home, python_home_str);

    Py_InitializeFromConfig(&config);
    PyConfig_Clear(&config);

    jclass cls = (*env)->GetObjectClass(env, cpython_adapter);
    jfieldID f;
    SET_BOOLEAN_FIELD("isInitialized", JNI_TRUE)
    SET_INTEGER_FIELD("pyEQ", Py_EQ)
    SET_INTEGER_FIELD("pyNE", Py_NE)
    SET_INTEGER_FIELD("pyLT", Py_LT)
    SET_INTEGER_FIELD("pyLE", Py_LE)
    SET_INTEGER_FIELD("pyGT", Py_GT)
    SET_INTEGER_FIELD("pyGE", Py_GE)
    SET_LONG_FIELD("pyNoneRef", (jlong) Py_None)

    initialize_java_python_type();
    initialize_virtual_object_available_slots();
    initialize_virtual_object_ready_types();

    PySys_AddAuditHook(audit_hook, &illegal_operation);

    SYMBOLIC_METHOD_INITIALIZATION
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_initializeSpecialApproximations(JNIEnv *env, jobject _) {
    INITIALIZE_PYTHON_APPROXIMATIONS
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_finalizePython(JNIEnv *env, jobject cpython_adapter) {
    deinitialize_virtual_object_available_slots();
    deinitialize_virtual_object_ready_types();
    release_global_refs(env);
    clean_methods();
    Py_FinalizeEx();
    jclass cls = (*env)->GetObjectClass(env, cpython_adapter);
    jfieldID f;
    SET_BOOLEAN_FIELD("isInitialized", JNI_FALSE)
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_pythonExceptionOccurred(JNIEnv *env, jclass _) {
    return PyErr_Occurred() != 0;
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_getNewNamespace(JNIEnv *env, jobject cpython_adapter) {
    return (jlong) PyDict_New();
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_addName(JNIEnv *env, jobject _, jlong dict, jlong ref, jstring name) {
    assert(PyDict_Check((PyObject *) dict));
    const char *c_name = (*env)->GetStringUTFChars(env, name, 0);
    PyDict_SetItemString((PyObject *) dict, c_name, (PyObject *) ref);
    (*env)->ReleaseStringUTFChars(env, name, c_name);
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_concreteRun(
    JNIEnv *env,
    jobject cpython_adapter,
    jlong globals,
    jstring code,
    jboolean print_error_message,
    jboolean set_hook
) {
    const char *c_code = (*env)->GetStringUTFChars(env, code, 0);

    PyObject *dict = (PyObject *) globals;
    if (set_hook)
        turn_on_audit_hook();
    PyObject *v = PyRun_StringFlags(c_code, Py_file_input, dict, dict, 0);
    if (set_hook)
        turn_off_audit_hook();
    (*env)->ReleaseStringUTFChars(env, code, c_code);
    if (v == NULL) {
        if (print_error_message)
            PyErr_Print();
        else
            PyErr_Clear();
        return 1;
    }

    return 0;
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_eval(
    JNIEnv *env,
    jobject cpython_adapter,
    jlong globals,
    jstring code,
    jboolean print_error_message,
    jboolean set_hook
) {
    const char *c_code = (*env)->GetStringUTFChars(env, code, 0);

    PyObject *dict = (PyObject *) globals;
    if (set_hook)
        turn_on_audit_hook();
    PyObject *v = PyRun_StringFlags(c_code, Py_eval_input, dict, dict, 0);
    if (set_hook)
        turn_off_audit_hook();
    (*env)->ReleaseStringUTFChars(env, code, c_code);
    if (v == NULL) {
        if (print_error_message)
            PyErr_Print();
        else
            PyErr_Clear();
        return 0;
    }

    Py_INCREF(v);
    return (jlong) v;
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_concreteRunOnFunctionRef(
    JNIEnv *env,
    jobject cpython_adapter,
    jlong function_ref,
    jlongArray concrete_args,
    jboolean set_hook
) {
    int n = (*env)->GetArrayLength(env, concrete_args);
    jlong *addresses = (*env)->GetLongArrayElements(env, concrete_args, 0);

    PyObject *args = PyTuple_New(n);
    for (int i = 0; i < n; i++) {
        PyTuple_SetItem(args, i, (PyObject *) addresses[i]);
    }
    if (set_hook)
        turn_on_audit_hook();
    PyObject *result = Py_TYPE(function_ref)->tp_call((PyObject *) function_ref, args, 0);
    if (set_hook)
        turn_off_audit_hook();

    if (result == NULL) {
        SET_EXCEPTION_IN_CPYTHONADAPTER
        PyErr_Clear();
    }

    Py_DECREF(args);
    (*env)->ReleaseLongArrayElements(env, concrete_args, addresses, 0);

    return (jlong) result;
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_concolicRun(
    JNIEnv *env,
    jobject cpython_adapter,
    jlong function_ref,
    jlongArray concrete_args,
    jlongArray virtual_args,
    jobjectArray symbolic_args,
    jobject context,
    jobjectArray global_clones,
    jboolean print_error_message
) {
    PyObjectArray args;
    ConcolicContext ctx;

    PyObject *function = (PyObject *) function_ref;

    construct_concolic_context(env, context, cpython_adapter, &ctx);
    (*env)->SetLongField(env, cpython_adapter, ctx.cpython_java_exception_field, (jlong) ctx.java_exception);

    PyObject *global_clones_dict = construct_global_clones_dict(env, global_clones);
    SymbolicAdapter *adapter = create_new_adapter(&ctx, global_clones_dict);
    ctx.adapter = adapter;
    register_virtual_methods(adapter);
    REGISTER_ADAPTER_METHODS(adapter);
    adapter->symbolic_tp_call = handler_symbolic_tp_call;
    adapter->is_pycfunction_with_approximation = handler_is_pycfunction_with_approximation;
    adapter->approximate_pycfunction_call = handler_approximate_pycfunction_call;
    adapter->extract_symbolic_self_from_pycfunction = handler_extract_self_from_method;
    adapter->extract_self_from_method = handler_extract_self_from_method;
    adapter->approximate_type_call = handler_approximate_type_call;
    register_approximations(adapter);

    construct_args_for_symbolic_adapter(adapter, &ctx, &concrete_args, &virtual_args, &symbolic_args, &args);

    assert(!PyErr_Occurred());
    assert(!(*env)->ExceptionCheck(env));
    PyObject *result = SymbolicAdapter_run((PyObject *) adapter, function, args.size, args.ptr, turn_on_audit_hook, turn_off_audit_hook);
    free(args.ptr);

    if (result == NULL) {
        SET_EXCEPTION_IN_CPYTHONADAPTER
    }

    if (result == NULL && print_error_message == JNI_TRUE) {
        PyErr_Print();
    }
    PyErr_Clear();
    // Py_DECREF(adapter);  TODO
    Py_DECREF(global_clones);
    return (jlong) result;
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_printPythonObject(JNIEnv *env, jobject cpython_adapter, jlong object_ref) {
    PyObject_Print((PyObject *) object_ref, stdout, 0);
    printf("\n");
    //if (Py_TYPE(object_ref) == &PyType_Type)
    //    printf("tp_new: %p\n", ((PyTypeObject *) object_ref)->tp_new);

    fflush(stdout);
}

JNIEXPORT jlongArray JNICALL Java_org_usvm_interpreter_CPythonAdapter_getIterableElements(JNIEnv *env, jobject _, jlong ref) {
    PyObject *obj = (PyObject *) ref;
    int n = PyObject_Size(obj);
    jlong *elements = malloc(n * sizeof(jlong));
    for (int i = 0; i < n; i++) {
        elements[i] = (jlong) PyObject_GetItem(obj, PyLong_FromLong(i));
    }

    jlongArray result = (*env)->NewLongArray(env, n);
    (*env)->SetLongArrayRegion(env, result, 0, n, elements);
    free(elements);

    return result;
}

/*
static int
trace_function(PyObject *obj, PyFrameObject *frame, int what, PyObject *arg) {
    PyObject_SetAttrString((PyObject *) frame, "f_trace_opcodes", Py_True);
    // frame->f_trace_opcodes = 1;
    // printf("Inside tracer!\n"); fflush(stdout);
    Py_ssize_t lasti = PyFrame_GetLasti(frame);
    PyObject *code_bytes = PyCode_GetCode(PyFrame_GetCode(frame));
    assert(PyBytes_Check(code_bytes));
    PyObject *cur_instruction_as_long = PySequence_GetItem(code_bytes, lasti);
    assert(PyLong_Check(cur_instruction_as_long));
    long cur_instruction = PyLong_AsLong(cur_instruction_as_long);
    // printf("instruction: %ld\n", cur_instruction); fflush(stdout);
    return 0;
}
*/

JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_getPythonObjectRepr(JNIEnv *env, jobject _, jlong object_ref, jboolean print_error_message) {
    assert(!PyErr_Occurred());
    PyObject *repr = PyObject_Repr((PyObject *) object_ref);
    if (!repr && !print_error_message) {
        PyErr_Clear();
        return 0;
    }
    if (!repr && print_error_message) {
        PyErr_Print();
        return 0;
    }
    const char *repr_as_string = PyUnicode_AsUTF8AndSize(repr, 0);
    return (*env)->NewStringUTF(env, repr_as_string);
}

JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_getPythonObjectStr(JNIEnv *env, jobject _, jlong object_ref) {
    assert(!PyErr_Occurred());
    PyObject *repr = PyObject_Str((PyObject *) object_ref);
    if (!repr) {
        PyErr_Clear();
        return 0;
    }
    const char *repr_as_string = PyUnicode_AsUTF8AndSize(repr, 0);
    return (*env)->NewStringUTF(env, repr_as_string);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_getAddressOfReprFunction(JNIEnv *env, jobject _, jlong object_ref) {
    return (jlong) ((PyTypeObject *) object_ref)->tp_repr;
}

JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_getPythonObjectTypeName(JNIEnv *env, jobject _, jlong object_ref) {
    const char *type_name = Py_TYPE(object_ref)->tp_name;
    return (*env)->NewStringUTF(env, type_name);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_getPythonObjectType(JNIEnv *env, jobject _, jlong object_ref) {
    return (jlong) Py_TYPE(object_ref);
}

JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_getNameOfPythonType(JNIEnv *env, jobject _, jlong type_ref) {
    assert(PyType_Check((PyObject *) type_ref));
    const char *type_name = ((PyTypeObject *) type_ref)->tp_name;
    return (*env)->NewStringUTF(env, type_name);
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_getInstructionFromFrame(JNIEnv *env, jclass _, jlong frame_ref) {
    assert(PyFrame_Check(frame_ref));
    if (PyErr_Occurred()) {
        PyObject *type, *value, *traceback;
        PyErr_Fetch(&type, &value, &traceback);
        printf("Exception type: ");
        PyObject_Print(type, stdout, 0);
        printf("\nException value: ");
        PyObject_Print(value, stdout, 0);
        printf("\nTraceback: ");
        PyObject_Print(traceback, stdout, 0);
        fflush(stdout);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
        }
        assert(0);
    }
    return take_instruction_from_frame((PyFrameObject *) frame_ref);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_getCodeFromFrame(JNIEnv *env, jclass _, jlong frame_ref) {
    assert(PyFrame_Check(frame_ref));
    return (jlong) PyFrame_GetCode((PyFrameObject *) frame_ref);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_allocateRawVirtualObjectWithAllSlots(JNIEnv *env, jobject cpython_adapter, jobject virtual_object) {
    return (jlong) allocate_raw_virtual_object_with_all_slots(env, virtual_object);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_allocateRawVirtualObject(
    JNIEnv *env,
    jobject cpython_adapter,
    jobject virtual_object,
    jbyteArray mask
) {
    return (jlong) allocate_raw_virtual_object(env, virtual_object, mask);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_makeList(JNIEnv *env, jobject cpython_adapter, jlongArray elements) {
    int size = (*env)->GetArrayLength(env, elements);
    PyObject *result = PyList_New(size);
    jlong *addresses = (*env)->GetLongArrayElements(env, elements, 0);
    for (int i = 0; i < size; i++) {
        PyList_SET_ITEM(result, i, (PyObject *) addresses[i]);
        Py_INCREF(addresses[i]);
    }
    Py_INCREF(result);
    (*env)->ReleaseLongArrayElements(env, elements, addresses, 0);
    return (jlong) result;
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_allocateTuple(JNIEnv *env, jobject _, jint size) {
    return (jlong) PyTuple_New(size);
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_setTupleElement(JNIEnv *env, jobject _, jlong tuple_ref, jint index, jlong elem_ref) {
    assert(elem_ref);
    Py_INCREF(elem_ref);
    PyTuple_SetItem((PyObject *) tuple_ref, index, (PyObject *) elem_ref);
}

#define QUERY_TYPE_HAS_PREFIX \
    if (Py_TYPE(type_ref) != &PyType_Type) \
            return -1; \
    PyTypeObject *type = (PyTypeObject *) type_ref; \


JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbBool(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_bool;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbInt(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_int;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbIndex(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_index;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbAdd(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_add;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbSubtract(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_subtract;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbMultiply(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_multiply;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbMatrixMultiply(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_matrix_multiply;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbNegative(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_negative;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbPositive(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_positive;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasSqConcat(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_sequence && type->tp_as_sequence->sq_concat;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasSqLength(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_sequence && type->tp_as_sequence->sq_length;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasMpLength(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_mapping && type->tp_as_mapping->mp_length;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasMpSubscript(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_mapping && type->tp_as_mapping->mp_subscript;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasMpAssSubscript(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_mapping && type->tp_as_mapping->mp_ass_subscript;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpRichcmp(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_richcompare != 0;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpGetattro(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_getattro != 0;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpSetattro(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_setattro != 0;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpIter(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_iter != 0;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpCall(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_call != 0;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpHash(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_hash != 0;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpDescrGet(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_descr_get != 0;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpDescrSet(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_descr_set != 0;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasStandardNew(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_new == PyBaseObject_Type.tp_new;
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_callStandardNew(JNIEnv *env, jobject _, jlong type_ref) {
    assert(Py_TYPE(type_ref) == &PyType_Type);
    PyTypeObject *type = (PyTypeObject *) type_ref;
    assert(type->tp_new == PyBaseObject_Type.tp_new);
    PyObject *arg_tuple = PyTuple_Pack(0);
    PyObject *result = PyBaseObject_Type.tp_new(type, arg_tuple, 0);
    Py_DECREF(arg_tuple);
    return (jlong) result;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasStandardTpGetattro(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_getattro == PyObject_GenericGetAttr && (type->tp_flags & Py_TPFLAGS_MANAGED_DICT);
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasStandardTpSetattro(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_setattro == PyObject_GenericSetAttr && (type->tp_flags & Py_TPFLAGS_MANAGED_DICT);
}

JNIEXPORT jthrowable JNICALL Java_org_usvm_interpreter_CPythonAdapter_extractException(JNIEnv *env, jobject _, jlong exception) {
    PyObject *wrapped = PyObject_GetAttrString((PyObject *) exception, "java_exception");
    if (!is_wrapped_java_object(wrapped)) {
        PyObject_Print(wrapped, stdout, 0);
        fflush(stdout);
    }
    assert(is_wrapped_java_object(wrapped));
    return ((JavaPythonObject *) wrapped)->reference;
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_decref(JNIEnv *env, jobject _, jlong obj_ref) {
    Py_XDECREF((PyObject *) obj_ref);
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_incref(JNIEnv *env, jobject _, jlong obj_ref) {
    Py_XINCREF((PyObject *) obj_ref);
}

JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_checkForIllegalOperation(JNIEnv *env, jobject _) {
    if (!illegal_operation)
        return 0;
    return (*env)->NewStringUTF(env, illegal_operation);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeLookup(JNIEnv *env, jobject _, jlong type_ref, jstring name) {
    assert(!PyErr_Occurred());
    assert(PyType_Check((PyObject *) type_ref));
    const char *c_name = (*env)->GetStringUTFChars(env, name, 0);
    PyObject *py_name = PyUnicode_FromString(c_name);
    PyObject *result = _PyType_Lookup((PyTypeObject *) type_ref, py_name);
    (*env)->ReleaseStringUTFChars(env, name, c_name);
    Py_DECREF(py_name);
    return (jlong) result;
}

JNIEXPORT jobject JNICALL Java_org_usvm_interpreter_CPythonAdapter_getSymbolicDescriptor(JNIEnv *env, jobject adapter, jlong descr_ref) {
    return get_symbolic_descriptor(env, adapter, (PyObject *) descr_ref);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_constructPartiallyAppliedSymbolicMethod(JNIEnv *env, jobject _, jobject self, jlong method_ref) {
    assert(method_ref);
    return (jlong) construct_symbolic_method_with_self(env, self, (call_type) method_ref);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_constructApproximation(JNIEnv *env, jobject _, jobject self, jlong method_ref, jlong approximation_ref) {
    assert(approximation_ref);
    return (jlong) construct_approximation(env, self, (call_type) method_ref, (PyObject *) approximation_ref);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_constructPartiallyAppliedPythonMethod(JNIEnv *env, jobject _, jobject self) {
    return (jlong) construct_python_method_with_self(env, self);
}