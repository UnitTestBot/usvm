#ifndef _Included_CPythonAdapter_virtual_objects
#define _Included_CPythonAdapter_virtual_objects
#ifdef __cplusplus
extern "C" {
#endif

#include "Python.h"
#include "utils.h"
#include "symbolicadapter.h"

#define VirtualObjectTypeName "ibmviqhlye.___virtual_object___ibmviqhlye"

typedef struct {
    PyObject_HEAD
    ConcolicContext *ctx;
    jobject reference;
    SymbolicAdapter *adapter;
} VirtualPythonObject;

void initialize_virtual_object_type();
PyObject *allocate_raw_virtual_object(JNIEnv *env, jobject object);
void finish_virtual_object_initialization(VirtualPythonObject *object, ConcolicContext *ctx, SymbolicAdapter *adapter);
PyObject *create_new_virtual_object(ConcolicContext *ctx, jobject object, SymbolicAdapter *adapter);
int is_virtual_object(PyObject *obj);
void register_virtual_methods();

#ifdef __cplusplus
}
#endif
#endif