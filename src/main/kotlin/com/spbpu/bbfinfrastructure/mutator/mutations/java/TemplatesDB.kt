package com.spbpu.bbfinfrastructure.mutator.mutations.java

import java.io.File

object TemplatesDB {
    val manualTemplates = mutableListOf<String>()
    val minedTemplates = mutableListOf<String>()
    val kotlinTemplates = mutableListOf<String>()
    val testTemplates = mutableListOf<String>()

    init {
        File("manualTemplates.txt").readText().split("---------").forEach {
            manualTemplates.add(it)
        }
        File("templates.txt").readText().split("---------").forEach {
            minedTemplates.add(it)
        }
        File("kotlinTemplates.txt").readText().split("---------").forEach {
            kotlinTemplates.add(it)
        }
        File("testTemplates.txt").readText().split("---------").forEach {
            testTemplates.add(it)
        }
    }
}