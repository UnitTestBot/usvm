#include "converters.h"
#include "utils.h"

jlong frame_converter(ConcolicContext *ctx, PyFrameObject *value, int *fail) {
    return (jlong) value;
}

jobject object_converter(ConcolicContext *ctx, PyObject *value, int *fail) {
    if (!is_wrapped_java_object(value)) {
        //printf("FAILED TO CONVERT OBJECT OF TYPE %s\n", Py_TYPE(value)->tp_name);
        //fflush(stdout);
        *fail = 1;
        return 0;
    }
    //printf("Successfully converted\n");
    //fflush(stdout);
    return ((JavaPythonObject *) value)->reference;
}

jint int_converter(ConcolicContext *ctx, int value, int *fail) {
    return value;
}

jlong ref_converter(ConcolicContext *ctx, PyObject *ref, int *fail) {
    return (jlong) ref;
}

PyObject *object_wrapper(ConcolicContext *ctx, jobject value) {
    if (!value)
        return Py_None;
    return wrap_java_object(ctx->env, value);
}

jobjectArray array_converter(ConcolicContext *ctx, PyObject **elems, int *fail) {
    int n = 0;
    while (elems[n] != 0) n++;
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

jstring string_converter(ConcolicContext *ctx, const char *str, int *fail) {
    return (*ctx->env)->NewStringUTF(ctx->env, str);
}