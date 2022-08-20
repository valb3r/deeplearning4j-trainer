package com.valb3r.deeplearning4j_trainer.flowable.validation

import com.valb3r.deeplearning4j_trainer.flowable.WrappedJavaDelegate
import com.valb3r.deeplearning4j_trainer.flowable.getValidationContext
import com.valb3r.deeplearning4j_trainer.flowable.setContext
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.stereotype.Service

@Service("modelValidationResultsReporter")
class ModelValidationResultsReporter: WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val ctx = execution.getValidationContext()!!
        ctx.currentEpoch += 1
        execution.setContext(ctx)
    }
}