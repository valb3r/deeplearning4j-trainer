package com.valb3r.deeplearning4j_trainer.flowable.training2

import com.valb3r.deeplearning4j_trainer.flowable.INPUT
import com.valb3r.deeplearning4j_trainer.flowable.WrappedJavaDelegate
import com.valb3r.deeplearning4j_trainer.flowable.getContext
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.stereotype.Service

@Service("chunkTraining")
class ChunkTraining(private val runtime: RuntimeService, private val repositoryService: RepositoryService): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val ctx = execution.getContext()!!
        val definition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionId("model-training-process-step")
            .singleResult()

        (0 until ctx.parallelStepCount).forEach {
            runtime.startProcessInstanceById(
                definition.id,
                "${execution.processInstanceId}-$it",
                mapOf(INPUT to ctx)
            )
        }
    }
}