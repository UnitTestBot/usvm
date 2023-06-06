#include <jni.h>
#include "Python.h"
#include "CPythonAdapterMethods.h"  // this is generated in Gradle script from "handler_defs.json"

#ifndef _Included_CPythonAdapter_utils
#define _Included_CPythonAdapter_utils
#ifdef __cplusplus
extern "C" {
#endif

#define JavaPythonObjectTypeName "ibmviqhlye.___java_object___ibmviqhlye"

typedef struct {
    PyObject_HEAD
    jobject reference;
    jobject object;
    JNIEnv *env;
} JavaPythonObject;

PyObject *wrap_java_object(JNIEnv *env, jobject object);
int is_wrapped_java_object(PyObject *object);

typedef struct {
    jobject context;
    JNIEnv *env;
    jclass cpython_adapter_cls;
    jobject cpython_adapter;
    HANDLERS_DEFS
} ConcolicContext;

void construct_concolic_context(JNIEnv *env, jobject context, jobject cpython_adapter, ConcolicContext *dist);

typedef struct {
    int size;
    PyObject **ptr;
} PyObjectArray;

void construct_args_for_symbolic_adapter(JNIEnv *env, jlongArray *concrete_args, jobjectArray symbolic_args, PyObjectArray *dist);
int take_instruction_from_frame(PyObject *frame);

#ifdef __cplusplus
}
#endif
#endif