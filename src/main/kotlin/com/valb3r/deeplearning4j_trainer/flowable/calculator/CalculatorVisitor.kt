package com.valb3r.deeplearning4j_trainer.flowable.calculator

import com.valb3r.deeplearning4j_trainer.CalculatorBaseVisitor
import com.valb3r.deeplearning4j_trainer.CalculatorParser
import org.nd4j.autodiff.samediff.SDVariable
import org.nd4j.autodiff.samediff.SameDiff

class CalculatorVisitor(private val sd: SameDiff): CalculatorBaseVisitor<SDVariable>() {

    override fun visitSdRef(ctx: CalculatorParser.SdRefContext?): SDVariable {
        return sd.getVariable(ctx!!.SD_VAR().text.trim('`'))
    }

    override fun visitNumber(ctx: CalculatorParser.NumberContext?): SDVariable {
        return sd.constant(ctx!!.NUMBER().text.toDouble())
    }

    override fun visitSlices(ctx: CalculatorParser.SlicesContext?): SDVariable {
        return visit(ctx!!.ref).get(*SliceExpressionParser().parse(ctx.index.text).toTypedArray())
    }

    override fun visitAdditionOrSubtraction(ctx: CalculatorParser.AdditionOrSubtractionContext?): SDVariable {
        if (ctx!!.operator.text == "+") {
            return sd.math().add(visit(ctx.left), visit(ctx.right))
        }

        return sd.math().sub(visit(ctx.left), visit(ctx.right))
    }

    override fun visitMultiplicationOrDivision(ctx: CalculatorParser.MultiplicationOrDivisionContext?): SDVariable {
        if (ctx!!.operator.text == "*") {
            return sd.math().mul(visit(ctx.left), visit(ctx.right))
        }

        return sd.math().div(visit(ctx.left), visit(ctx.right))
    }

    override fun visitParentheses(ctx: CalculatorParser.ParenthesesContext?): SDVariable {
        return visit(ctx!!.inner)
    }

    override fun visitPower(ctx: CalculatorParser.PowerContext?): SDVariable {
        return sd.math().pow(visit(ctx!!.left), visit(ctx.right))
    }
}