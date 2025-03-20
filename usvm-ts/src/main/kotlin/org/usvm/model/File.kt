package org.usvm.model

class TsFile(
    val signature: TsFileSignature,
    val classes: List<TsClass>,
    val namespaces: List<TsNamespace>,
) {
    init {
        classes.forEach { (it as TsClassImpl).declaringFile = this }
        namespaces.forEach { it.declaringFile = this }
    }

    var scene: TsScene? = null

    val name: String
        get() = signature.fileName
    val projectName: String
        get() = signature.projectName

    val allClasses: List<TsClass> by lazy {
        classes + namespaces.flatMap { it.allClasses }
    }

    override fun toString(): String {
        return signature.toString()
    }
}
