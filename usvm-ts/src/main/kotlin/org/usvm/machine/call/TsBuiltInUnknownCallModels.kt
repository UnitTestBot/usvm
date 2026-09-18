package org.usvm.machine.call

import org.usvm.machine.call.intrinsic.TsBuiltInUnknownCallModel

/** Discovers built-in model objects from the sealed hierarchy. */
object TsBuiltInUnknownCallModels {
    private val models by lazy {
        TsBuiltInUnknownCallModel::class.sealedSubclasses.map { modelClass ->
            requireNotNull(modelClass.objectInstance) {
                "Built-in semantic model must be an object: ${modelClass.qualifiedName}"
            }
        }
    }
    private val allModels by lazy { TsUnknownCallModelCatalog(models) }

    fun catalog(selection: TsUnknownCallModelSelection = TsUnknownCallModelSelection.All): TsUnknownCallModelCatalog =
        if (selection == TsUnknownCallModelSelection.All) allModels else TsUnknownCallModelCatalog(models, selection)
}
