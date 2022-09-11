package com.valb3r.deeplearning4j_trainer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

@ConstructorBinding
@ConfigurationProperties(prefix = "training.iteration")
data class TrainingIterationConfig(
    val maxDuration: Duration = Duration.ofMinutes(5)
)