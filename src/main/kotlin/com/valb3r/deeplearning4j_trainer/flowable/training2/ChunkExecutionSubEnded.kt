package com.valb3r.deeplearning4j_trainer.flowable.training2

import com.valb3r.deeplearning4j_trainer.flowable.WrappedJavaDelegate
import com.valb3r.deeplearning4j_trainer.flowable.getContext
import com.valb3r.deeplearning4j_trainer.flowable.updateContext
import org.flowable.engine.RuntimeService
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.stereotype.Service

@Service("chunkExecutionSubEnded")
class ChunkExecutionSubEnded(private val runtime: RuntimeService): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val ctx = execution.getContext()!!
        ctx.activeChildExecutionIds = ctx.activeChildExecutionIds - execution.id
        if (ctx.activeChildExecutionIds.isEmpty()) {
            runtime.trigger(ctx.parentExecutionId)
        }
        execution.updateContext { ctx }
    }
}