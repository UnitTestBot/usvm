package org.usvm.language

sealed class PropertyOfPythonObject
sealed class ContentOfPrimitiveType: PropertyOfPythonObject()
object IntContent: ContentOfPrimitiveType()
object BoolContent: ContentOfPrimitiveType()
object ListOfListIterator: ContentOfPrimitiveType()
object IndexOfListIterator: ContentOfPrimitiveType()