#include "descriptors.h"
#include "approximation_defs.h"

#include "MethodDescriptors.h"  // generated
#include "MemberDescriptors.h"  // generated

jobject
get_symbolic_descriptor(JNIEnv *env, jobject cpython_adapter, PyObject *concrete_descriptor) {
    jclass cpython_adapter_cls = (*env)->GetObjectClass(env, cpython_adapter);
    METHOD_DESCRIPTORS
    MEMBER_DESCRIPTORS
    return 0;
}