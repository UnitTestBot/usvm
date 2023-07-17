#ifndef _Included_CPythonAdapter_virtual_objects
#define _Included_CPythonAdapter_virtual_objects
#ifdef __cplusplus
extern "C" {
#endif

#include "Python.h"
#include "utils.h"

#define VirtualObjectTypeName "ibmviqhlye.___virtual_object___ibmviqhlye"

typedef struct {
    PyObject_HEAD
    ConcolicContext *ctx;
    jobject reference;
    jobject object;
} VirtualPythonObject;

PyObject *create_new_virtual_object(ConcolicContext *ctx, jobject object);

#ifdef __cplusplus
}
#endif
#endif