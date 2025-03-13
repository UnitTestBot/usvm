package org.usvm.util

import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsScene
import org.usvm.model.TsBlockCfg
import org.usvm.model.TsClass
import org.usvm.model.TsClassImpl
import org.usvm.model.TsClassSignature
import org.usvm.model.TsField
import org.usvm.model.TsFieldImpl
import org.usvm.model.TsFieldSignature
import org.usvm.model.TsFile
import org.usvm.model.TsFileSignature
import org.usvm.model.TsMethod
import org.usvm.model.TsMethodImpl
import org.usvm.model.TsMethodParameter
import org.usvm.model.TsMethodSignature
import org.usvm.model.TsModifiers
import org.usvm.model.TsScene
import org.usvm.model.TsUnknownType

fun EtsScene.convert(): TsScene {
    val projectFiles = projectFiles.map { it.convert() }
    val sdkFiles = sdkFiles.map { it.convert() }
    return TsScene(projectFiles, sdkFiles)
}

fun EtsFile.convert(): TsFile {
    val fileSignature = TsFileSignature(projectName, name)
    val classes = classes.map { cls -> cls.convert(fileSignature) }
    return TsFile(
        signature = fileSignature,
        classes = classes,
        namespaces = emptyList(), // TODO: namespaces
    )
}

fun EtsClass.convert(fileSignature: TsFileSignature): TsClass {
    val classSignature = TsClassSignature(
        name = name,
        file = fileSignature,
        namespace = null, // TODO: namespace
    )
    val fields = fields.map { field -> field.convert(classSignature) }
    val methods = methods.map { method -> method.convert(classSignature) }
    return TsClassImpl(
        signature = classSignature,
        fields = fields,
        methods = methods,
        superClass = null, // TODO: superClass
        implementedInterfaces = emptyList(), // TODO: implementedInterfaces
        typeParameters = emptyList(), // TODO: typeParameters
        modifiers = TsModifiers(modifiers.mask),
        decorators = emptyList(), // TODO: decorators
    )
}

fun EtsField.convert(classSignature: TsClassSignature): TsField {
    val fieldSignature = TsFieldSignature(
        enclosingClass = classSignature,
        name = name,
        type = TsUnknownType, // TODO: type
    )
    return TsFieldImpl(
        signature = fieldSignature,
        modifiers = TsModifiers((this as EtsFieldImpl).modifiers.mask),
        isOptional = this.isOptional,
        isDefinitelyAssigned = this.isDefinitelyAssigned,
    )
}

fun EtsMethod.convert(classSignature: TsClassSignature): TsMethod {
    val methodSignature = TsMethodSignature(
        enclosingClass = classSignature,
        name = name,
        parameters = parameters.map { it.convert() },
        returnType = TsUnknownType, // TODO: type
    )
    return TsMethodImpl(
        signature = methodSignature,
        typeParameters = emptyList(), // TODO: typeParameters
        modifiers = TsModifiers(modifiers.mask),
        decorators = emptyList(), // TODO: decorators
    ).also { method ->
        method._cfg = cfg.convert()
    }
}

fun EtsMethodParameter.convert(): TsMethodParameter {
    return TsMethodParameter(
        index = index,
        name = name,
        type = TsUnknownType, // TODO: type
        isOptional = isOptional,
    )
}

fun EtsCfg.convert(): TsBlockCfg {
    TODO()
}
