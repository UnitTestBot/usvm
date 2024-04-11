package com.spbpu.bbfinfrastructure.util

import java.util.jar.JarFile

object JavaTypeMappings {
    val mappings = initMappings(mutableMapOf())

    private fun initMappings(mappings: MutableMap<String, String>): MutableMap<String, String> {
        JarFile(CompilerArgs.pathToOwaspJar).use { jar ->
            val entries = jar.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".class")) {
                    val className = entry.name.replace("/", ".").removeSuffix(".class")
                    mappings[className.substringAfterLast(".")] = className
                }
            }
        }
        mappings.putAll(
            mapOf(
                "String" to "java.lang.String",
                "Integer" to "java.lang.Integer",
                "Double" to "java.lang.Double",
                "Float" to "java.lang.Float",
                "Boolean" to "java.lang.Boolean",
                "Character" to "java.lang.Character",
                "Byte" to "java.lang.Byte",
                "Short" to "java.lang.Short",
                "Long" to "java.lang.Long",
                "Object" to "java.lang.Object",
                "Class" to "java.lang.Class",
                "StringBuilder" to "java.lang.StringBuilder",
                "StringBuffer" to "java.lang.StringBuffer",
                "Throwable" to "java.lang.Throwable",
                "Exception" to "java.lang.Exception",
                "RuntimeException" to "java.lang.RuntimeException",
                "Error" to "java.lang.Error",
                "Iterable" to "java.lang.Iterable",
                "Comparable" to "java.lang.Comparable",
                "Runnable" to "java.lang.Runnable",
                "Thread" to "java.lang.Thread",
                "System" to "java.lang.System",
                "Math" to "java.lang.Math",
                "Number" to "java.lang.Number",
                "Enum" to "java.lang.Enum",
                "Iterable" to "java.lang.Iterable",
                "Iterator" to "java.util.Iterator",
                "Collection" to "java.util.Collection",
                "List" to "java.util.List",
                "ArrayList" to "java.util.ArrayList",
                "LinkedList" to "java.util.LinkedList",
                "Set" to "java.util.Set",
                "HashSet" to "java.util.HashSet",
                "TreeSet" to "java.util.TreeSet",
                "Map" to "java.util.Map",
                "HashMap" to "java.util.HashMap",
                "TreeMap" to "java.util.TreeMap",
                "LinkedHashMap" to "java.util.LinkedHashMap",
                "Date" to "java.util.Date",
                "Calendar" to "java.util.Calendar",
                "SimpleDateFormat" to "java.text.SimpleDateFormat",
                "DateFormat" to "java.text.DateFormat",
                "DecimalFormat" to "java.text.DecimalFormat",
                "NumberFormat" to "java.text.NumberFormat",
                "InputStream" to "java.io.InputStream",
                "OutputStream" to "java.io.OutputStream",
                "FileInputStream" to "java.io.FileInputStream",
                "FileOutputStream" to "java.io.FileOutputStream",
                "ByteArrayOutputStream" to "java.io.ByteArrayOutputStream",
                "InputStreamReader" to "java.io.InputStreamReader",
                "OutputStreamWriter" to "java.io.OutputStreamWriter",
                "FileReader" to "java.io.FileReader",
                "FileWriter" to "java.io.FileWriter",
                "File" to "java.io.File",
                "BufferedReader" to "java.io.BufferedReader",
                "BufferedWriter" to "java.io.BufferedWriter",
                "PrintWriter" to "java.io.PrintWriter",
                "RandomAccessFile" to "java.io.RandomAccessFile",
                "ObjectInputStream" to "java.io.ObjectInputStream",
                "ObjectOutputStream" to "java.io.ObjectOutputStream",
                "ByteArrayInputStream" to "java.io.ByteArrayInputStream",
                "DataInputStream" to "java.io.DataInputStream",
                "DataOutputStream" to "java.io.DataOutputStream",
                "FileFilter" to "java.io.FileFilter",
                "FilenameFilter" to "java.io.FilenameFilter",
                "Serializable" to "java.io.Serializable",
                "Cloneable" to "java.lang.Cloneable",
                "Comparator" to "java.util.Comparator",
                "Arrays" to "java.util.Arrays",
                "Collections" to "java.util.Collections",
                "Scanner" to "java.util.Scanner",
                "StringTokenizer" to "java.util.StringTokenizer",
                "Timer" to "java.util.Timer",
                "TimerTask" to "java.util.TimerTask",
                "UUID" to "java.util.UUID",
                "WeakHashMap" to "java.util.WeakHashMap",
                "BitSet" to "java.util.BitSet",
                "Observable" to "java.util.Observable",
                "Observer" to "java.util.Observer",
                "Properties" to "java.util.Properties",
                "ResourceBundle" to "java.util.ResourceBundle",
                "JarFile" to "java.util.jar.JarFile",
                "JarEntry" to "java.util.jar.JarEntry",
                "ZipFile" to "java.util.zip.ZipFile",
                "ZipEntry" to "java.util.zip.ZipEntry",
            )
        )
        return mappings
    }
}