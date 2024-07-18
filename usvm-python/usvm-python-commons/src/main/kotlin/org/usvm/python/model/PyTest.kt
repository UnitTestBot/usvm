package org.usvm.python.model

class PyTest<PyObjectRepr>(
    val inputModel: PyInputModel,
    val inputArgs: List<PyObjectRepr>,
    val result: PyResult<PyObjectRepr>,
)

class PyInputModel(
    val inputArgs: List<PyValue>,
)

sealed class PyResult<PyObjectRepr>

class PyResultSuccess<PyObjectRepr>(
    val output: PyObjectRepr,
) : PyResult<PyObjectRepr>()

class PyResultFailure<PyObjectRepr>(
    val exception: PyObjectRepr,
) : PyResult<PyObjectRepr>()
