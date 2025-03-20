package org.usvm.model

class TsNamespace(
    val signature: TsNamespaceSignature,
    val classes: List<TsClass>,
    val namespaces: List<TsNamespace>,
) {
    init {
        classes.forEach { (it as TsClassImpl).declaringNamespace = this }
        namespaces.forEach { it.declaringNamespace = this }
    }

    var declaringFile: TsFile? = null
    var declaringNamespace: TsNamespace? = null

    val allClasses: List<TsClass>
        get() = classes + namespaces.flatMap { it.allClasses }
}
