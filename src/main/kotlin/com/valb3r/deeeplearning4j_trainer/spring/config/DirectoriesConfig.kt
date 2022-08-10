package com.valb3r.deeeplearning4j_trainer.spring.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import javax.validation.constraints.NotBlank

@ConstructorBinding
@ConfigurationProperties(prefix = "directories")
data class DirectoriesConfig(
    @NotBlank val input: String,
    @NotBlank val output: String
)