package com.valb3r.deeplearning4j_trainer.config

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.valb3r.deeplearning4j_trainer.config.serde.InputSerde
import com.valb3r.deeplearning4j_trainer.config.serde.OldTrainingSerde
import com.valb3r.deeplearning4j_trainer.config.serde.TrainingSerde
import com.valb3r.deeplearning4j_trainer.config.serde.ValidationSerde
import org.flowable.common.engine.api.async.AsyncTaskExecutor
import org.flowable.common.engine.impl.AbstractEngineConfiguration
import org.flowable.common.engine.impl.EngineConfigurator
import org.flowable.common.engine.impl.Page
import org.flowable.job.service.JobServiceConfiguration
import org.flowable.job.service.impl.persistence.entity.JobEntity
import org.flowable.job.service.impl.persistence.entity.data.impl.MybatisJobDataManager
import org.flowable.spring.SpringProcessEngineConfiguration
import org.flowable.spring.boot.EngineConfigurationConfigurer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncListenableTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.validation.annotation.Validated
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import javax.validation.constraints.Min


@Configuration
class FlowableConfig{
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
    fun taskExecutor(cfg: FlowableExecutorPoolConfig): AsyncListenableTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = cfg.corePoolSize
        executor.maxPoolSize = cfg.maxPoolSize
        executor.queueCapacity = cfg.queueCapacity
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