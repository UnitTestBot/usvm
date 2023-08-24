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
    jclass virtual_cls;
    PyObject *java_exception;
    jfieldID cpython_thrown_exception_field;
    jfieldID cpython_java_exception_field;
    HANDLERS_DEFS
} ConcolicContext;

void construct_concolic_context(JNIEnv *env, jobject context, jobject cpython_adapter, ConcolicContext *dist);
void register_approximations(SymbolicAdapter *adapter);

typedef struct {
    int size;
    PyObject **ptr;
} PyObjectArray;

void construct_args_for_symbolic_adapter(SymbolicAdapter *adapter, ConcolicContext *ctx, jlongArray *concrete_args, jlongArray *virtual_args, jobjectArray *symbolic_args, PyObjectArray *dist);
int take_instruction_from_frame(PyFrameObject *frame);
int extract_int_value(PyObject *int_object);

#define CHECK_FOR_EXCEPTION(ctx, fail_value) \
    jthrowable cur_exception = (*ctx->env)->ExceptionOccurred(ctx->env); \
    if (cur_exception && !PyErr_Occurred()) { \
        (*ctx->env)->ExceptionClear(ctx->env); \
        PyObject *exception_instance = ((PyTypeObject *)ctx->java_exception)->tp_new((PyTypeObject *)ctx->java_exception, 0, 0); \
        PyObject_SetAttrString(exception_instance, "java_exception", wrap_java_object(ctx->env, cur_exception)); \
        PyErr_SetObject(ctx->java_exception, exception_instance); \
        return fail_value; \
    }

#define CALL_JAVA_METHOD(result, ctx, func, args...) \
    result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_##func, args); \
    CHECK_FOR_EXCEPTION(ctx, Py_None)

#define CALL_JAVA_METHOD_CUSTOM_FAIL(fail_value, result, ctx, func, args...) \
    result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_##func, args); \
    CHECK_FOR_EXCEPTION(ctx, fail_value)

int audit_hook(const char *event, PyObject *args, void *data);

#ifdef __cplusplus
}
#endif
#endif