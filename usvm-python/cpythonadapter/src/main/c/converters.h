#ifndef _Included_CPythonAdapter_converters
#define _Included_CPythonAdapter_converters
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "Python.h"
#include "utils.h"

jint frame_converter(ConcolicContext *ctx, PyFrameObject *value, int *fail);
jobject object_converter(ConcolicContext *ctx, PyObject *value, int *fail);
jint int_converter(ConcolicContext *ctx, int value, int *fail);
jlong ref_converter(ConcolicContext *ctx, PyObject *ref, int *fail);
PyObject *object_wrapper(ConcolicContext *ctx, jobject value);
jobjectArray array_converter(ConcolicContext *ctx, PyObject **elems, int *fail);

#ifdef __cplusplus
}
#endif
#endif