package com.valb3r.deeplearning4j_trainer.flowable.training2

import com.valb3r.deeplearning4j_trainer.flowable.WrappedJavaDelegate
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.stereotype.Service

@Service("nodeTainter")
class NodeTainter: WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        TODO("Not yet implemented")
    }
}