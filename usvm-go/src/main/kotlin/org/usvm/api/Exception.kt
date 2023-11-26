package org.usvm.api

class UnknownMethodException(name: String) : Exception("Unknown method $name")
class UnknownPackageException(name: String) : Exception("Unknown package $name")
class UnknownUnaryOperationException(name: String) : Exception("Unknown unary operation: $name")
class UnknownBinaryOperationException(name: String) : Exception("Unknown binary operation: $name")
class UnknownFunctionException(name: String) : Exception("Unknown function $name")

class UnsupportedUnaryOperationException(name: String) : Exception("Unsupported unary operation: $name")
class UnsupportedInstructionException(name: String) : Exception("Unsupported instruction: $name")
