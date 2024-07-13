#ifndef _Included_CPythonAdapter_utils
#define _Included_CPythonAdapter_utils
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "Python.h"
#include "symbolicadapter.h"
#include "CPythonAdapterMethods.h"  // generated

#define JavaPythonObjectTypeName "ibmviqhlye.___java_object___ibmviqhlye"

typedef struct {
    PyObject_HEAD
    jobject reference; // global
} JavaPythonObject;

void initialize_java_python_type();
PyObject *wrap_java_object(JNIEnv *env, jobject object);
int is_wrapped_java_object(PyObject *object);

typedef struct {
    SymbolicAdapter *adapter;
    jobject context;
    JNIEnv *env;
    jclass cpython_adapter_cls;
    jobject cpython_adapter;
    jclass symbol_cls;
    jclass virtual_cls;
    PyObject *java_exception;
    jfieldID cpython_thrown_exception_field;
    jfieldID cpython_java_exception_field;
    jfieldID symbol_tp_call_ref;
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
long extract_long_value(PyObject *int_object);

#define CHECK_FOR_EXCEPTION(ctx, fail_value) \
    jthrowable cur_exception = (*ctx->env)->ExceptionOccurred(ctx->env); \
    if (cur_exception && !PyErr_Occurred()) { \
        /*(*ctx->env)->ExceptionDescribe(ctx->env);*/ \
        (*ctx->env)->ExceptionClear(ctx->env); \
        PyObject *exception_instance = ((PyTypeObject *)ctx->java_exception)->tp_new((PyTypeObject *)ctx->java_exception, 0, 0); \
        PyObject_SetAttrString(exception_instance, "java_exception", wrap_java_object(ctx->env, cur_exception)); \
        PyErr_SetObject(ctx->java_exception, exception_instance); \
        return fail_value; \
    }

#define CALL_JAVA_METHOD(result, ctx, func, ...) \
    result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_##func, __VA_ARGS__); \
    CHECK_FOR_EXCEPTION(ctx, Py_None)

#define CALL_JAVA_METHOD_CUSTOM_FAIL(fail_value, result, ctx, func, ...) \
    result = (*ctx->env)->CallStaticObjectMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_##func, __VA_ARGS__); \
    CHECK_FOR_EXCEPTION(ctx, fail_value)

int audit_hook(const char *event, PyObject *args, void *data);
PyObject *construct_global_clones_dict(JNIEnv *env, jobjectArray global_clones);

typedef struct {
    void *prev;
    void *value;
} RefHolderNode;

void add_ref_to_list(RefHolderNode **list, void *ref);
void clean_list(RefHolderNode **holder, void *data, void (*release)(void *ref, void *data));
jobject create_global_ref(JNIEnv *env, jobject local_ref);
void release_global_refs(JNIEnv *env);

#ifdef __cplusplus
}
#endif
#endif