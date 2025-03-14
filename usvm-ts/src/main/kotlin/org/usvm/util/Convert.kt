package org.usvm.util

import org.jacodb.ets.base.EtsAliasType
import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsArrayType
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsFunctionType
import org.jacodb.ets.base.EtsGenericType
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsLiteralType
import org.jacodb.ets.base.EtsNeverType
import org.jacodb.ets.base.EtsNullType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsRawType
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsTupleType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnclearRefType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnionType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.base.EtsVoidType
import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsModifiers
import org.jacodb.ets.model.EtsNamespace
import org.jacodb.ets.model.EtsNamespaceSignature
import org.jacodb.ets.model.EtsScene
import org.usvm.model.TsAnyType
import org.usvm.model.TsArrayType
import org.usvm.model.TsBlockCfg
import org.usvm.model.TsBooleanType
import org.usvm.model.TsClass
import org.usvm.model.TsClassImpl
import org.usvm.model.TsClassSignature
import org.usvm.model.TsClassType
import org.usvm.model.TsField
import org.usvm.model.TsFieldImpl
import org.usvm.model.TsFieldSignature
import org.usvm.model.TsFile
import org.usvm.model.TsFileSignature
import org.usvm.model.TsFunctionType
import org.usvm.model.TsGenericType
import org.usvm.model.TsLiteralType
import org.usvm.model.TsMethod
import org.usvm.model.TsMethodImpl
import org.usvm.model.TsMethodParameter
import org.usvm.model.TsMethodSignature
import org.usvm.model.TsModifiers
import org.usvm.model.TsNamespace
import org.usvm.model.TsNamespaceSignature
import org.usvm.model.TsNeverType
import org.usvm.model.TsNullType
import org.usvm.model.TsNumberType
import org.usvm.model.TsRawType
import org.usvm.model.TsScene
import org.usvm.model.TsStringType
import org.usvm.model.TsTupleType
import org.usvm.model.TsType
import org.usvm.model.TsUnclearType
import org.usvm.model.TsUndefinedType
import org.usvm.model.TsUnionType
import org.usvm.model.TsUnknownType
import org.usvm.model.TsVoidType

fun EtsScene.convert(): TsScene {
    val projectFiles = projectFiles.map { it.convert() }
    val sdkFiles = sdkFiles.map { it.convert() }
    return TsScene(projectFiles, sdkFiles)
}

fun EtsFile.convert(): TsFile {
    val fileSignature = signature.convert()
    val classes = classes.map { cls -> cls.convert(fileSignature, null) }
    val namespaces = namespaces.map { ns -> ns.convert(fileSignature, null) }
    return TsFile(
        signature = fileSignature,
        classes = classes,
        namespaces = namespaces,
    )
}

fun EtsNamespace.convert(
    fileSignature: TsFileSignature,
    parentNamespace: TsNamespaceSignature?,
): TsNamespace {
    val namespaceSignature = signature.convert(fileSignature, parentNamespace)
    val classes = classes.map { cls -> cls.convert(fileSignature, namespaceSignature) }
    val namespaces = namespaces.map { ns -> ns.convert(fileSignature, namespaceSignature) }
    return TsNamespace(
        signature = namespaceSignature,
        classes = classes,
        namespaces = namespaces,
    )
}

fun EtsClass.convert(
    fileSignature: TsFileSignature,
    namespaceSignature: TsNamespaceSignature?,
): TsClass {
    val classSignature = signature.convert(fileSignature, namespaceSignature)
    val fields = fields.map { field -> field.convert(classSignature) }
    val methods = methods.map { method -> method.convert(classSignature) }
    val typeParameters = typeParameters.map { it.convert() }
    return TsClassImpl(
        signature = classSignature,
        fields = fields,
        methods = methods,
        superClass = null, // TODO: superClass
        implementedInterfaces = emptyList(), // TODO: implementedInterfaces
        typeParameters = typeParameters,
        modifiers = modifiers.convert(),
        decorators = emptyList(), // TODO: decorators
    )
}

fun EtsField.convert(
    classSignature: TsClassSignature,
): TsField {
    val fieldSignature = signature.convert(classSignature)
    return TsFieldImpl(
        signature = fieldSignature,
        modifiers = (this as EtsFieldImpl).modifiers.convert(),
        isOptional = isOptional,
        isDefinitelyAssigned = isDefinitelyAssigned,
    )
}

fun EtsMethod.convert(
    classSignature: TsClassSignature,
): TsMethod {
    val methodSignature = signature.convert(classSignature)
    val typeParameters = typeParameters.map { it.convert() }
    return TsMethodImpl(
        signature = methodSignature,
        typeParameters = typeParameters,
        modifiers = modifiers.convert(),
        decorators = emptyList(), // TODO: decorators
    ).also { method ->
        method._cfg = cfg.convert(method)
    }
}

fun EtsMethodParameter.convert(): TsMethodParameter {
    return TsMethodParameter(
        index = index,
        name = name,
        type = type.convert(),
        isOptional = isOptional,
    )
}

fun EtsModifiers.convert(): TsModifiers {
    return TsModifiers(mask)
}

fun EtsFileSignature.convert(): TsFileSignature {
    return TsFileSignature(
        projectName = projectName,
        fileName = fileName,
    )
}

fun EtsNamespaceSignature.convert(
    fileSignature: TsFileSignature = file.convert(),
    parentNamespace: TsNamespaceSignature? = this@convert.namespace?.convert(),
): TsNamespaceSignature {
    return TsNamespaceSignature(
        name = name,
        file = fileSignature,
        namespace = parentNamespace,
    )
}

fun EtsClassSignature.convert(
    fileSignature: TsFileSignature = file.convert(),
    namespaceSignature: TsNamespaceSignature? = namespace?.convert(),
): TsClassSignature {
    return TsClassSignature(
        name = name,
        file = fileSignature,
        namespace = namespaceSignature,
    )
}

fun EtsFieldSignature.convert(
    enclosingClass: TsClassSignature = this.enclosingClass.convert(),
): TsFieldSignature {
    return TsFieldSignature(
        enclosingClass = enclosingClass,
        name = name,
        type = type.convert(),
    )
}

fun EtsMethodSignature.convert(
    enclosingClass: TsClassSignature = this.enclosingClass.convert(),
): TsMethodSignature {
    return TsMethodSignature(
        enclosingClass = enclosingClass,
        name = name,
        parameters = parameters.map { it.convert() },
        returnType = returnType.convert(),
    )
}

fun EtsType.convert(): TsType = accept(TypeConverter)

private object TypeConverter : EtsType.Visitor<TsType> {
    override fun visit(type: EtsRawType): TsType {
        return TsRawType(
            kind = type.kind,
            extra = type.extra,
        )
    }

    override fun visit(type: EtsAnyType): TsType {
        return TsAnyType
    }

    override fun visit(type: EtsUnknownType): TsType {
        return TsUnknownType
    }

    override fun visit(type: EtsUnionType): TsType {
        return TsUnionType(
            types = type.types.map { it.convert() },
        )
    }

    override fun visit(type: EtsTupleType): TsType {
        return TsTupleType(
            types = type.types.map { it.convert() },
        )
    }

    override fun visit(type: EtsBooleanType): TsType {
        return TsBooleanType
    }

    override fun visit(type: EtsNumberType): TsType {
        return TsNumberType
    }

    override fun visit(type: EtsStringType): TsType {
        return TsStringType
    }

    override fun visit(type: EtsNullType): TsType {
        return TsNullType
    }

    override fun visit(type: EtsUndefinedType): TsType {
        return TsUndefinedType
    }

    override fun visit(type: EtsVoidType): TsType {
        return TsVoidType
    }

    override fun visit(type: EtsNeverType): TsType {
        return TsNeverType
    }

    override fun visit(type: EtsLiteralType): TsType {
        return TsLiteralType(
            literalTypeName = type.literalTypeName
        )
    }

    override fun visit(type: EtsClassType): TsType {
        val typeParameters = type.typeParameters.map { it.convert() }
        return TsClassType(
            signature = type.signature.convert(),
            typeParameters = typeParameters,
        )
    }

    override fun visit(type: EtsFunctionType): TsType {
        val typeParameters = type.typeParameters.map { it.convert() }
        return TsFunctionType(
            signature = type.method.convert(),
            typeParameters = typeParameters,
        )
    }

    override fun visit(type: EtsArrayType): TsType {
        return TsArrayType(
            elementType = type.elementType.convert(),
            dimensions = type.dimensions,
        )
    }

    override fun visit(type: EtsUnclearRefType): TsType {
        val typeParameters = type.typeParameters.map { it.convert() }
        return TsUnclearType(
            typeName = type.name,
            typeParameters = typeParameters,
        )
    }

    override fun visit(type: EtsGenericType): TsType {
        return TsGenericType(
            typeName = type.name,
            // TODO: constraint
            // TODO: defaultType
        )
    }

    override fun visit(type: EtsAliasType): TsType {
        error("Unhandled ${type.javaClass.simpleName}: $type")
    }
}

fun EtsCfg.convert(method: TsMethod): TsBlockCfg {
    if (stmts.isEmpty()) {
        return TsBlockCfg(emptyList(), emptyMap())
    }
    
    // val starts: MutableSet<EtsStmt> = hashSetOf()
    //
    // for (stmt in stmts) {
    //     if (stmt is EtsIfStmt) {
    //         val (negStmt, posStmt) = successors(stmt).take(2)
    //         starts.add(negStmt)
    //         starts.add(posStmt)
    //     }
    // }
    //
    // val blocks: MutableList<List<EtsStmt>> = mutableListOf()
    // lateinit var currentBlock: MutableList<EtsStmt>
    //
    // var currentStmt = stmts[0]
    //
    // while(true) {
    //     if (currentStmt in starts) {
    //         val newBlock = currentBlock
    //         blocks.add(newBlock)
    //         currentBlock = mutableListOf(currentStmt)
    //     }
    // }

    TODO()
}
