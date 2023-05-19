#include <stdlib.h>

#include "org_usvm_interpreter_CPythonAdapter.h"
#include "utils.h"

#include "symbolicadapter.h"

static PyObject *
symbolic_handler(Py_ssize_t n, PyObject *const *args, void *param) {
    JavaEnvironment *env = (JavaEnvironment *) param;
    printf("IN SYMBOLIC HANDLER:");
    int k = 0;
    jobject *buf = malloc(sizeof(jobject) * n);
    for (int i = 0; i < n; i++) {
        printf(" ");
        PyObject_Print(args[i], stdout, 0);
        if (strcmp(Py_TYPE(args[i])->tp_name, JavaPythonObjectTypeName) == 0) {
            buf[k++] = ((JavaPythonObject *) args[i])->object;
        }
    }
    printf(" (java: %d)\n", k);
    fflush(stdout);

    char cmd_buf[100];
    Py_ssize_t sz = PyObject_Length(args[0]);
    for (int i = 0; i < sz; i++) {
        cmd_buf[i] = PyUnicode_READ(PyUnicode_4BYTE_KIND, PyUnicode_AS_DATA(args[0]), i);
    }
    cmd_buf[sz] = 0;

    jstring cmd = (*env->env)->NewStringUTF(env->env, cmd_buf);
    jobjectArray java_args = (*env->env)->NewObjectArray(env->env, k, env->symbol_cls, 0);
    for (int i = 0; i < k; i++) {
        (*env->env)->SetObjectArrayElement(env->env, java_args, i, buf[i]);
    }

    free(buf);
    jobject java_result = (*env->env)->CallStaticObjectMethod(env->env, env->cpython_adapter_cls, env->handler_mid, cmd, java_args);

    PyObject *result = wrap_java_object(env, java_result);

    if (PyUnicode_CompareWithASCIIString(args[0], "LOAD_CONST") == 0 || PyUnicode_CompareWithASCIIString(args[0], "BUILD_LIST") == 0) {
        PyObject *list = PyList_New(1);
        PyList_SetItem(list, 0, result);
        result = list;
    }

    return result;
}

JNIEXPORT void JNICALL
Java_CPythonAdapter_run(JNIEnv *env, jobject cpython_adapter, jstring code, jstring func_name, jobjectArray symbolic_args) {
    Py_Initialize();
    JavaEnvironment j_env;

    jboolean is_copy1, is_copy2;
    const char *c_code = (*env)->GetStringUTFChars(env, code, &is_copy1);
    const char *c_func_name = (*env)->GetStringUTFChars(env, func_name, &is_copy2);

    j_env.cpython_adapter_cls = (*env)->GetObjectClass(env, cpython_adapter);
    j_env.handler_mid = (*env)->GetStaticMethodID(env, j_env.cpython_adapter_cls, "handler", "(Ljava/lang/String;[LSymbol;)LSymbol;");
    j_env.env = env;
    j_env.symbol_cls = (*env)->FindClass(env, "Symbol");

    SymbolicAdapter *adapter = create_new_adapter(symbolic_handler, &j_env);

    char *cmd = malloc(strlen(c_func_name) + 10);
    sprintf(cmd, "eval(\"%s\")", c_func_name);
    PyObject *function = run_python(c_code, cmd);
    Py_ssize_t n = (*env)->GetArrayLength(env, symbolic_args);
    PyObject **args = malloc(sizeof(PyObject *) * n);
    for (int i = 0; i < n; i++) {
        args[i] = PyTuple_New(2);
        PyTuple_SetItem(args[i], 0, Py_None);  // concrete value
        jobject symbolic = (*env)->GetObjectArrayElement(env, symbolic_args, i);
        PyTuple_SetItem(args[i], 1, wrap_java_object(&j_env, symbolic));
    }

    PyObject *result = SymbolicAdapter_run((PyObject *) adapter, function, n, args);
    printf("RESULT: ");
    PyObject_Print(result, stdout, 0);
    printf("\n");

    free(cmd);
    free(args);
    Py_FinalizeEx();  // free Python
}