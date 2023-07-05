package org.usvm.language

sealed class PropertyOfPythonObject
sealed class ContentOfPrimitiveType: PropertyOfPythonObject()
object IntContent: ContentOfPrimitiveType()
object BoolContent: ContentOfPrimitiveType()