package org.usvm.fuzzer.generator

import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.usvm.fuzzer.generator.arrays.ArrayGenerator
import org.usvm.fuzzer.generator.collections.ArrayListGenerator
import org.usvm.fuzzer.generator.other.StringGenerator
import org.usvm.fuzzer.generator.primitives.*
import org.usvm.fuzzer.util.arrayListType
import org.usvm.instrumentation.util.stringType

class GeneratorRepository {

    private val generators = HashMap<JcType, List<Generator>>()
    lateinit var context: GeneratorContext
    lateinit var jcClasspath: JcClasspath

    fun registerGeneratorContext(generatorContext: GeneratorContext) {
        context = generatorContext
        jcClasspath = generatorContext.jcClasspath
    }

    private fun Generator.appendContext(): Generator = this.also { it.ctx = context }

    fun getGeneratorForType(jcType: JcType): Generator? = with(jcType.unboxIfNeeded()) {
        when (this) {
            is JcArrayType -> ArrayGenerator(elementType)
            is JcPrimitiveType -> getPrimitiveGeneratorForType(this, jcClasspath)
            is JcClassType -> getBuiltInOrUserGenerator(this, jcClasspath)
            else -> TODO()
        }.appendContext()
    }

    private fun getBuiltInOrUserGenerator(jcType: JcClassType, jcClasspath: JcClasspath) =
        when (jcType.typeNameWOGenerics) {
            jcClasspath.arrayListType().typeNameWOGenerics -> ArrayListGenerator(jcType)
            else -> TODO()
        }

    private val JcType.typeNameWOGenerics
        get() = typeName.substringBefore('<')

    private fun getPrimitiveGeneratorForType(jcType: JcType, jcClasspath: JcClasspath) =
        when (jcType) {
            jcClasspath.boolean -> BooleanGenerator()
            jcClasspath.byte -> ByteGenerator()
            jcClasspath.int -> IntegerGenerator()
            jcClasspath.short -> ShortGenerator()
            jcClasspath.long -> LongGenerator()
            jcClasspath.float -> FloatGenerator()
            jcClasspath.double -> DoubleGenerator()
            jcClasspath.char -> CharGenerator()
            jcClasspath.stringType() -> StringGenerator()
            else -> error("Not primitive type")
        }


}