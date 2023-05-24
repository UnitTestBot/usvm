#include <stdlib.h>

#include "org_usvm_interpreter_CPythonAdapter.h"
#include "utils.h"

#include "symbolicadapter.h"

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    initializePython
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_initializePython(JNIEnv *env, jobject cpython_adapter) {
    Py_Initialize();

    PyObject *m = PyImport_AddModule("__main__");
    //printf("%ld\n", (jlong) m);
    //fflush(stdout);
    return (jlong) m;  // may be null
}

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    finalizePython
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_finalizePython(JNIEnv *env, jobject cpython_adapter) {
    Py_FinalizeEx();
}

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    concreteRun
 * Signature: (JLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_org_usvm_interpreter_CPythonAdapter_concreteRun(
    JNIEnv *env,
    jobject cpython_adapter,
    jlong main_module,
    jstring code
) {
    jboolean is_copy;
    const char *c_code = (*env)->GetStringUTFChars(env, code, &is_copy);

    PyObject *m = (PyObject *) main_module;
    PyObject *dict = PyModule_GetDict(m);
    PyObject *v = PyRun_StringFlags(c_code, Py_file_input, dict, dict, 0);
    if (v == NULL) {
        PyErr_Print();
        return 1;
    }

    return 0;
}

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    eval
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_org_usvm_interpreter_CPythonAdapter_eval(
    JNIEnv *env,
    jobject cpython_adapter,
    jlong main_module,
    jstring code
) {
    jboolean is_copy;
    const char *c_code = (*env)->GetStringUTFChars(env, code, &is_copy);

    PyObject *m = (PyObject *) main_module;
    PyObject *dict = PyModule_GetDict(m);
    PyObject *v = PyRun_StringFlags(c_code, Py_eval_input, dict, dict, 0);
    if (v == NULL) {
        PyErr_Print();
        return 0;
    }

    return (jlong) v;
}

/*
 * Class:     org_usvm_interpreter_CPythonAdapter
 * Method:    concolicRun
 * Signature: (JLjava/lang/String;[Lorg/usvm/language/Symbol;)V
 */
JNIEXPORT void JNICALL Java_org_usvm_interpreter_CPythonAdapter_concolicRun(JNIEnv *env, jobject cpython_adapter, jlong main_module, jstring function_name, jobjectArray symbolic_args) {
    // TODO
}