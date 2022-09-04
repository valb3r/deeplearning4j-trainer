package com.valb3r.deeplearning4j_trainer.flowable.validation

import com.valb3r.deeplearning4j_trainer.flowable.WrappedJavaDelegate
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.stereotype.Service


@Service("validationChunkFetcher")
class ValidationChunkFetcher: WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        // NOP
    }
}