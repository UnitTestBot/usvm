package org.usvm.errors

class UnknownModeError(type: String) :
    HandledError("Unknown running mode: $type. Run the server with \"Server\" or \"Generator\" mode.")
