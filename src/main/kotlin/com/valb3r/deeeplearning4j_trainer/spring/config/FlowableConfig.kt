package com.valb3r.deeeplearning4j_trainer.spring.config

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.flowable.spring.SpringProcessEngineConfiguration
import org.flowable.spring.boot.EngineConfigurationConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncListenableTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor


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
            processConfiguration.customPreVariableTypes = listOf(JsonCustomSerializer(mapper))
            processConfiguration.isEnableEventDispatcher = true
        }
    }

    @Bean
    fun taskExecutor(): AsyncListenableTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 2
        executor.queueCapacity = 100
        executor.threadNamePrefix = "flowable-executor"
        executor.setAwaitTerminationSeconds(30)
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }
}