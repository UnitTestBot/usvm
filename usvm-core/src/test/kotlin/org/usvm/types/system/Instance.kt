package org.usvm.types.system

val top: TestType

val base1: TestType
val base2: TestType

val interface1: TestType
val interface2: TestType
val combinedInterface: TestType

val derived1A: TestType
val derived1B: TestType
val derivedMulti: TestType

val comparable: TestType
val userComparable: TestType
val derivedMultiInterfaces: TestType

val interfaceAB: TestType
val interfaceBC1: TestType
val interfaceBC2: TestType
val interfaceAC: TestType

val a: TestType
val b: TestType
val c: TestType

val testTypeSystem = buildTypeSystem {
    top = topType

    base1 = open()
    base2 = open()

    interface1 = `interface`()
    interface2 = `interface`()
    comparable = `interface`()

    combinedInterface = `interface`().implements(interface1, interface2)

    derived1A = open().implements(base1, interface1)
    derived1B = open().implements(base1, interface2)
    derivedMulti = open().implements(base2, combinedInterface)
    derivedMultiInterfaces = open().implements(base2, interface1, interface2)
    userComparable = open().implements(interface2, comparable)

    interfaceAB = `interface`()
    interfaceBC2 = `interface`()
    interfaceBC1 = `interface`().implements(interfaceBC2)
    interfaceAC = `interface`()

    a = open().implements(interfaceAB, interfaceAC)
    b = open().implements(interfaceAB, interfaceBC1)
    c = open().implements(interfaceAC, interfaceBC1)


    // simulate inheritors of top type
    repeat(10_000) { idx ->
        when (idx % 2) {
            0 -> open()
            1 -> abstract()
        }
    }

    // simulate inheritors of comparable
    repeat(10_000) { idx ->
        when (idx % 3) {
            0 -> open() implements comparable
            1 -> `interface`() implements comparable
            2 -> abstract() implements comparable
        }
    }
}

