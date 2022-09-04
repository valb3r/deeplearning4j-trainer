package com.valb3r.deeplearning4j_trainer.flowable

import com.valb3r.deeplearning4j_trainer.repository.ProcessRepository
import org.flowable.engine.delegate.DelegateExecution
import org.springframework.stereotype.Service

@Service("processCompletion")
class ProcessCompletion(private val processRepo: ProcessRepository): WrappedJavaDelegate() {

    override fun doExecute(execution: DelegateExecution) {
        val proc = processRepo.findByProcessId(execution.processInstanceId) ?: return
        proc.completed = true
        processRepo.save(proc)
    }
}