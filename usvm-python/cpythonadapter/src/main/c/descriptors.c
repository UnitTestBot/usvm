#include "descriptors.h"
#include "approximation_defs.h"

#include "MethodDescriptors.h"  // generated
#include "MemberDescriptors.h"  // generated

jobject
get_symbolic_descriptor(JNIEnv *env, jobject cpython_adapter, PyObject *concrete_descriptor) {
    jclass cpython_adapter_cls = (*env)->GetObjectClass(env, cpython_adapter);

    if (PyFunction_Check(concrete_descriptor)) {
        jfieldID field_id = (*env)->GetFieldID(env, cpython_adapter_cls, "pythonMethodDescriptor", "Lorg/usvm/interpreter/MemberDescriptor;");
        return (*env)->GetObjectField(env, cpython_adapter, field_id);
    }

    METHOD_DESCRIPTORS
    MEMBER_DESCRIPTORS
    return 0;
}