package org.usvm.model

class TsNamespace(
    val signature: TsNamespaceSignature,
    val classes: List<TsClass>,
    val namespaces: List<TsNamespace>,
) {
    val allClasses: List<TsClass>
        get() = classes + namespaces.flatMap { it.allClasses }
}
