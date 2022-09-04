package com.valb3r.deeplearning4j_trainer.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.valb3r.deeplearning4j_trainer.config.ObjectMapperConfig

val domainCtxMapper = ObjectMapperConfig.configure(ObjectMapper().registerModule(KotlinModule.Builder().build()))