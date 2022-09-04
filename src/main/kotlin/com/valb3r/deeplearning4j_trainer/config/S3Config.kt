package com.valb3r.deeplearning4j_trainer.config

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.util.unit.DataSize

@ConstructorBinding
@ConfigurationProperties(prefix = "s3")
data class S3Config(
    val accessKeyId: String,
    val secretKey: String,
    val region: String,
    val isHttp: Boolean = false,
    @JsonIgnore val maxChunkSize: DataSize = DataSize.ofMegabytes(30) // 30 * 10_000 = 300Gb max upload
)