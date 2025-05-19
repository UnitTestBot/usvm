package org.usvm.service

import manager.classSignature
import manager.fieldSignature
import manager.fileSignature
import manager.methodParameter
import manager.methodSignature
import manager.namespaceSignature
import mu.KotlinLogging
import org.jacodb.ets.grpc.toProto
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespaceSignature
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.toType
import usvm.infer.argumentTypeResult
import usvm.infer.classTypeResult
import usvm.infer.fieldTypeResult
import usvm.infer.inferredTypes
import usvm.infer.localTypeResult
import usvm.infer.methodTypeResult
import manager.ClassSignature as ProtoClassSignature
import manager.FieldSignature as ProtoFieldSignature
import manager.FileSignature as ProtoFileSignature
import manager.MethodParameter as ProtoMethodParameter
import manager.MethodSignature as ProtoMethodSignature
import manager.NamespaceSignature as ProtoNamespaceSignature
import usvm.infer.InferredTypes as ProtoInferredTypes

fun TypeInferenceResult.toProto(): ProtoInferredTypes {
    val classTypeInferenceResult = inferredCombinedThisType.map { (clazz, fact) ->
        val properties = (fact as? EtsTypeFact.ObjectEtsTypeFact)?.properties ?: emptyMap()
        val methods = properties
            .filter { it.value is EtsTypeFact.FunctionEtsTypeFact }
            .keys
            .sortedBy { it }
        val fields = properties
            .filterNot { it.value is EtsTypeFact.FunctionEtsTypeFact }
            .mapNotNull { (name, fact) ->
                fact.toType()?.let {
                    fieldTypeResult {
                        this.name = name
                        this.type = it.toProto()
                    }
                }
            }
            .sortedBy { it.name }
        classTypeResult {
            this.signature = clazz.toProto()
            this.fields += fields
            this.methods += methods
        }
    }.sortedBy {
        it.signature.toString()
    }

    val methodTypeInferenceResult = inferredTypes.map { (method, facts) ->
        val args = facts.mapNotNull { (base, fact) ->
            if (base is AccessPathBase.Arg) {
                val type = fact.toType()
                if (type != null) {
                    return@mapNotNull argumentTypeResult {
                        this.index = base.index
                        this.type = type.toProto()
                    }
                }
            }
            null
        }.sortedBy { it.index }
        val returnType = inferredReturnType[method]?.toType()?.toProto()
        val locals = facts.mapNotNull { (base, fact) ->
            if (base is AccessPathBase.Local) {
                val type = fact.toType()
                if (type != null) {
                    return@mapNotNull localTypeResult {
                        this.name = base.name
                        this.type = type.toProto()
                    }
                }
            }
            null
        }.sortedBy { it.name }
        methodTypeResult {
            this.signature = method.signature.toProto()
            this.args += args
            returnType?.let { this.returnType = it }
            this.locals += locals
        }
    }.sortedBy {
        it.signature.toString()
    }

    return inferredTypes {
        this.classes += classTypeInferenceResult
        this.methods += methodTypeInferenceResult
    }
}

fun EtsFileSignature.toProto(): ProtoFileSignature =
    fileSignature {
        this.projectName = this@toProto.projectName
        this.fileName = this@toProto.fileName
    }

fun EtsNamespaceSignature.toProto(): ProtoNamespaceSignature =
    namespaceSignature {
        this.name = this@toProto.name
        this.file = this@toProto.file.toProto()
        this@toProto.namespace?.let { this.parent = it.toProto() }
    }

fun EtsClassSignature.toProto(): ProtoClassSignature =
    classSignature {
        this.name = this@toProto.name
        this.file = this@toProto.file.toProto()
        this@toProto.namespace?.let { this.namespace = it.toProto() }
    }

fun EtsFieldSignature.toProto(): ProtoFieldSignature =
    fieldSignature {
        this.enclosingClass = this@toProto.enclosingClass.toProto()
        this.name = this@toProto.name
        this.type = this@toProto.type.toProto()
    }

fun EtsMethodSignature.toProto(): ProtoMethodSignature =
    methodSignature {
        this.enclosingClass = this@toProto.enclosingClass.toProto()
        this.name = this@toProto.name
        this.parameters += this@toProto.parameters.map { it.toProto() }
        this.returnType = this@toProto.returnType.toProto()
    }

fun EtsMethodParameter.toProto(): ProtoMethodParameter =
    methodParameter {
        this.name = this@toProto.name
        this.type = this@toProto.type.toProto()
        this.isOptional = this@toProto.isOptional
    }
