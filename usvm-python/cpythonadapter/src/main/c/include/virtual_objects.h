#ifndef _Included_CPythonAdapter_virtual_objects
#define _Included_CPythonAdapter_virtual_objects
#ifdef __cplusplus
extern "C" {
#endif

#include "limits.h"
#include "Python.h"
#include "utils.h"
#include "symbolicadapter.h"
#include "AvailableSlots.h" // generated

#define VirtualObjectTypeName "ibmviqhlye.___virtual_object___ibmviqhlye"

typedef struct {
    PyObject_HEAD
    ConcolicContext *ctx;
    jobject reference;
    SymbolicAdapter *adapter;
} VirtualPythonObject;

void initialize_virtual_object_available_slots();
void deinitialize_virtual_object_available_slots();
void initialize_virtual_object_ready_types();
void deinitialize_virtual_object_ready_types();
PyObject *_allocate_raw_virtual_object(JNIEnv *env, jobject object, const unsigned char *mask, size_t length);
PyObject *allocate_raw_virtual_object_with_all_slots(JNIEnv *env, jobject object);
PyObject *allocate_raw_virtual_object(JNIEnv *env, jobject object, jbyteArray mask);
void finish_virtual_object_initialization(VirtualPythonObject *object, ConcolicContext *ctx, SymbolicAdapter *adapter);
PyObject *create_new_virtual_object(ConcolicContext *ctx, jobject object, SymbolicAdapter *adapter);
int is_virtual_object(PyObject *obj);
void register_virtual_methods(SymbolicAdapter *adapter);

#ifdef __cplusplus
}
#endif
#endif