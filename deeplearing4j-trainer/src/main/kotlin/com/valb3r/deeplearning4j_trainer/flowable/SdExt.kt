package com.valb3r.deeplearning4j_trainer.flowable

import org.nd4j.autodiff.samediff.SameDiff

fun SameDiff.uniqName(name: String): String {
    var currName: String
    var idx = 0
    do {
        currName = if (0 == idx) name else "$name-$idx"
        idx++
    } while (null != getVariable(currName))
    return currName
}