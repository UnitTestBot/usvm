package org.usvm.annotations.codegeneration

data class MemberDescriptorInfo(
    val nativeTypeName: String,
    val nativeMemberName: String,
    val javaMemberName: String
)

fun generateDescriptorCheck(info: MemberDescriptorInfo): String =
    """
        if (Py_TYPE(concrete_descriptor) == &PyMemberDescr_Type &&
        ((PyMemberDescrObject *) concrete_descriptor)->d_common.d_type == &${info.nativeTypeName} &&
        PyUnicode_CompareWithASCIIString(((PyMemberDescrObject *) concrete_descriptor)->d_common.d_name, "${info.nativeMemberName}") == 0) {
            jfieldID field_id = (*env)->GetFieldID(env, cpython_adapter_cls, "${info.javaMemberName}", "$memberDescriptionQualifiedName");
            return (*env)->GetObjectField(env, cpython_adapter, field_id);
        }
    """.trimIndent()

fun generateDescriptorChecks(info: List<MemberDescriptorInfo>): String =
    "#define MEMBER_DESCRIPTORS \\\n" +
    info.joinToString("\n", transform = ::generateDescriptorCheck).replace("\n", "\\\n")

fun generateMethodDescriptorCheck(info: MemberDescriptorInfo): String =
    """
        if (Py_TYPE(concrete_descriptor) == &PyMethodDescr_Type &&
        ((PyMethodDescrObject *) concrete_descriptor)->d_common.d_type == &${info.nativeTypeName} &&
        PyUnicode_CompareWithASCIIString(((PyMethodDescrObject *) concrete_descriptor)->d_common.d_name, "${info.nativeMemberName}") == 0) {
            jfieldID field_id = (*env)->GetFieldID(env, cpython_adapter_cls, "${info.javaMemberName}", "$memberDescriptionQualifiedName");
            return (*env)->GetObjectField(env, cpython_adapter, field_id);
        }
    """.trimIndent()


fun generateMethodDescriptorChecks(info: List<MemberDescriptorInfo>): String =
    "#define METHOD_DESCRIPTORS \\\n" +
            info.joinToString("\n", transform = ::generateMethodDescriptorCheck).replace("\n", "\\\n")