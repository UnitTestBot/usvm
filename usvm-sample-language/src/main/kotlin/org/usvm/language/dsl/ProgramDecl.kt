package org.usvm.language.dsl

import org.usvm.language.Expr
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.Program
import org.usvm.language.SampleType
import org.usvm.language.Struct
import org.usvm.language.StructCreation
import org.usvm.language.StructExpr
import org.usvm.language.StructType
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class ProgramDecl {
    private val name = this::class.simpleName ?: error("Program must be a non-anonymous object")

    private val methodDecls: MutableMap<String, MethodDecl<SampleType?>> = mutableMapOf()
    private val structs: MutableMap<String, Struct> = mutableMapOf()

    fun addDecl(decl: MethodDecl<SampleType?>) = methodDecls.put(decl.name, decl)
    @Suppress("UNCHECKED_CAST")
    fun <R : SampleType?> getOrPut(name: String, block: () -> MethodDecl<R>): MethodDecl<R> =
        methodDecls.getOrPut(name) { block() } as MethodDecl<R>

    private fun addStruct(struct: Struct) = structs.put(struct.name, struct)

    operator fun invoke() = Program(
        name,
        structs.values.toList(),
        methodDecls.values.map { it.apply { build() }.method }
    )

    class MethodDecl<out R : SampleType?>(
        val name: String,
        argumentTypes: List<SampleType>,
        returnType: R,
        val body: MethodScope<@UnsafeVariance R>.() -> Unit
    ) : ReadOnlyProperty<ProgramDecl, Method<R>> {
        private val scope = MethodScope(name, argumentTypes, returnType)

        val method = scope.init()
        fun build() {
            scope.body()
            scope.build()
        }

        override operator fun getValue(thisRef: ProgramDecl, property: KProperty<*>): Method<R> = method
    }

    abstract inner class StructDecl {
        private val name: String = this::class.simpleName ?: error("Struct must be a non-anonymous object")

        private val fields = mutableSetOf<Field<SampleType>>()

        private val struct = Struct(name, fields).also { addStruct(it) }
        val type = StructType(struct)

        protected operator fun <T : SampleType> T.provideDelegate(thisRef: StructDecl, property: KProperty<*>): Lazy<Field<T>> {
            val field = Field(property.name, this)
            fields += field
            return lazy { field }
        }

        operator fun invoke(vararg initValues: Pair<Field<SampleType>, Expr<SampleType>>): StructExpr {
            return StructCreation(struct, initValues.toList())
        }


    }
}
