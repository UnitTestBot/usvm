package org.usvm.machine

import org.usvm.UAddressSort
import org.usvm.UMockSymbol
import org.usvm.language.TypeMethod
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

data class MockHeader(
    val method: TypeMethod,
    val args: List<UninterpretedSymbolicPythonObject>,
    var methodOwner: UninterpretedSymbolicPythonObject?,
)

data class MockResult(
    val mockedObject: UninterpretedSymbolicPythonObject,
    val isNew: Boolean,
    val mockSymbol: UMockSymbol<UAddressSort>,
)

fun PyState.mock(what: MockHeader): MockResult {
    val cached = mocks[what]
    if (cached != null) {
        return MockResult(UninterpretedSymbolicPythonObject(cached, typeSystem), false, cached)
    }
    val result = memory.mocker.call(
        what.method, what.args.map { it.address }.asSequence(),
        ctx.addressSort,
        memory.ownership
    )
    mocks[what] = result
    what.methodOwner?.let { mockedObjects.add(it) }
    return MockResult(UninterpretedSymbolicPythonObject(result, typeSystem), true, result)
}

fun PyState.getMocksForSymbol(
    symbol: UninterpretedSymbolicPythonObject,
): List<Pair<MockHeader, UninterpretedSymbolicPythonObject>> =
    mocks.mapNotNull { (mockHeader, mockResult) ->
        if (mockHeader.methodOwner == symbol) {
            mockHeader to UninterpretedSymbolicPythonObject(mockResult, typeSystem)
        } else {
            null
        }
    }
