#include <stdlib.h>

#include "org_usvm_interpreter_CPythonAdapter.h"
#include "utils.h"
#include "symbolic_handler.h"

#include "symbolicadapter.h"
#include "virtual_objects.h"

#define SET_IS_INITIALIZED(value) \
    jclass cls = (*env)->GetObjectClass(env, cpython_adapter); \
    jfieldID f = (*env)->GetFieldID(env, cls, "isInitialized", "Z"); \
    (*env)->SetBooleanField(env, cpython_adapter, f, value);

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_initializePython(JNIEnv *env, jobject cpython_adapter) {
    Py_Initialize();
    SET_IS_INITIALIZED(JNI_TRUE);
}

JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_finalizePython(JNIEnv *env, jobject cpython_adapter) {
    Py_FinalizeEx();
    SET_IS_INITIALIZED(JNI_FALSE);
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_getNewNamespace(JNIEnv *env, jobject cpython_adapter) {
    return (jlong) PyDict_New();
}

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_concreteRun(
    JNIEnv *env,
    jobject cpython_adapter,
    jlong globals,
    jstring code
) {
    const char *c_code = (*env)->GetStringUTFChars(env, code, 0);

    PyObject *dict = (PyObject *) globals;
    PyObject *v = PyRun_StringFlags(c_code, Py_file_input, dict, dict, 0);
    (*env)->ReleaseStringUTFChars(env, code, c_code);
    if (v == NULL) {
        PyErr_Print();
        return 1;
    }

    return 0;
}

JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_eval(
    JNIEnv *env,
    jobject cpython_adapter,
    jlong globals,
    jstring code
) {
    const char *c_code = (*env)->GetStringUTFChars(env, code, 0);

    PyObject *dict = (PyObject *) globals;
    PyObject *v = PyRun_StringFlags(c_code, Py_eval_input, dict, dict, 0);
    (*env)->ReleaseStringUTFChars(env, code, c_code);
    if (v == NULL) {
        PyErr_Print();
        return 0;
    }

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
    if (result == 0)
        PyErr_Print();

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
    SymbolicAdapter *adapter = create_new_adapter(handler, &ctx);
    register_virtual_methods();

    construct_args_for_symbolic_adapter(adapter, &ctx, &concrete_args, &virtual_args, &symbolic_args, &args);

    PyObject *result = SymbolicAdapter_run((PyObject *) adapter, function, args.size, args.ptr);
    free(args.ptr);

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

JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_getPythonObjectRepr(JNIEnv *env, jobject cpython_adapter, jlong object_ref) {
    PyObject *repr = PyObject_Repr((PyObject *) object_ref);
    const char *repr_as_string = PyUnicode_AsUTF8AndSize(repr, 0);
    return (*env)->NewStringUTF(env, repr_as_string);
}

JNIEXPORT jstring JNICALL Java_org_usvm_interpreter_CPythonAdapter_getPythonObjectTypeName(JNIEnv *env, jobject cpython_adapter, jlong object_ref) {
    const char *type_name = Py_TYPE(object_ref)->tp_name;
    return (*env)->NewStringUTF(env, type_name);
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

JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_typeHasTpRichcmp(JNIEnv *env, jobject _, jlong type_ref) {
    QUERY_TYPE_HAS_PREFIX
    return type->tp_richcompare != 0;
}