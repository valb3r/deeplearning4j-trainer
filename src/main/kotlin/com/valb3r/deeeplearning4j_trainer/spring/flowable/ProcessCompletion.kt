package com.valb3r.deeeplearning4j_trainer.spring.flowable

import com.valb3r.deeeplearning4j_trainer.spring.repository.TrainingProcessRepository
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.stereotype.Service

@Service("processCompletion")
class ProcessCompletion(private val trainingRepo: TrainingProcessRepository): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val proc = trainingRepo.findByProcessId(execution.processInstanceId) ?: return
        proc.completed = true
        trainingRepo.save(proc)
    }
}