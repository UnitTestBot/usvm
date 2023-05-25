#include <jni.h>
#include "Python.h"
#include "SYMBOLIC_API.h"

#ifndef _Included_CPythonAdapter_symbolic_handler
#define _Included_CPythonAdapter_symbolic_handler
#ifdef __cplusplus
extern "C" {
#endif

PyObject *handler(int signal_type, int signal_id, int nargs, PyObject *const *args, void *param);

#ifdef __cplusplus
}
#endif
#endif