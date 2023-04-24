package org.usvm.programs

import org.usvm.language.BooleanType
import org.usvm.language.IntType
import org.usvm.language.builders.ProgramDecl
import org.usvm.language.builders.eq
import org.usvm.language.builders.expr
import org.usvm.language.builders.get
import org.usvm.language.builders.isNull
import org.usvm.language.builders.method
import org.usvm.language.builders.or

object StructProgram : ProgramDecl() {
    object S : StructDecl() {
        val a by IntType
    }

    val checkRefEquality by method(S.type, S.type, returnType = BooleanType) { a, b ->
        branch(a eq b) {
            ret(false.expr)
        }
        ret(true.expr)
    }

    val checkImplicitRefEquality by method(S.type, S.type, returnType = IntType) { a, b ->
        branch(a.isNull or b.isNull) {
            ret(0.expr)
        }
        branch(a[S.a] eq 1.expr) {
            ret(1.expr)
        }
        b[S.a] = 1.expr
        branch(a[S.a] eq 1.expr) {
            ret(2.expr)
        }
        ret(3.expr)
    }
}