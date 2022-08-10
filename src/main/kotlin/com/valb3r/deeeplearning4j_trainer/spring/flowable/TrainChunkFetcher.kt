package com.valb3r.deeeplearning4j_trainer.spring.flowable

import org.flowable.engine.delegate.DelegateExecution
import org.springframework.stereotype.Service


@Service("trainChunkFetcher")
class TrainChunkFetcher: WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        execution.updateContext {
            return@updateContext it.copy(currentEpoch = (it.currentEpoch ?: 0L) + 1)
        }
    }
}