package org.usvm.instrumentation.jacodb.util


import org.jacodb.api.JcClassOrInterface
import java.nio.file.Path
import java.nio.file.Paths

class ClassWriter(val ctx: ExecutionContext, val target: Path) {

    fun visit(klass: JcClassOrInterface) {
//        val classFileName = target.resolve(Paths.get(klass.pkg.fileSystemPath, "${klass.name}.class")).toAbsolutePath()
//        tryOrNull {
//            klass.write(cm, ctx.loader, classFileName)
//        } ?: log.warn("Could not write class $klass")
    }
}
