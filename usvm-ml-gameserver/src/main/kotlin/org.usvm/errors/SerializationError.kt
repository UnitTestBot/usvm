package org.usvm.errors

abstract class SerializationError(msg: String) : HandledError(msg)
class ExcessKeysError(keys: Array<String>) : SerializationError("Message contains excess keys: $keys")