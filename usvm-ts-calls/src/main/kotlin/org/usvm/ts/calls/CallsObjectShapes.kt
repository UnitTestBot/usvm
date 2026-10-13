package org.usvm.ts.calls

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.PropertyDomain

/** Runtime shapes of the own data properties emitted by the concrete input generator. */
internal class CallsObjectShapes(sourceScene: EtsScene, domains: List<PropertyDomain>) {
    private val shapes = linkedMapOf<ObjectDomain, EtsClassType>()
    private val classes = mutableListOf<EtsClass>()
    private val occupiedNames = sourceScene.projectAndSdkClasses.mapTo(mutableSetOf()) { it.name }
    private val fileSignature: EtsFileSignature
    val scene: EtsScene

    init {
        val occupiedFiles = (sourceScene.projectFiles + sourceScene.sdkFiles).mapTo(mutableSetOf()) { it.signature }
        sourceScene.projectAndSdkClasses.mapTo(occupiedFiles) { it.signature.file }
        fileSignature = generateSequence(0) { it + 1 }
            .map { index -> EtsFileSignature(projectName = "usvm-calls-inputs", fileName = "shapes-$index.ts") }
            .first { it !in occupiedFiles }
        domains.forEach(::register)

        scene = if (classes.isEmpty()) {
            sourceScene
        } else {
            val file = EtsFile(signature = fileSignature, classes = classes, namespaces = emptyList())
            EtsScene(
                projectFiles = sourceScene.projectFiles + file,
                sdkFiles = sourceScene.sdkFiles,
                projectName = sourceScene.projectName,
            )
        }
    }

    fun typeOf(domain: ObjectDomain): EtsClassType = shapes.getValue(domain)

    private fun register(domain: PropertyDomain): EtsType = domain.callsRuntimeType(::registerObject)

    private fun registerObject(domain: ObjectDomain): EtsClassType {
        shapes[domain]?.let { return it }

        val name = generateSequence(shapes.size) { it + 1 }
            .map { index -> "__usvm_calls_input_$index" }
            .first { it !in occupiedNames }
        occupiedNames += name
        val signature = EtsClassSignature(name = name, file = fileSignature)
        val type = EtsClassType(signature)
        shapes[domain] = type
        val fields = domain.fields.map { (fieldName, fieldDomain) ->
            EtsFieldImpl(EtsFieldSignature(enclosingClass = signature, name = fieldName, type = register(fieldDomain)))
        }

        // A source annotation supplies field layout, never methods or nominal ancestry.
        classes += EtsClassImpl(
            signature = signature,
            fields = fields,
            methods = emptyList(),
            category = EtsClassCategory.OBJECT,
        )
        return type
    }
}
