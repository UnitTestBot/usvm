package org.usvm.fuzzer.strategy

abstract class Selectable {

    open var score: Double = 0.0
    open var numberOfChooses: Int = 0

}