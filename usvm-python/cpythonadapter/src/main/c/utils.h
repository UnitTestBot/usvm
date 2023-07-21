#ifndef _Included_CPythonAdapter_utils
#define _Included_CPythonAdapter_utils
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "Python.h"
#include "symbolicadapter.h"
#include "CPythonAdapterMethods.h"  // this is generated in Gradle script from "handler_defs.json"

#define JavaPythonObjectTypeName "ibmviqhlye.___java_object___ibmviqhlye"

typedef struct {
    PyObject_HEAD
    jobject reference; // local
    JNIEnv *env;
} JavaPythonObject;

PyObject *wrap_java_object(JNIEnv *env, jobject object);
int is_wrapped_java_object(PyObject *object);

typedef struct {
    jobject context;
    JNIEnv *env;
    jclass cpython_adapter_cls;
    jobject cpython_adapter;
    jclass symbol_cls;
    HANDLERS_DEFS
} ConcolicContext;

void construct_concolic_context(JNIEnv *env, jobject context, jobject cpython_adapter, ConcolicContext *dist);

typedef struct {
    int size;
    PyObject **ptr;
} PyObjectArray;

void construct_args_for_symbolic_adapter(SymbolicAdapter *adapter, ConcolicContext *ctx, jlongArray *concrete_args, jlongArray *virtual_args, jobjectArray *symbolic_args, PyObjectArray *dist);
int take_instruction_from_frame(PyFrameObject *frame);

#define CHECK_FOR_EXCEPTION(ctx, fail_value) \
    if ((*ctx->env)->ExceptionCheck(ctx->env)) { \
        PyErr_SetString(PyExc_RuntimeError, "Java exception"); \
        return fail_value; \
    }

#define CALL_JAVA_METHOD(result, ctx, func, args...) \
    result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_##func, args); \
    CHECK_FOR_EXCEPTION(ctx, Py_None)

#define CALL_JAVA_METHOD_CUSTOM_FAIL(fail_value, result, ctx, func, args...) \
    result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_##func, args); \
    CHECK_FOR_EXCEPTION(ctx, fail_value)

#ifdef __cplusplus
}
#endif
#endif