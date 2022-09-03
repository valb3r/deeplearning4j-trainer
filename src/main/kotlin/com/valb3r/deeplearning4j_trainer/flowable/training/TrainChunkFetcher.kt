package com.valb3r.deeplearning4j_trainer.flowable.training

import com.valb3r.deeplearning4j_trainer.flowable.WrappedFutureJavaDelegate
import com.valb3r.deeplearning4j_trainer.flowable.WrappedJavaDelegate
import com.valb3r.deeplearning4j_trainer.flowable.updateContext
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