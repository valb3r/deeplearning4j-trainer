package com.valb3r.deeplearning4j_trainer.flowable.dto

interface Context {

    var currentEpoch: Long
    val inputFiles: List<String>
    val datasetSize: Long?
}