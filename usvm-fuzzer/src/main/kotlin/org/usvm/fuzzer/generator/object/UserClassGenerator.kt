package org.usvm.fuzzer.generator.`object`

import org.jacodb.api.JcClassType
import org.jacodb.api.ext.isEnum
import org.usvm.fuzzer.generator.GenerationMode
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation

sealed class UserClassGenerator: Generator()