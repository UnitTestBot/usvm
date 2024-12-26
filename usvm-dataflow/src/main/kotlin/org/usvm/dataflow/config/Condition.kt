/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.config

import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.taint.configuration.And
import org.jacodb.taint.configuration.AnnotationType
import org.jacodb.taint.configuration.ConditionVisitor
import org.jacodb.taint.configuration.ConstantEq
import org.jacodb.taint.configuration.ConstantGt
import org.jacodb.taint.configuration.ConstantLt
import org.jacodb.taint.configuration.ConstantMatches
import org.jacodb.taint.configuration.ConstantTrue
import org.jacodb.taint.configuration.ContainsMark
import org.jacodb.taint.configuration.IsConstant
import org.jacodb.taint.configuration.IsType
import org.jacodb.taint.configuration.Not
import org.jacodb.taint.configuration.Or
import org.jacodb.taint.configuration.PositionResolver
import org.jacodb.taint.configuration.SourceFunctionMatches
import org.jacodb.taint.configuration.TypeMatches
import org.usvm.dataflow.taint.Tainted
import org.usvm.dataflow.util.Traits
import org.usvm.dataflow.util.removeTrailingElementAccessors
import org.usvm.util.Maybe
import org.usvm.util.onSome

open class BasicConditionEvaluator(
    val traits: Traits<CommonMethod, CommonInst>,
    internal val positionResolver: PositionResolver<Maybe<CommonValue>>
) : ConditionVisitor<Boolean> {

    override fun visit(condition: ConstantTrue): Boolean {
        return true
    }

    override fun visit(condition: Not): Boolean {
        return !condition.arg.accept(this)
    }

    override fun visit(condition: And): Boolean {
        return condition.args.all { it.accept(this) }
    }

    override fun visit(condition: Or): Boolean {
        return condition.args.any { it.accept(this) }
    }

    override fun visit(condition: IsType): Boolean {
        // Note: TaintConfigurationFeature.ConditionSpecializer is responsible for
        // expanding IsType condition upon parsing the taint configuration.
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: AnnotationType): Boolean {
        // Note: TaintConfigurationFeature.ConditionSpecializer is responsible for
        // expanding AnnotationType condition upon parsing the taint configuration.
        error("Unexpected condition: $condition")
    }

    override fun visit(condition: IsConstant): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome {
            return isConstant(it)
        }
        return false
    }

    override fun visit(condition: ConstantEq): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome { value ->
            return eqConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantLt): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome { value ->
            return ltConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantGt): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome { value ->
            return gtConstant(value, condition.value)
        }
        return false
    }

    override fun visit(condition: ConstantMatches): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome { value ->
            return matches(value, condition.pattern)
        }
        return false
    }

    override fun visit(condition: SourceFunctionMatches): Boolean {
        TODO("Not implemented yet")
    }

    override fun visit(condition: ContainsMark): Boolean {
        error("This visitor does not support condition $condition. Use FactAwareConditionEvaluator instead")
    }

    override fun visit(condition: TypeMatches): Boolean = with(traits) {
        positionResolver.resolve(condition.position).onSome { value ->
            return typeMatches(value, condition)
        }
        return false
    }
}

class FactAwareConditionEvaluator(
    traits: Traits<CommonMethod, CommonInst>,
    private val fact: Tainted,
    positionResolver: PositionResolver<Maybe<CommonValue>>,
) : BasicConditionEvaluator(traits, positionResolver) {

    override fun visit(condition: ContainsMark): Boolean = with(traits) {
        if (fact.mark != condition.mark) return false
        positionResolver.resolve(condition.position).onSome { value ->
            val variable = convertToPath(value)

            // FIXME: Adhoc for arrays
            val variableWithoutStars = variable.removeTrailingElementAccessors()
            val factWithoutStars = fact.variable.removeTrailingElementAccessors()
            if (variableWithoutStars == factWithoutStars) return true

            return variable == fact.variable
        }
        return false
    }
}
