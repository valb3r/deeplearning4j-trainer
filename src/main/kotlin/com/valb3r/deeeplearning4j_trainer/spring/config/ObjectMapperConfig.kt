package com.valb3r.deeeplearning4j_trainer.spring.config

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class ObjectMapperConfig {

    @Bean
    fun yamlObjectMapper(): ObjectMapper {
        return configure(ObjectMapper(YAMLFactory()))
            .registerModule(KotlinModule.Builder().build())
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    }

    @Bean
    @Primary
    fun jsonObjectMapper(): ObjectMapper {
        return configure(ObjectMapper().registerModule(KotlinModule.Builder().build()))
    }

    companion object Configurer {

        fun configure(mapper: ObjectMapper): ObjectMapper {
            mapper.findAndRegisterModules()
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // Ignoring getters and setters as we are using 'rich' domain model:
            mapper.setVisibility(
                mapper.serializationConfig.defaultVisibilityChecker
                    .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withCreatorVisibility(JsonAutoDetect.Visibility.ANY)
            )
            return mapper
        }
    }
}