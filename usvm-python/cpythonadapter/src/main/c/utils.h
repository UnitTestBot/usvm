#include <jni.h>
#include "Python.h"

#ifndef _Included_CPythonAdapter_utils
#define _Included_CPythonAdapter_utils
#ifdef __cplusplus
extern "C" {
#endif

#define JavaPythonObjectTypeName "ibmviqhlye.___java_object___ibmviqhlye"

typedef struct {
    JNIEnv *env;
    jclass cpython_adapter_cls;
    jmethodID handler_mid;
    jclass symbol_cls;
} JavaEnvironment;

typedef struct {
    PyObject_HEAD
    jobject reference;
    jobject object;
    JavaEnvironment *env;
} JavaPythonObject;

PyObject *wrap_java_object(JavaEnvironment *env, jobject object);

#ifdef __cplusplus
}
#endif
#endif