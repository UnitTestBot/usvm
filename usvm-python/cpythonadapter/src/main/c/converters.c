#include "converters.h"
#include "utils.h"

jlong
frame_converter(ConcolicContext *ctx, PyFrameObject *value, int *fail) {
    return (jlong) value;
}

jobject
object_converter(ConcolicContext *ctx, PyObject *value, int *fail) {
    if (!is_wrapped_java_object(value)) {
        *fail = 1;
        return 0;
    }
    return ((JavaPythonObject *) value)->reference;
}

jint
int_converter(ConcolicContext *ctx, int value, int *fail) {
    return value;
}

jlong
ref_converter(ConcolicContext *ctx, PyObject *ref, int *fail) {
    return (jlong) ref;
}

PyObject *
ref_wrapper(ConcolicContext *ctx, jlong value) {
    return (PyObject *) value;
}

PyObject *
object_wrapper(ConcolicContext *ctx, jobject value) {
    if (!value)
        return Py_None;
    return wrap_java_object(ctx->env, value);
}

static jobjectArray
convert_array(ConcolicContext *ctx, int n, PyObject **elems, int *fail) {
    jobjectArray symbol_array = (*ctx->env)->NewObjectArray(ctx->env, n, ctx->symbol_cls, 0);
    for (int i = 0; i < n; i++) {
        if (!is_wrapped_java_object(elems[i])) {
            *fail = 1;
            return 0;
        }

        jobject elem = ((JavaPythonObject *) elems[i])->reference;
        (*ctx->env)->SetObjectArrayElement(ctx->env, symbol_array, i, elem);
    }
    return symbol_array;
}

jobjectArray
array_converter(ConcolicContext *ctx, PyObject **elems, int *fail) {
    int n = 0;
    while (elems[n] != 0) n++;
    return convert_array(ctx, n, elems, fail);
}

jobjectArray
tuple_converter(ConcolicContext *ctx, PyObject *tuple, int *fail) {
    assert(PyTuple_Check(tuple));
    int n = PyTuple_GET_SIZE(tuple);
    return convert_array(ctx, n, ((PyTupleObject *) tuple)->ob_item, fail);
}

jstring
string_converter(ConcolicContext *ctx, const char *str, int *fail) {
    return (*ctx->env)->NewStringUTF(ctx->env, str);
}

jobject
object_id_converter(ConcolicContext *ctx, jobject obj, int *fail) {
    return obj;
}