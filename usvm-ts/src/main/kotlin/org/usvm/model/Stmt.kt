package org.usvm.model

data class TsInstLocation(
    val method: TsMethod,
    var index: Int,
)

interface TsStmt {
    val location: TsInstLocation

    interface Visitor<out R> {
        fun visit(stmt: TsNopStmt): R
        fun visit(stmt: TsAssignStmt): R
        fun visit(stmt: TsReturnStmt): R
        fun visit(stmt: TsIfStmt): R
        fun visit(stmt: TsCallStmt): R

        fun visit(stmt: TsRawStmt): R {
            if (this is Default) {
                return defaultVisit(stmt)
            }
            error("Cannot handle ${stmt::class.java.simpleName}: $stmt")
        }

        interface Default<out R> : Visitor<R> {
            override fun visit(stmt: TsNopStmt): R = defaultVisit(stmt)
            override fun visit(stmt: TsAssignStmt): R = defaultVisit(stmt)
            override fun visit(stmt: TsReturnStmt): R = defaultVisit(stmt)
            override fun visit(stmt: TsIfStmt): R = defaultVisit(stmt)
            override fun visit(stmt: TsCallStmt): R = defaultVisit(stmt)
            override fun visit(stmt: TsRawStmt): R = defaultVisit(stmt)

            fun defaultVisit(stmt: TsStmt): R
        }
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class TsRawStmt(
    override val location: TsInstLocation,
    val kind: String,
    val extra: Map<String, Any> = emptyMap(),
) : TsStmt {
    override fun toString(): String {
        return "$kind $extra"
    }

    override fun <R> accept(visitor: TsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsNopStmt(
    override val location: TsInstLocation,
) : TsStmt {
    override fun toString(): String = "nop"

    override fun <R> accept(visitor: TsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsAssignStmt(
    override val location: TsInstLocation,
    val lhv: TsLValue,
    val rhv: TsEntity,
) : TsStmt {
    override fun toString(): String {
        return "$lhv := $rhv"
    }

    override fun <R> accept(visitor: TsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsReturnStmt(
    override val location: TsInstLocation,
    val returnValue: TsLocal?,
) : TsStmt {
    override fun toString(): String {
        return if (returnValue != null) {
            "return $returnValue"
        } else {
            "return"
        }
    }

    override fun <R> accept(visitor: TsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsIfStmt(
    override val location: TsInstLocation,
    val condition: TsLocal,
) : TsStmt {
    override fun toString(): String {
        return "if ($condition)"
    }

    override fun <R> accept(visitor: TsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsCallStmt(
    override val location: TsInstLocation,
    val expr: TsCallExpr,
) : TsStmt {
    override fun toString(): String {
        return expr.toString()
    }

    override fun <R> accept(visitor: TsStmt.Visitor<R>): R {
        return visitor.visit(this)
    }
}
