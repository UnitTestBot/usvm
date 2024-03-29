package com.spbpu.bbfinfrastructure.util

import java.io.File
import java.util.*

object BBFProperties {

    private val file: File = File("bbf.conf")

    fun getPropValue(name: String): String? {
        val props = Properties()
        props.load(file.inputStream())
        return props.getProperty(name)
    }

    fun getStringGroup(groupName: String): Map<String, String> {
        val props = Properties()
        props.load(file.inputStream())
        return props.entries
                .map { it.key as String to it.value as String }
                .filter { it.first.startsWith(groupName) }
                .toMap()
                .mapKeys { it.key.takeLastWhile { it != '.' } }
    }

    fun getStringGroupWithoutQuotes(groupName: String): Map<String, String> =
            getStringGroup(groupName).mapValues {
                if (it.value.first() == '"' && it.value.last() == '"')
                    it.value.drop(1).dropLast(1)
                else
                    it.value
            }


}