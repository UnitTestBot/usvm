package org.usvm.util

import mu.KotlinLogging
import org.jacodb.ets.base.EtsAddExpr
import org.jacodb.ets.base.EtsAliasType
import org.jacodb.ets.base.EtsAndExpr
import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsArrayType
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsAwaitExpr
import org.jacodb.ets.base.EtsBitAndExpr
import org.jacodb.ets.base.EtsBitNotExpr
import org.jacodb.ets.base.EtsBitOrExpr
import org.jacodb.ets.base.EtsBitXorExpr
import org.jacodb.ets.base.EtsBooleanConstant
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsCommaExpr
import org.jacodb.ets.base.EtsDeleteExpr
import org.jacodb.ets.base.EtsDivExpr
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsEqExpr
import org.jacodb.ets.base.EtsExpExpr
import org.jacodb.ets.base.EtsFunctionType
import org.jacodb.ets.base.EtsGenericType
import org.jacodb.ets.base.EtsGotoStmt
import org.jacodb.ets.base.EtsGtEqExpr
import org.jacodb.ets.base.EtsGtExpr
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsInExpr
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsInstanceOfExpr
import org.jacodb.ets.base.EtsLeftShiftExpr
import org.jacodb.ets.base.EtsLengthExpr
import org.jacodb.ets.base.EtsLiteralType
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsLtEqExpr
import org.jacodb.ets.base.EtsLtExpr
import org.jacodb.ets.base.EtsMulExpr
import org.jacodb.ets.base.EtsNegExpr
import org.jacodb.ets.base.EtsNeverType
import org.jacodb.ets.base.EtsNewArrayExpr
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsNotEqExpr
import org.jacodb.ets.base.EtsNotExpr
import org.jacodb.ets.base.EtsNullConstant
import org.jacodb.ets.base.EtsNullType
import org.jacodb.ets.base.EtsNullishCoalescingExpr
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsOrExpr
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsPostDecExpr
import org.jacodb.ets.base.EtsPostIncExpr
import org.jacodb.ets.base.EtsPreDecExpr
import org.jacodb.ets.base.EtsPreIncExpr
import org.jacodb.ets.base.EtsPtrCallExpr
import org.jacodb.ets.base.EtsRawEntity
import org.jacodb.ets.base.EtsRawType
import org.jacodb.ets.base.EtsRemExpr
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsRightShiftExpr
import org.jacodb.ets.base.EtsStaticCallExpr
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStrictEqExpr
import org.jacodb.ets.base.EtsStrictNotEqExpr
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsSubExpr
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsTernaryExpr
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsThrowStmt
import org.jacodb.ets.base.EtsTupleType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsTypeOfExpr
import org.jacodb.ets.base.EtsUnaryPlusExpr
import org.jacodb.ets.base.EtsUnclearRefType
import org.jacodb.ets.base.EtsUndefinedConstant
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnionType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.base.EtsUnsignedRightShiftExpr
import org.jacodb.ets.base.EtsVoidExpr
import org.jacodb.ets.base.EtsVoidType
import org.jacodb.ets.base.EtsYieldExpr
import org.jacodb.ets.graph.EtsBasicBlock
import org.jacodb.ets.graph.EtsBlockCfg
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
import org.usvm.model.TsAddExpr
import org.usvm.model.TsAndExpr
import org.usvm.model.TsAnyType
import org.usvm.model.TsArrayAccess
import org.usvm.model.TsArrayType
import org.usvm.model.TsAssignStmt
import org.usvm.model.TsAwaitExpr
import org.usvm.model.TsBasicBlock
import org.usvm.model.TsBitAndExpr
import org.usvm.model.TsBitNotExpr
import org.usvm.model.TsBitOrExpr
import org.usvm.model.TsBitXorExpr
import org.usvm.model.TsBlockCfg
import org.usvm.model.TsBooleanConstant
import org.usvm.model.TsBooleanType
import org.usvm.model.TsCallExpr
import org.usvm.model.TsCallStmt
import org.usvm.model.TsCastExpr
import org.usvm.model.TsClass
import org.usvm.model.TsClassImpl
import org.usvm.model.TsClassSignature
import org.usvm.model.TsClassType
import org.usvm.model.TsDeleteExpr
import org.usvm.model.TsDivExpr
import org.usvm.model.TsEntity
import org.usvm.model.TsEqExpr
import org.usvm.model.TsExpExpr
import org.usvm.model.TsField
import org.usvm.model.TsFieldImpl
import org.usvm.model.TsFieldSignature
import org.usvm.model.TsFile
import org.usvm.model.TsFileSignature
import org.usvm.model.TsFunctionType
import org.usvm.model.TsGenericType
import org.usvm.model.TsGtEqExpr
import org.usvm.model.TsGtExpr
import org.usvm.model.TsIfStmt
import org.usvm.model.TsInExpr
import org.usvm.model.TsInstLocation
import org.usvm.model.TsInstanceCallExpr
import org.usvm.model.TsInstanceFieldRef
import org.usvm.model.TsInstanceOfExpr
import org.usvm.model.TsLValue
import org.usvm.model.TsLeftShiftExpr
import org.usvm.model.TsLiteralType
import org.usvm.model.TsLocal
import org.usvm.model.TsLtEqExpr
import org.usvm.model.TsLtExpr
import org.usvm.model.TsMethod
import org.usvm.model.TsMethodImpl
import org.usvm.model.TsMethodParameter
import org.usvm.model.TsMethodSignature
import org.usvm.model.TsModifiers
import org.usvm.model.TsMulExpr
import org.usvm.model.TsNamespace
import org.usvm.model.TsNamespaceSignature
import org.usvm.model.TsNegExpr
import org.usvm.model.TsNeverType
import org.usvm.model.TsNewArrayExpr
import org.usvm.model.TsNewExpr
import org.usvm.model.TsNopStmt
import org.usvm.model.TsNotEqExpr
import org.usvm.model.TsNotExpr
import org.usvm.model.TsNullConstant
import org.usvm.model.TsNullType
import org.usvm.model.TsNumberConstant
import org.usvm.model.TsNumberType
import org.usvm.model.TsOrExpr
import org.usvm.model.TsParameterRef
import org.usvm.model.TsPostDecExpr
import org.usvm.model.TsPostIncExpr
import org.usvm.model.TsPreDecExpr
import org.usvm.model.TsPreIncExpr
import org.usvm.model.TsPtrCallExpr
import org.usvm.model.TsRawEntity
import org.usvm.model.TsRawType
import org.usvm.model.TsRemExpr
import org.usvm.model.TsReturnStmt
import org.usvm.model.TsRightShiftExpr
import org.usvm.model.TsScene
import org.usvm.model.TsStaticCallExpr
import org.usvm.model.TsStaticFieldRef
import org.usvm.model.TsStmt
import org.usvm.model.TsStrictEqExpr
import org.usvm.model.TsStrictNotEqExpr
import org.usvm.model.TsStringConstant
import org.usvm.model.TsStringType
import org.usvm.model.TsSubExpr
import org.usvm.model.TsThis
import org.usvm.model.TsTupleType
import org.usvm.model.TsType
import org.usvm.model.TsTypeOfExpr
import org.usvm.model.TsUnaryPlusExpr
import org.usvm.model.TsUnclearType
import org.usvm.model.TsUndefinedConstant
import org.usvm.model.TsUndefinedType
import org.usvm.model.TsUnionType
import org.usvm.model.TsUnknownType
import org.usvm.model.TsUnsignedRightShiftExpr
import org.usvm.model.TsValue
import org.usvm.model.TsVoidExpr
import org.usvm.model.TsVoidType
import org.usvm.model.TsYieldExpr
import org.usvm.model.TypeNameImpl
import java.util.IdentityHashMap

private val logger = KotlinLogging.logger {}

fun EtsScene.convert(): TsScene {
    val projectFiles = projectFiles.map { it.convert() }
    val sdkFiles = sdkFiles.map { it.convert() }
    return TsScene(projectFiles, sdkFiles, this)
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
    val localType = locals.associate { convertLocal(it) to it.type.convert() }
    return TsMethodImpl(
        signature = methodSignature,
        typeParameters = typeParameters,
        modifiers = modifiers.convert(),
        decorators = emptyList(), // TODO: decorators
        localType = localType,
        etsMethod = this,
    ).also { method ->
        method._cfg = cfg.convert(this, method)
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

fun EtsCfg.convert(etsMethod: EtsMethod, method: TsMethod): TsBlockCfg {
    logger.info("Converting CFG for method $method")
    val blockCfg = toBlockCfg(etsMethod, method)
    return blockCfg.convert(method)
}

fun EtsCfg.toBlockCfg(etsMethod: EtsMethod, tsMethod: TsMethod): EtsBlockCfg {
    if (stmts.isEmpty()) {
        return EtsBlockCfg(emptyList(), emptyMap())
    }

    if (stmts.any { it is EtsIfStmt && successors(it).size != 2 }) {
        val badStmt = stmts.first { it is EtsIfStmt && successors(it).size != 2 }
        logger.warn {
            "Not all if statements have two successors in method '$etsMethod'. For example '$badStmt' has ${
                successors(badStmt).size
            } successors: ${
                successors(badStmt)
            }"
        }
        return EtsBlockCfg(emptyList(), emptyMap())
    }

    val pivots: MutableSet<EtsStmt> = hashSetOf()

    for (stmt in stmts) {
        if (stmt is EtsIfStmt) {
            val (negStmt, posStmt) = successors(stmt).take(2)
            pivots.add(negStmt)
            pivots.add(posStmt)
        } else if (predecessors(stmt).size > 1) {
            pivots.add(stmt)
        }
    }

    lateinit var currentBlock: MutableList<EtsStmt>
    val blocks: MutableList<EtsBasicBlock> = mutableListOf()
    val stmtToBlock: MutableMap<EtsStmt, Int> = IdentityHashMap()

    fun newBlock(): EtsBasicBlock {
        currentBlock = mutableListOf()
        val block = EtsBasicBlock(blocks.size, currentBlock)
        blocks += block
        return block
    }

    val queue = ArrayDeque<EtsStmt>(listOf(stmts[0]))
    val visited: MutableSet<EtsStmt> = hashSetOf()
    var block = newBlock()

    while (queue.isNotEmpty()) {
        val stmt = queue.removeFirst()
        if (visited.add(stmt)) {
            if (stmt in pivots) {
                logger.info("pivot at ${stmt.location.index}: $stmt")
                block.statements.forEach { stmtToBlock[it] = block.id }
                block = newBlock()
            }
            currentBlock += stmt
            for (s in successors(stmt).reversed()) {
                queue.addFirst(s)
            }
        }
    }

    block.statements.forEach { stmtToBlock[it] = block.id }

    val successors = blocks.associate { block ->
        val last = block.statements.last()
        block.id to this.successors(last).map { stmtToBlock[it]!! }
    }

    for (block in blocks) {
        logger.info("BLOCK ${block.id} with successors ${successors[block.id]} has ${block.statements.size} statements:")
        for (stmt in block.statements) {
            logger.info("  ${stmt.location.index}: $stmt")
        }
    }

    return EtsBlockCfg(blocks, successors)
}

fun EtsBlockCfg.convert(method: TsMethod): TsBlockCfg {
    val blocks = this.blocks.map { it.convert(method) }
    return TsBlockCfg(blocks, successors)
}

fun EtsBasicBlock.convert(method: TsMethod): TsBasicBlock {
    val stmts: MutableList<TsStmt> = mutableListOf()
    val stub = TsInstLocation(method, -1)

    var freeTempLocal = 0
    fun newTempLocal(): TsLocal {
        return TsLocal("_temp${freeTempLocal++}")
    }

    fun ensureLocal(entity: TsEntity): TsLocal {
        if (entity is TsLocal) {
            return entity
        }
        val newLocal = newTempLocal()
        stmts += TsAssignStmt(stub, newLocal, entity)
        return newLocal
    }

    fun EtsEntity.convert(): TsEntity = accept(object : EtsEntity.Visitor<TsEntity> {
        override fun visit(value: EtsRawEntity): TsEntity {
            return TsRawEntity(value.kind, value.extra)
        }

        override fun visit(value: EtsLocal): TsEntity {
            return convertLocal(value)
        }

        override fun visit(value: EtsStringConstant): TsEntity {
            return TsStringConstant(value.value)
        }

        override fun visit(value: EtsBooleanConstant): TsEntity {
            return TsBooleanConstant(value.value)
        }

        override fun visit(value: EtsNumberConstant): TsEntity {
            return TsNumberConstant(value.value)
        }

        override fun visit(value: EtsNullConstant): TsEntity {
            return TsNullConstant
        }

        override fun visit(value: EtsUndefinedConstant): TsEntity {
            return TsUndefinedConstant
        }

        override fun visit(value: EtsThis): TsEntity {
            return TsThis
        }

        override fun visit(value: EtsParameterRef): TsEntity {
            return TsParameterRef(value.index)
        }

        override fun visit(value: EtsArrayAccess): TsEntity {
            val array = convertLocal(value.array as EtsLocal)
            val index = value.index.convert() as TsValue
            return TsArrayAccess(array, index)
        }

        override fun visit(value: EtsInstanceFieldRef): TsEntity {
            val instance = convertLocal(value.instance)
            val fieldName = value.field.name
            return TsInstanceFieldRef(instance, fieldName)
        }

        override fun visit(value: EtsStaticFieldRef): TsEntity {
            val enclosingClass = TypeNameImpl(value.field.enclosingClass.name)
            val fieldName = value.field.name
            return TsStaticFieldRef(enclosingClass, fieldName)
        }

        override fun visit(expr: EtsNewExpr): TsEntity {
            val type = expr.type.convert()
            return TsNewExpr(type)
        }

        override fun visit(expr: EtsNewArrayExpr): TsEntity {
            val elementType = expr.elementType.convert()
            val size = expr.size.convert()
            return TsNewArrayExpr(elementType, size)
        }

        override fun visit(expr: EtsLengthExpr): TsEntity {
            error("No")
        }

        override fun visit(expr: EtsCastExpr): TsEntity {
            val arg = expr.arg.convert()
            val type = expr.type.convert()
            return TsCastExpr(arg, type)
        }

        override fun visit(expr: EtsInstanceOfExpr): TsEntity {
            val arg = expr.arg.convert()
            val checkType = expr.checkType.convert()
            return TsInstanceOfExpr(arg, checkType)
        }

        override fun visit(expr: EtsDeleteExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsDeleteExpr(arg)
        }

        override fun visit(expr: EtsAwaitExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsAwaitExpr(arg)
        }

        override fun visit(expr: EtsYieldExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsYieldExpr(arg)
        }

        override fun visit(expr: EtsTypeOfExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsTypeOfExpr(arg)
        }

        override fun visit(expr: EtsVoidExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsVoidExpr(arg)
        }

        override fun visit(expr: EtsNotExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsNotExpr(arg)
        }

        override fun visit(expr: EtsBitNotExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsBitNotExpr(arg)
        }

        override fun visit(expr: EtsNegExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsNegExpr(arg)
        }

        override fun visit(expr: EtsUnaryPlusExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsUnaryPlusExpr(arg)
        }

        override fun visit(expr: EtsPreIncExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsPreIncExpr(arg)
        }

        override fun visit(expr: EtsPreDecExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsPreDecExpr(arg)
        }

        override fun visit(expr: EtsPostIncExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsPostIncExpr(arg)
        }

        override fun visit(expr: EtsPostDecExpr): TsEntity {
            val arg = expr.arg.convert()
            return TsPostDecExpr(arg)
        }

        override fun visit(expr: EtsEqExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsEqExpr(left, right)
        }

        override fun visit(expr: EtsNotEqExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsNotEqExpr(left, right)
        }

        override fun visit(expr: EtsStrictEqExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsStrictEqExpr(left, right)
        }

        override fun visit(expr: EtsStrictNotEqExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsStrictNotEqExpr(left, right)
        }

        override fun visit(expr: EtsLtExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsLtExpr(left, right)
        }

        override fun visit(expr: EtsLtEqExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsLtEqExpr(left, right)
        }

        override fun visit(expr: EtsGtExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsGtExpr(left, right)
        }

        override fun visit(expr: EtsGtEqExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsGtEqExpr(left, right)
        }

        override fun visit(expr: EtsInExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsInExpr(left, right)
        }

        override fun visit(expr: EtsAddExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsAddExpr(left, right)
        }

        override fun visit(expr: EtsSubExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsSubExpr(left, right)
        }

        override fun visit(expr: EtsMulExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsMulExpr(left, right)
        }

        override fun visit(expr: EtsDivExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsDivExpr(left, right)
        }

        override fun visit(expr: EtsRemExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsRemExpr(left, right)
        }

        override fun visit(expr: EtsExpExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsExpExpr(left, right)
        }

        override fun visit(expr: EtsBitAndExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsBitAndExpr(left, right)
        }

        override fun visit(expr: EtsBitOrExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsBitOrExpr(left, right)
        }

        override fun visit(expr: EtsBitXorExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsBitXorExpr(left, right)
        }

        override fun visit(expr: EtsLeftShiftExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsLeftShiftExpr(left, right)
        }

        override fun visit(expr: EtsRightShiftExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsRightShiftExpr(left, right)
        }

        override fun visit(expr: EtsUnsignedRightShiftExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsUnsignedRightShiftExpr(left, right)
        }

        override fun visit(expr: EtsAndExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsAndExpr(left, right)
        }

        override fun visit(expr: EtsOrExpr): TsEntity {
            val left = expr.left.convert()
            val right = expr.right.convert()
            return TsOrExpr(left, right)
        }

        override fun visit(expr: EtsNullishCoalescingExpr): TsEntity {
            error("No")
        }

        override fun visit(expr: EtsInstanceCallExpr): TsEntity {
            val instance = convertLocal(expr.instance)
            val method = expr.method.convert()
            val args = expr.args.map { ensureLocal(it.convert()) }
            return TsInstanceCallExpr(instance, method, args)
        }

        override fun visit(expr: EtsStaticCallExpr): TsEntity {
            val method = expr.method.convert()
            val args = expr.args.map { ensureLocal(it.convert()) }
            return TsStaticCallExpr(method, args)
        }

        override fun visit(expr: EtsPtrCallExpr): TsEntity {
            val ptr = convertLocal(expr.ptr)
            val method = expr.method.convert()
            val args = expr.args.map { ensureLocal(it.convert()) }
            return TsPtrCallExpr(ptr, method, args)
        }

        override fun visit(expr: EtsCommaExpr): TsEntity {
            error("No")
        }

        override fun visit(expr: EtsTernaryExpr): TsEntity {
            error("No")
        }
    })

    fun handle(stmt: EtsStmt) {
        stmt.accept(object : EtsStmt.Visitor<Unit> {
            override fun visit(stmt: EtsNopStmt) {
                stmts += TsNopStmt(stub)
            }

            override fun visit(stmt: EtsAssignStmt) {
                val lhv = stmt.lhv.convert() as TsLValue
                val rhv = stmt.rhv.convert()
                stmts += TsAssignStmt(stub, lhv, rhv)
            }

            override fun visit(stmt: EtsCallStmt) {
                val expr = stmt.expr.convert() as TsCallExpr
                stmts += TsCallStmt(stub, expr)
            }

            override fun visit(stmt: EtsReturnStmt) {
                val returnValue = stmt.returnValue?.convert()?.let { ensureLocal(it) }
                stmts += TsReturnStmt(stub, returnValue)
            }

            override fun visit(stmt: EtsThrowStmt) {
                TODO("Not yet implemented")
            }

            override fun visit(stmt: EtsGotoStmt) {
                error("No")
            }

            override fun visit(stmt: EtsIfStmt) {
                val condition = ensureLocal(stmt.condition.convert())
                stmts += TsIfStmt(stub, condition)
            }

            override fun visit(stmt: EtsSwitchStmt) {
                error("No")
            }
        })
    }

    this.statements.forEach { handle(it) }

    return TsBasicBlock(id, stmts)
}

fun convertLocal(local: EtsLocal): TsLocal {
    return TsLocal(
        name = local.name,
    )
}
