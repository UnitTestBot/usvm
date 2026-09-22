package org.usvm.machine.call

import org.usvm.machine.call.intrinsic.TsBuiltInUnknownCallModel
import org.usvm.machine.call.intrinsic.TsBuiltInUnknownCallModelFamily

/** Discovers built-in model objects from the sealed hierarchy. */
object TsBuiltInUnknownCallModels {
    private val models by lazy {
        val individualModels = TsBuiltInUnknownCallModel::class.sealedSubclasses.map { modelClass ->
            requireNotNull(modelClass.objectInstance) {
                "Built-in semantic model must be an object: ${modelClass.qualifiedName}"
            }
        }
        val modelFamilies = TsBuiltInUnknownCallModelFamily::class.sealedSubclasses.flatMap { familyClass ->
            val family = requireNotNull(familyClass.objectInstance) {
                "Built-in semantic model family must be an object: ${familyClass.qualifiedName}"
            }
            family.models
        }

        individualModels + modelFamilies
    }
    private val allModels by lazy { TsUnknownCallModelCatalog(models) }

    fun catalog(selection: TsUnknownCallModelSelection = TsUnknownCallModelSelection.All): TsUnknownCallModelCatalog =
        if (selection == TsUnknownCallModelSelection.All) allModels else TsUnknownCallModelCatalog(models, selection)
}
