package com.valb3r.deeplearning4j_trainer.flowable.calculator

import com.valb3r.deeplearning4j_trainer.SliceLexer
import com.valb3r.deeplearning4j_trainer.SliceParser
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.ParseTree
import org.nd4j.autodiff.samediff.SDIndex

class SliceExpressionParser() {

    fun parse(input: String): List<SDIndex> {
        val chars: CharStream = CharStreams.fromString(input)

        val lexer: Lexer = SliceLexer(chars)
        lexer.addErrorListener(ErrorListener())

        val tokens = CommonTokenStream(lexer)

        val parser = SliceParser(tokens)
        parser.addErrorListener(ErrorListener())

        val tree: ParseTree = parser.start()
        val slice = SliceVisitor()
        return slice.visit(tree)
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