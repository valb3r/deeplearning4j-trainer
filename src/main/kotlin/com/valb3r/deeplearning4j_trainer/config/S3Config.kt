package com.valb3r.deeplearning4j_trainer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "s3")
data class S3Config(val accessKeyId: String, val secretKey: String, val region: String, val isHttp: Boolean = false)