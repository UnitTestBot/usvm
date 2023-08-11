#include <stdlib.h>

#include "org_usvm_interpreter_CPythonAdapter.h"
#include "utils.h"
#include "converters.h"
#include "SymbolicAdapterMethods.h"  // generated from Gradle script

#include "symbolicadapter.h"
#include "virtual_objects.h"

#include "internal/pycore_frame.h"

#define SET_IS_INITIALIZED(value) \
    jclass cls = (*env)->GetObjectClass(env, cpython_adapter); \
    jfieldID f = (*env)->GetFieldID(env, cls, "isInitialized", "Z"); \
    (*env)->SetBooleanField(env, cpython_adapter, f, value);

#define SET_EXCEPTION_IN_CPYTHONADAPTER \
    PyObject *type, *value, *traceback; \
    PyErr_Fetch(&type, &value, &traceback); \
    jclass cls = (*env)->GetObjectClass(env, cpython_adapter); \
    jfieldID f = (*env)->GetFieldID(env, cls, "thrownException", "J"); \
    jfieldID f_type = (*env)->GetFieldID(env, cls, "thrownExceptionType", "J"); \
    (*env)->SetLongField(env, cpython_adapter, f, (jlong) value); \
    (*env)->SetLongField(env, cpython_adapter, f_type, (jlong) type); \
    Py_INCREF(value); \
    PyErr_Restore(type, value, traceback); \

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_initializePython(JNIEnv *env, jobject cpython_adapter) {
    PyConfig config;
    PyConfig_InitIsolatedConfig(&config);

    Py_InitializeFromConfig(&config);
    PyConfig_Clear(&config);
    SET_IS_INITIALIZED(JNI_TRUE);
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_finalizePython(JNIEnv *env, jobject cpython_adapter) {
    Py_FinalizeEx();
    SET_IS_INITIALIZED(JNI_FALSE);
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
    jboolean print_error_message
) {
    const char *c_code = (*env)->GetStringUTFChars(env, code, 0);

    PyObject *dict = (PyObject *) globals;
    PyObject *v = PyRun_StringFlags(c_code, Py_file_input, dict, dict, 0);
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
    jboolean print_error_message
) {
    const char *c_code = (*env)->GetStringUTFChars(env, code, 0);

    PyObject *dict = (PyObject *) globals;
    PyObject *v = PyRun_StringFlags(c_code, Py_eval_input, dict, dict, 0);
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
    jlongArray concrete_args
) {
    int n = (*env)->GetArrayLength(env, concrete_args);
    jlong *addresses = (*env)->GetLongArrayElements(env, concrete_args, 0);

    PyObject *args = PyTuple_New(n);
    for (int i = 0; i < n; i++) {
        PyTuple_SetItem(args, i, (PyObject *) addresses[i]);
    }
    PyObject *result = Py_TYPE(function_ref)->tp_call((PyObject *) function_ref, args, 0);

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
    jboolean print_error_message
) {
    PyObjectArray args;
    ConcolicContext ctx;

    PyObject *function = (PyObject *) function_ref;

    construct_concolic_context(env, context, cpython_adapter, &ctx);
    (*env)->SetLongField(env, cpython_adapter, ctx.cpython_java_exception_field, (jlong) ctx.java_exception);

    SymbolicAdapter *adapter = create_new_adapter(&ctx);
    register_virtual_methods(adapter);
    REGISTER_ADAPTER_METHODS(adapter);
    register_approximations(adapter);

    construct_args_for_symbolic_adapter(adapter, &ctx, &concrete_args, &virtual_args, &symbolic_args, &args);

    PyObject *result = SymbolicAdapter_run((PyObject *) adapter, function, args.size, args.ptr);
    free(args.ptr);

    if (result == NULL) {
        SET_EXCEPTION_IN_CPYTHONADAPTER
    }

    if (result == NULL && print_error_message == JNI_TRUE) {
        PyErr_Print();
    }
    PyErr_Clear();
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

JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_getPythonObjectRepr(JNIEnv *env, jobject _, jlong object_ref) {
    PyObject *repr = PyObject_Repr((PyObject *) object_ref);
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
    return take_instruction_from_frame((PyFrameObject *) frame_ref);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_getFunctionFromFrame(JNIEnv *env, jclass _, jlong frame_ref) {
    assert(PyFrame_Check(frame_ref));
    return (jlong) PyFrame_GetCode((PyFrameObject *) frame_ref);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_allocateVirtualObject(JNIEnv *env, jobject cpython_adapter, jobject virtual_object) {
    return (jlong) allocate_raw_virtual_object(env, virtual_object);
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

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbAdd(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_add;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbMultiply(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_multiply;
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasNbMatrixMultiply(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_as_number && type->tp_as_number->nb_matrix_multiply;
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

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpIter(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_iter != 0;
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

JNIEXPORT jthrowable JNICALL Java_org_usvm_interpreter_CPythonAdapter_extractException(JNIEnv *env, jobject _, jlong exception) {
    PyObject *wrapped = PyObject_GetAttrString((PyObject *) exception, "java_exception");
    assert(is_wrapped_java_object(wrapped));
    return ((JavaPythonObject *) wrapped)->reference;
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_decref(JNIEnv *env, jobject _, jlong obj_ref) {
    Py_XDECREF((PyObject *) obj_ref);
}