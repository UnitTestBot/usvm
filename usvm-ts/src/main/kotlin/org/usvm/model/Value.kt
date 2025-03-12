package org.usvm.model

interface TsValue : TsEntity {
    interface Visitor<out R> {
        fun visit(value: TsLocal): R

        // Constant
        fun visit(value: TsStringConstant): R
        fun visit(value: TsBooleanConstant): R
        fun visit(value: TsNumberConstant): R
        fun visit(value: TsNullConstant): R
        fun visit(value: TsUndefinedConstant): R

        // Ref
        fun visit(value: TsThis): R
        fun visit(value: TsParameterRef): R
        fun visit(value: TsArrayAccess): R
        fun visit(value: TsInstanceFieldRef): R
        fun visit(value: TsStaticFieldRef): R

        interface Default<out R> : Visitor<R> {
            override fun visit(value: TsLocal): R = defaultVisit(value)

            override fun visit(value: TsStringConstant): R = defaultVisit(value)
            override fun visit(value: TsBooleanConstant): R = defaultVisit(value)
            override fun visit(value: TsNumberConstant): R = defaultVisit(value)
            override fun visit(value: TsNullConstant): R = defaultVisit(value)
            override fun visit(value: TsUndefinedConstant): R = defaultVisit(value)

            override fun visit(value: TsThis): R = defaultVisit(value)
            override fun visit(value: TsParameterRef): R = defaultVisit(value)
            override fun visit(value: TsArrayAccess): R = defaultVisit(value)
            override fun visit(value: TsInstanceFieldRef): R = defaultVisit(value)
            override fun visit(value: TsStaticFieldRef): R = defaultVisit(value)

            fun defaultVisit(value: TsValue): R
        }
    }

    override fun <R> accept(visitor: TsEntity.Visitor<R>): R {
        return accept(visitor as Visitor<R>)
    }

    fun <R> accept(visitor: Visitor<R>): R
}
