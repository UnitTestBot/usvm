#include <stdlib.h>

#include "org_usvm_interpreter_CPythonAdapter.h"
#include "utils.h"
#include "symbolic_handler.h"

#include "symbolicadapter.h"

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

JNIEXPORT int JNICALL Java_org_usvm_interpreter_CPythonAdapter_concolicRun(
    JNIEnv *env,
    jobject cpython_adapter,
    jlong globals,
    jlong function_ref,
    jlongArray concrete_args,
    jobjectArray symbolic_args,
    jobject context
) {
    PyObjectArray args;
    ConcolicContext ctx;

    PyObject *function = (PyObject *) function_ref;
    //printf("CONCOLIC RUN on \n");
    //PyObject_Print(function, stdout, 0);
    //printf("\n");
    //fflush(stdout);

    construct_concolic_context(env, context, cpython_adapter, &ctx);
    SymbolicAdapter *adapter = create_new_adapter(handler, &ctx);
    construct_args_for_symbolic_adapter(env, &concrete_args, symbolic_args, &args);
    PyObject *result = SymbolicAdapter_run((PyObject *) adapter, function, args.size, args.ptr);
    free(args.ptr);

    if (result == NULL) {
        PyErr_Print();
        return 1;
    }

    PyObject_Print(result, stdout, 0);
    printf("\n");
    fflush(stdout);
    return 0;
}