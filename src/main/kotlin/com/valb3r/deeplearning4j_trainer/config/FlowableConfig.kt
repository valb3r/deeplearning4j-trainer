package com.valb3r.deeplearning4j_trainer.config

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.valb3r.deeplearning4j_trainer.config.serde.InputSerde
import com.valb3r.deeplearning4j_trainer.config.serde.OldTrainingSerde
import com.valb3r.deeplearning4j_trainer.config.serde.TrainingSerde
import com.valb3r.deeplearning4j_trainer.config.serde.ValidationSerde
import org.flowable.spring.SpringProcessEngineConfiguration
import org.flowable.spring.boot.EngineConfigurationConfigurer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncListenableTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank


@Configuration
class FlowableConfig {
    /**
     * Customizes flowable so that it can store custom classes (not ones that implement Serializable) as
     * JSON as variables in database.
     */
    @Bean
    fun jsonSerializer(): EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {
        val mapper = ObjectMapper()
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

        return EngineConfigurationConfigurer { processConfiguration: SpringProcessEngineConfiguration ->
            processConfiguration.customPreVariableTypes = listOf(
                InputSerde(mapper),
                TrainingSerde(mapper),
                ValidationSerde(mapper),
                OldTrainingSerde(mapper)
            )
            processConfiguration.isEnableEventDispatcher = true
        }
    }

    @Bean
    fun taskExecutor(poolCfg: FlowableExecutorPoolConfig): AsyncListenableTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = poolCfg.corePoolSize
        executor.maxPoolSize = poolCfg.maxPoolSize
        executor.queueCapacity = poolCfg.queueCapacity
        executor.threadNamePrefix = "flowable-executor"
        executor.setAwaitTerminationSeconds(30)
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    @Validated
    @ConstructorBinding
    @ConfigurationProperties(prefix = "flowable-executor.pool")
    data class FlowableExecutorPoolConfig(
        @Min(1) val corePoolSize: Int,
        @Min(1) val maxPoolSize: Int,
        @Min(1) val queueCapacity: Int
    )
}