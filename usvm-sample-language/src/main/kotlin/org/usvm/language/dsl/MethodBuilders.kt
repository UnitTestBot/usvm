package org.usvm.language.dsl

import org.usvm.language.Expr
import org.usvm.language.SampleType
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty

inline fun <reified R : SampleType?> method(
    returnType: R,
    noinline init: MethodScope<R>.() -> Unit,
) = PropertyDelegateProvider { programDecl: ProgramDecl, property: KProperty<*> ->
    programDecl.getOrPut(property.name) {
        val decl = ProgramDecl.MethodDecl(property.name, emptyList(), returnType, init)
        programDecl.addDecl(decl)
        decl
    }
}

inline fun <reified T1 : SampleType, reified R : SampleType?> method(
    type1: T1,
    returnType: R,
    noinline init: MethodScope<R>.(Expr<T1>) -> Unit,
) = PropertyDelegateProvider { programDecl: ProgramDecl, property: KProperty<*> ->
    programDecl.getOrPut(property.name) {
        val decl = ProgramDecl.MethodDecl(property.name, listOf(type1), returnType) {
            val arg1 by DeclaredArg(type1)
            init(arg1)
        }
        decl
    }
}

inline fun <reified T1 : SampleType, reified T2 : SampleType, reified R : SampleType> ProgramDecl.method(
    type1: T1,
    type2: T2,
    returnType: R,
    noinline init: MethodScope<R>.(Expr<T1>, Expr<T2>) -> Unit,
) = PropertyDelegateProvider { programDecl: ProgramDecl, property: KProperty<*> ->
    programDecl.getOrPut(property.name) {
        val decl = ProgramDecl.MethodDecl(property.name, listOf(type1, type2), returnType) {
            val arg1 by DeclaredArg(type1)
            val arg2 by DeclaredArg(type2)
            init(arg1, arg2)
        }
        decl
    }
}

inline fun <
    reified T1 : SampleType,
    reified T2 : SampleType,
    reified T3 : SampleType,
    reified R : SampleType?,
    > method(
    type1: T1,
    type2: T2,
    type3: T3,
    returnType: R,
    noinline init: MethodScope<R>.(Expr<T1>, Expr<T2>, Expr<T3>) -> Unit,
) = PropertyDelegateProvider { programDecl: ProgramDecl, property: KProperty<*> ->
    programDecl.getOrPut(property.name) {
        val decl = ProgramDecl.MethodDecl(property.name, listOf(type1, type2, type3), returnType) {
            val arg1 by DeclaredArg(type1)
            val arg2 by DeclaredArg(type2)
            val arg3 by DeclaredArg(type3)
            init(arg1, arg2, arg3)
        }
        @Suppress("USELESS_CAST")
        decl as ProgramDecl.MethodDecl<R>
    }
}
