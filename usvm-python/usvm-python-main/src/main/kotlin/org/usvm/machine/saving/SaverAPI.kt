package org.usvm.machine.saving

fun createStandardSaver(): PythonRepresentationSaver<PythonObjectInfo> =
    PythonRepresentationSaver(StandardPythonObjectSerializer)

fun createReprSaver(): PythonRepresentationSaver<String> =
    PythonRepresentationSaver(ReprObjectSerializer)

fun createPickleSaver(): PythonRepresentationSaver<String?> =
    PythonRepresentationSaver(PickleObjectSerializer)