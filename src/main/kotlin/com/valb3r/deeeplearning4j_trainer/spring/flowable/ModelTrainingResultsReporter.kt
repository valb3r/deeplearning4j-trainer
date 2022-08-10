package com.valb3r.deeeplearning4j_trainer.spring.flowable

import mu.KotlinLogging
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}
@Service("modelTrainingResultsReporter")
class ModelTrainingResultsReporter: WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        logger.info { "Training completed for one epoch" }
    }
}