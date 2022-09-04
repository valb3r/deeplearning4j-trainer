package com.valb3r.deeplearning4j_trainer.flowable.calculator

import com.valb3r.deeplearning4j_trainer.CalculatorLexer
import com.valb3r.deeplearning4j_trainer.CalculatorParser
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTree
import org.nd4j.autodiff.samediff.SDVariable
import org.nd4j.autodiff.samediff.SameDiff

class ExpressionParser(private val sd: SameDiff) {

    fun parse(input: String): SDVariable {
        val chars: CharStream = CharStreams.fromString(input)

        val lexer: Lexer = CalculatorLexer(chars)
        lexer.addErrorListener(ErrorListener())

        val tokens = CommonTokenStream(lexer)

        val parser = CalculatorParser(tokens)
        parser.addErrorListener(ErrorListener())

        val tree: ParseTree = parser.start()
        val calculator = CalculatorVisitor(sd)
        return calculator.visit(tree)
    }

    class ErrorListener : BaseErrorListener() {
        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String?,
            e: RecognitionException?
        ) {
            throw IllegalArgumentException("$offendingSymbol at $line:$charPositionInLine", e)
        }
    }
}