package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

enum class TestingFeature(val dir: String) {
    RANDOM ("templates/"),
    CONSTRUCTORS("templates/constructors"),
    PATH_SENSITIVITY ("templates/pathSensitivity"),
    CYCLES ("templates/cycles"),
}