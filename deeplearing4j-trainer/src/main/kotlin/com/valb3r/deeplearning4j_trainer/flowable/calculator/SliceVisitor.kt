package com.valb3r.deeplearning4j_trainer.flowable.calculator

import com.valb3r.deeplearning4j_trainer.SliceBaseVisitor
import com.valb3r.deeplearning4j_trainer.SliceParser
import org.nd4j.autodiff.samediff.SDIndex

class SliceVisitor: SliceBaseVisitor<MutableList<SDIndex>>() {

    override fun visitSliceNumber(ctx: SliceParser.SliceNumberContext?): MutableList<SDIndex> {
        return mutableListOf(SDIndex.point(ctx!!.NUMBER().text.toLong()))
    }

    override fun visitSliceStar(ctx: SliceParser.SliceStarContext?): MutableList<SDIndex> {
        return mutableListOf(SDIndex.all())
    }

    override fun visitSliceRange(ctx: SliceParser.SliceRangeContext?): MutableList<SDIndex> {
        return (visit(ctx!!.left) + visit(ctx.right)).toMutableList()
    }
}