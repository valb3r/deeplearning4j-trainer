package com.valb3r.deeplearning4j_trainer.flowable.dto

interface Context {

    val datasetSize: Long
    var currentEpoch: Long
    val inputFiles: List<String>
}