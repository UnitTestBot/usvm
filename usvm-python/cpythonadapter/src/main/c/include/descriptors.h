#ifndef _Included_CPythonAdapter_descriptors
#define _Included_CPythonAdapter_descriptors
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include "Python.h"
#include "utils.h"

jobject get_symbolic_descriptor(JNIEnv *env, jobject cpython_adapter, PyObject *concrete_descriptor);

#ifdef __cplusplus
}
#endif
#endif