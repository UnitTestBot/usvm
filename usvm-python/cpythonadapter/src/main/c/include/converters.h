#ifndef _Included_CPythonAdapter_converters
#define _Included_CPythonAdapter_converters
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "Python.h"
#include "utils.h"

jlong frame_converter(ConcolicContext *ctx, PyFrameObject *value, int *fail);
jobject object_converter(ConcolicContext *ctx, PyObject *value, int *fail);
jint int_converter(ConcolicContext *ctx, int value, int *fail);
jlong ref_converter(ConcolicContext *ctx, PyObject *ref, int *fail);
PyObject *ref_wrapper(ConcolicContext *ctx, jlong value);
PyObject *object_wrapper(ConcolicContext *ctx, jobject value);
jobjectArray array_converter(ConcolicContext *ctx, PyObject **elems, int *fail);
jobjectArray tuple_converter(ConcolicContext *ctx, PyObject *tuple, int *fail);
jstring string_converter(ConcolicContext *ctx, const char *str, int *fail);
jobject object_id_converter(ConcolicContext *ctx, jobject obj, int *fail);

#ifdef __cplusplus
}
#endif
#endif