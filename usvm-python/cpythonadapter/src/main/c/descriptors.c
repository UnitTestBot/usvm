#include "descriptors.h"
#include "approximation_defs.h"

jobject
get_symbolic_descriptor(JNIEnv *env, jobject cpython_adapter, PyObject *concrete_descriptor) {
    jclass cpython_adapter_cls = (*env)->GetObjectClass(env, cpython_adapter);
    if (Py_TYPE(concrete_descriptor) == &PyMethodDescr_Type &&
        ((PyMethodDescrObject *) concrete_descriptor)->d_method->ml_meth == EXPORT_FOR_APPROXIMATION_LIST_APPEND) {
        jfieldID list_append_id = (*env)->GetFieldID(env, cpython_adapter_cls, "listAppendDescriptor", "Lorg/usvm/interpreter/MemberDescriptor;");
        return (*env)->GetObjectField(env, cpython_adapter, list_append_id);

    } else if (Py_TYPE(concrete_descriptor) == &PyMemberDescr_Type &&
        ((PyMemberDescrObject *) concrete_descriptor)->d_common.d_type == &PySlice_Type &&
        PyUnicode_CompareWithASCIIString(((PyMemberDescrObject *) concrete_descriptor)->d_common.d_name, "start") == 0) {
        jfieldID slice_start = (*env)->GetFieldID(env, cpython_adapter_cls, "sliceStartDescriptor", "Lorg/usvm/interpreter/MemberDescriptor;");
        return (*env)->GetObjectField(env, cpython_adapter, slice_start);
    }

    return 0;
}