#include <jni.h>
#include "Python.h"

#ifndef _Included_CPythonAdapter_utils
#define _Included_CPythonAdapter_utils
#ifdef __cplusplus
extern "C" {
#endif

PyObject *handler(int signal_type, int signal_id, int nargs, PyObject *const *args, void *param);

#ifdef __cplusplus
}
#endif
#endif