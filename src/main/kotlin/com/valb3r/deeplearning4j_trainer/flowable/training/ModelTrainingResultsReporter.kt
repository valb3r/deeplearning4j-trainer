package com.valb3r.deeplearning4j_trainer.flowable.training

import com.valb3r.deeplearning4j_trainer.flowable.WrappedFutureJavaDelegate
import com.valb3r.deeplearning4j_trainer.flowable.WrappedJavaDelegate
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