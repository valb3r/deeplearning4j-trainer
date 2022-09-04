package com.valb3r.deeplearning4j_trainer.flowable.dto

data class InputContext(
    val inputDataPath: String,
    val outputDataPath: String,
    val modelPath: String? = null,
    val dataFilesPath: List<String>? = null,
)