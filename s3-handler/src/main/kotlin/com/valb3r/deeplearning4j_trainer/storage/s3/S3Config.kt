package com.valb3r.deeplearning4j_trainer.storage.s3

data class S3Config(
    val accessKeyId: String,
    val secretKey: String,
    val region: String,
    val isHttp: Boolean = false,
    val maxChunkSizeBytes: Int
)