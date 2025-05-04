package org.usvm.service

import manager.BinaryOperator
import manager.RelationOperator
import manager.UnaryOperator
import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBinaryExpr
import org.jacodb.ets.model.EtsBitAndExpr
import org.jacodb.ets.model.EtsBitNotExpr
import org.jacodb.ets.model.EtsBitOrExpr
import org.jacodb.ets.model.EtsBitXorExpr
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsConstant
import org.jacodb.ets.model.EtsDeleteExpr
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
import org.jacodb.ets.model.EtsExpr
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsLValue
import org.jacodb.ets.model.EtsLeftShiftExpr
import org.jacodb.ets.model.EtsLiteralType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsLocalSignature
import org.jacodb.ets.model.EtsLtEqExpr
import org.jacodb.ets.model.EtsLtExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsModifiers
import org.jacodb.ets.model.EtsMulExpr
import org.jacodb.ets.model.EtsNegExpr
import org.jacodb.ets.model.EtsNeverType
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsNotEqExpr
import org.jacodb.ets.model.EtsNotExpr
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNullishCoalescingExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsOrExpr
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRawEntity
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsRawType
import org.jacodb.ets.model.EtsRef
import org.jacodb.ets.model.EtsRelationExpr
import org.jacodb.ets.model.EtsRemExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsRightShiftExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsStrictEqExpr
import org.jacodb.ets.model.EtsStrictNotEqExpr
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsSubExpr
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsTypeOfExpr
import org.jacodb.ets.model.EtsUnaryExpr
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedConstant
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsUnsignedRightShiftExpr
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.model.EtsVoidType
import org.jacodb.ets.model.EtsYieldExpr
import manager.BinaryExpr as ProtoBinaryExpr
import manager.BlockCfg as ProtoBlockCfg
import manager.CallExpr as ProtoCallExpr
import manager.Class as ProtoClass
import manager.ClassSignature as ProtoClassSignature
import manager.Expr as ProtoExpr
import manager.Field as ProtoField
import manager.FieldSignature as ProtoFieldSignature
import manager.File as ProtoFile
import manager.FileSignature as ProtoFileSignature
import manager.Local as ProtoLocal
import manager.Method as ProtoMethod
import manager.MethodSignature as ProtoMethodSignature
import manager.Ref as ProtoRef
import manager.RelationExpr as ProtoRelationExpr
import manager.Scene as ProtoScene
import manager.Stmt as ProtoStmt
import manager.Type as ProtoType
import manager.UnaryExpr as ProtoUnaryExpr
import manager.Value as ProtoValue

class ProtoToEtsConverter {
    fun convert(scene: ProtoScene): EtsScene {
        return scene.toEts()
    }

    // region model

    fun ProtoScene.toEts(): EtsScene {
        val files = files.map { it.toEts() }
        return EtsScene(files)
    }

    fun ProtoFile.toEts(): EtsFile {
        val fileSignature = signature!!.toEts()
        return EtsFile(
            signature = fileSignature,
            classes = classes.map { it.toEts(fileSignature) },
            namespaces = emptyList(), // TODO
        )
    }

    fun ProtoClass.toEts(
        fileSignature: EtsFileSignature,
    ): EtsClass {
        val classSignature = signature!!.toEts(fileSignature)
        return EtsClassImpl(
            signature = classSignature,
            fields = fields.map { it.toEts(classSignature) },
            methods = methods.map { it.toEts(classSignature) },
            // TODO: category
            // TODO: typeParameters
            // TODO: modifiers
            // TODO: decorators
            // TODO: other...
        )
    }

    fun ProtoField.toEts(
        classSignature: EtsClassSignature,
    ): EtsField {
        return EtsFieldImpl(
            signature = signature!!.toEts(classSignature),
            modifiers = EtsModifiers.EMPTY, // TODO
            isOptional = false, // TODO
            isDefinitelyAssigned = false, // TODO
        )
    }

    fun ProtoMethod.toEts(
        classSignature: EtsClassSignature,
    ): EtsMethod {
        val method = EtsMethodImpl(
            signature = signature!!.toEts(classSignature),
            typeParameters = typeParameters.map { it.toEts() },
            // TODO: modifiers
            // TODO: decorators
        )
        if (cfg != null) {
            method._cfg = cfg!!.toEts(method)
        }
        return method
    }

    // endregion model

    // region signatures

    fun ProtoFileSignature.toEts(): EtsFileSignature {
        return EtsFileSignature(
            projectName = projectName,
            fileName = fileName,
        )
    }

    fun ProtoClassSignature.toEts(
        fileSignature: EtsFileSignature? = null,
    ): EtsClassSignature {
        return EtsClassSignature(
            name = name,
            file = fileSignature
                ?: file_?.toEts()
                ?: EtsFileSignature.UNKNOWN,
            // TODO: namespace
        )
    }

    fun ProtoFieldSignature.toEts(
        classSignature: EtsClassSignature? = null,
    ): EtsFieldSignature {
        return EtsFieldSignature(
            enclosingClass = classSignature
                ?: enclosingClass?.toEts()
                ?: EtsClassSignature.UNKNOWN,
            name = name,
            type = type!!.toEts(),
        )
    }

    fun ProtoMethodSignature.toEts(
        classSignature: EtsClassSignature? = null,
    ): EtsMethodSignature {
        return EtsMethodSignature(
            enclosingClass = classSignature
                ?: enclosingClass?.toEts()
                ?: EtsClassSignature.UNKNOWN,
            name = name,
            parameters = parameters.mapIndexed { i, p ->
                EtsMethodParameter(
                    index = i,
                    name = p.name,
                    type = p.type!!.toEts(),
                    isOptional = false, // TODO
                    isRest = false, // TODO
                )
            },
            returnType = returnType!!.toEts(),
        )
    }

    // endregion signatures

    // region types

    fun ProtoType.toEts(): EtsType = when {
        raw_type != null -> {
            EtsRawType(
                kind = raw_type!!.kind,
                // TODO: extra
            )
        }

        any_type != null -> {
            EtsAnyType
        }

        unknown_type != null -> {
            EtsUnknownType
        }

        union_type != null -> {
            EtsUnionType(
                types = union_type!!.types.map { it.toEts() },
            )
        }

        intersection_type != null -> {
            EtsIntersectionType(
                types = intersection_type!!.types.map { it.toEts() },
            )
        }

        generic_type != null -> {
            EtsGenericType(
                typeName = generic_type!!.type_name,
                constraint = generic_type!!.constraint?.toEts(),
                defaultType = generic_type!!.default_type?.toEts(),
            )
        }

        alias_type != null -> {
            EtsAliasType(
                name = alias_type!!.name,
                originalType = alias_type!!.original_type!!.toEts(),
                // TODO: signature
                // signature = alias_type!!.signature!!.toEts(),
                signature = EtsLocalSignature(
                    "", EtsMethodSignature(
                        EtsClassSignature.UNKNOWN, "", emptyList(),
                        EtsUnknownType
                    )
                ),
            )
        }

        boolean_type != null -> {
            EtsBooleanType
        }

        number_type != null -> {
            EtsNumberType
        }

        string_type != null -> {
            EtsStringType
        }

        null_type != null -> {
            EtsNullType
        }

        undefined_type != null -> {
            EtsUndefinedType
        }

        void_type != null -> {
            EtsVoidType
        }

        never_type != null -> {
            EtsNeverType
        }

        literal_type != null -> {
            EtsLiteralType(
                literalTypeName = literal_type!!.literal_name,
            )
        }

        class_type != null -> {
            EtsClassType(
                signature = class_type!!.signature!!.toEts(),
                typeParameters = class_type!!.type_parameters.map { it.toEts() },
            )
        }

        unclear_ref_type != null -> {
            EtsUnclearRefType(
                name = unclear_ref_type!!.name,
                typeParameters = unclear_ref_type!!.type_parameters.map { it.toEts() },
            )
        }

        array_type != null -> {
            EtsArrayType(
                elementType = array_type!!.element_type!!.toEts(),
                dimensions = array_type!!.dimensions,
            )
        }

        tuple_type != null -> {
            EtsTupleType(
                types = tuple_type!!.types.map { it.toEts() },
            )
        }

        function_type != null -> {
            EtsFunctionType(
                signature = function_type!!.signature!!.toEts(),
            )
        }

        else -> {
            error("Unsupported type: $this")
        }
    }

    // endregion types

    // region cfg

    fun ProtoBlockCfg.toEts(method: EtsMethod): EtsBlockCfg {
        return CfgBuilder(method).build(this)
    }

    inner class CfgBuilder(
        val method: EtsMethod,
    ) {
        private lateinit var currentStmts: MutableList<EtsStmt>

        private var freeTempLocal: Int = 0
        private fun newTempLocal(): EtsLocal {
            return EtsLocal(
                name = "_tmp${freeTempLocal++}",
                // TODO: type
            )
        }

        private fun loc(): EtsStmtLocation {
            return EtsStmtLocation.stub(method)
        }

        private var built: Boolean = false

        fun build(cfg: ProtoBlockCfg): EtsBlockCfg {
            require(!built) { "Method has already been built" }
            val etsCfg = cfg.toEts()
            built = true
            return etsCfg
        }

        fun ProtoBlockCfg.toEts(): EtsBlockCfg {
            if (blocks.isEmpty()) {
                return EtsBlockCfg.EMPTY
            }
            return EtsBlockCfg(
                blocks = blocks.map { block ->
                    currentStmts = mutableListOf()
                    for (stmt in block.statements) {
                        currentStmts += stmt.toEts()
                    }
                    if (currentStmts.isEmpty()) {
                        currentStmts += EtsNopStmt(loc())
                    }
                    BasicBlock(block.id, currentStmts)
                },
                // Note: in AA, successors for IF stmts are (false, true) branches,
                //       however in all our CFGs we use (true, false) order.
                successors = blocks.associate { it.id to it.successors.asReversed() },
            )
        }

        private fun ensureLocal(value: EtsEntity): EtsLocal {
            if (value is EtsLocal) {
                return value
            }
            val local = newTempLocal()
            currentStmts += EtsAssignStmt(
                location = loc(),
                lhv = local,
                rhv = value,
            )
            return local
        }

        // region statements

        fun ProtoStmt.toEts(): EtsStmt = when {
            raw_stmt != null -> {
                EtsRawStmt(
                    location = loc(),
                    kind = raw_stmt!!.kind,
                    // TODO: handle .text
                )
            }

            nop_stmt != null -> {
                EtsNopStmt(
                    location = loc(),
                )
            }

            assign_stmt != null -> {
                EtsAssignStmt(
                    location = loc(),
                    lhv = assign_stmt!!.lhv!!.toEts() as EtsLValue,
                    rhv = assign_stmt!!.rhv!!.toEts(),
                )
            }

            return_stmt != null -> {
                EtsReturnStmt(
                    location = loc(),
                    returnValue = return_stmt!!.return_value?.let { r ->
                        ensureLocal(r.toEts())
                    },
                )
            }

            throw_stmt != null -> {
                EtsThrowStmt(
                    location = loc(),
                    exception = ensureLocal(throw_stmt!!.exception!!.toEts()),
                )
            }

            if_stmt != null -> {
                EtsIfStmt(
                    location = loc(),
                    condition = ensureLocal(if_stmt!!.condition!!.toEts()),
                )
            }

            call_stmt != null -> {
                EtsCallStmt(
                    location = loc(),
                    expr = call_stmt!!.expr!!.toEts(),
                )
            }

            else -> {
                error("Unsupported statement: $this")
            }
        }

        // endregion statements

        // region values

        // TODO: extract each branch to `.toEts()` method
        //  For example, `raw_value != null -> raw_value!!.toEts()`
        fun ProtoValue.toEts(): EtsEntity = when {
            raw_value != null -> {
                EtsRawEntity(
                    kind = raw_value!!.kind,
                    extra = mapOf("text" to raw_value!!.text),
                )
            }

            local != null -> {
                local!!.toEts()
            }

            constant != null -> {
                val type = constant!!.type!!.toEts()
                when (type) {
                    EtsStringType -> EtsStringConstant(value = constant!!.value_)
                    EtsBooleanType -> EtsBooleanConstant(value = constant!!.value_.toBoolean())
                    EtsNumberType -> EtsNumberConstant(value = constant!!.value_.toDouble())
                    EtsNullType -> EtsNullConstant
                    EtsUndefinedType -> EtsUndefinedConstant
                    else -> object : EtsConstant {
                        val value: String = constant!!.value_
                        override val type: EtsType = type
                        override fun toString(): String = value
                        override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
                            return visitor.visit(this)
                        }
                    }
                }
            }

            expr != null -> {
                expr!!.toEts()
            }

            ref != null -> {
                ref!!.toEts()
            }

            else -> {
                error("Unsupported value: $this")
            }
        }

        fun ProtoLocal.toEts(): EtsLocal {
            return EtsLocal(
                name = name,
                type = type!!.toEts(),
            )
        }

        // region expressions

        fun ProtoExpr.toEts(): EtsExpr = when {
            new_expr != null -> {
                EtsNewExpr(
                    type = new_expr!!.type!!.toEts(),
                )
            }

            new_array_expr != null -> {
                EtsNewArrayExpr(
                    elementType = new_array_expr!!.element_type!!.toEts(),
                    size = ensureLocal(new_array_expr!!.size!!.toEts()),
                )
            }

            delete_expr != null -> {
                EtsDeleteExpr(
                    arg = delete_expr!!.arg!!.toEts(),
                )
            }

            await_expr != null -> {
                EtsAwaitExpr(
                    arg = await_expr!!.arg!!.toEts(),
                    type = await_expr!!.type!!.toEts(),
                )
            }

            yield_expr != null -> {
                EtsYieldExpr(
                    arg = yield_expr!!.arg!!.toEts(),
                    type = yield_expr!!.type!!.toEts(),
                )
            }

            type_of_expr != null -> {
                EtsTypeOfExpr(
                    arg = type_of_expr!!.arg!!.toEts(),
                )
            }

            instance_of_expr != null -> {
                EtsInstanceOfExpr(
                    arg = instance_of_expr!!.arg!!.toEts(),
                    checkType = instance_of_expr!!.check_type!!.toEts(),
                )
            }

            cast_expr != null -> {
                EtsCastExpr(
                    arg = cast_expr!!.arg!!.toEts(),
                    type = cast_expr!!.type!!.toEts(),
                )
            }

            unary_expr != null -> {
                unary_expr!!.toEts()
            }

            binary_expr != null -> {
                binary_expr!!.toEts()
            }

            relation_expr != null -> {
                relation_expr!!.toEts()
            }

            call_expr != null -> {
                call_expr!!.toEts()
            }

            else -> {
                error("Unsupported expr: $this")
            }
        }

        fun ProtoUnaryExpr.toEts(): EtsUnaryExpr {
            return when (op) {
                UnaryOperator.NEG -> {
                    EtsNegExpr(
                        arg = arg!!.toEts(),
                        type = type!!.toEts(),
                    )
                }

                UnaryOperator.LOGICAL_NOT -> {
                    EtsNotExpr(
                        arg = arg!!.toEts(),
                    )
                }

                UnaryOperator.BITWISE_NOT -> {
                    EtsBitNotExpr(
                        arg = arg!!.toEts(),
                        type = type!!.toEts(),
                    )
                }

                else -> {
                    error("Unsupported unary operator: $op")
                }
            }
        }

        fun ProtoBinaryExpr.toEts(): EtsBinaryExpr {
            val left = left!!.toEts()
            val right = right!!.toEts()
            return when (op) {
                BinaryOperator.ADDITION -> {
                    EtsAddExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.SUBTRACTION -> {
                    EtsSubExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.MULTIPLICATION -> {
                    EtsMulExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.DIVISION -> {
                    EtsDivExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.REMAINDER -> {
                    EtsRemExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.EXPONENTIATION -> {
                    EtsExpExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.LEFT_SHIFT -> {
                    EtsLeftShiftExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.RIGHT_SHIFT -> {
                    EtsRightShiftExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.UNSIGNED_RIGHT_SHIFT -> {
                    EtsUnsignedRightShiftExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.BITWISE_AND -> {
                    EtsBitAndExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.BITWISE_OR -> {
                    EtsBitOrExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.BITWISE_XOR -> {
                    EtsBitXorExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.LOGICAL_AND -> {
                    EtsAndExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.LOGICAL_OR -> {
                    EtsOrExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                BinaryOperator.NULLISH_COALESCING -> {
                    EtsNullishCoalescingExpr(
                        left = left,
                        right = right,
                        type = type!!.toEts(),
                    )
                }

                else -> {
                    error("Unsupported binary operator: $op")
                }
            }
        }

        fun ProtoRelationExpr.toEts(): EtsRelationExpr {
            val left = left!!.toEts()
            val right = right!!.toEts()
            return when (op) {
                RelationOperator.EQ -> {
                    EtsEqExpr(
                        left = left,
                        right = right,
                    )
                }

                RelationOperator.NEQ -> {
                    EtsNotEqExpr(
                        left = left,
                        right = right,
                    )
                }

                RelationOperator.STRICT_EQ -> {
                    EtsStrictEqExpr(
                        left = left,
                        right = right,
                    )
                }

                RelationOperator.STRICT_NEQ -> {
                    EtsStrictNotEqExpr(
                        left = left,
                        right = right,
                    )
                }

                RelationOperator.LT -> {
                    EtsLtExpr(
                        left = left,
                        right = right,
                    )
                }

                RelationOperator.LTE -> {
                    EtsLtEqExpr(
                        left = left,
                        right = right,
                    )
                }

                RelationOperator.GT -> {
                    EtsGtExpr(
                        left = left,
                        right = right,
                    )
                }

                RelationOperator.GTE -> {
                    EtsGtEqExpr(
                        left = left,
                        right = right,
                    )
                }

                RelationOperator.IN -> {
                    EtsInExpr(
                        left = left,
                        right = right,
                    )
                }

                else -> {
                    error("Unsupported relation operator: $op")
                }
            }
        }

        fun ProtoCallExpr.toEts(): EtsCallExpr {
            val callee = callee!!.toEts()
            val args = args.map { ensureLocal(it.toEts()) }
            val type = type!!.toEts()
            return when {
                instance_call != null -> {
                    EtsInstanceCallExpr(
                        instance = instance_call!!.instance!!.toEts(),
                        callee = callee,
                        args = args,
                        type = type,
                    )
                }

                static_call != null -> {
                    EtsStaticCallExpr(
                        callee = callee,
                        args = args,
                        type = type,
                    )
                }

                ptr_call != null -> {
                    EtsPtrCallExpr(
                        ptr = ensureLocal(ptr_call!!.ptr!!.toEts()),
                        callee = callee,
                        args = args,
                        type = type,
                    )
                }

                else -> {
                    error("Unsupported call expr: $this")
                }
            }
        }

        // endregion expressions

        // region references

        fun ProtoRef.toEts(): EtsRef = when {
            this_ != null -> {
                EtsThis(
                    type = this_!!.type!!.toEts(),
                )
            }

            parameter != null -> {
                EtsParameterRef(
                    index = parameter!!.index,
                    type = parameter!!.type!!.toEts(),
                )
            }

            array_access != null -> {
                EtsArrayAccess(
                    array = array_access!!.array!!.toEts(),
                    index = ensureLocal(array_access!!.index!!.toEts()),
                    type = array_access!!.type!!.toEts(),
                )
            }

            field_ref != null -> {
                when {
                    field_ref!!.instance != null -> {
                        EtsInstanceFieldRef(
                            instance = ensureLocal(field_ref!!.instance!!.instance!!.toEts()),
                            field = field_ref!!.instance!!.field_!!.toEts(),
                            type = field_ref!!.instance!!.type!!.toEts(),
                        )
                    }

                    field_ref!!.static != null -> {
                        EtsStaticFieldRef(
                            field = field_ref!!.static!!.field_!!.toEts(),
                            type = field_ref!!.static!!.type!!.toEts(),
                        )
                    }

                    else -> {
                        error("Unsupported field ref: $this")
                    }
                }
            }

            else -> {
                error("Unsupported ref: $this")
            }
        }

        // endregion references

        // endregion value
    }

    // endregion cfg
}
