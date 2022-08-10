package com.valb3r.deeeplearning4j_trainer.spring.flowable

import com.valb3r.deeeplearning4j_trainer.spring.repository.TrainingProcessRepository
import mu.KotlinLogging
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.flowable.engine.delegate.JavaDelegate
import org.springframework.beans.factory.annotation.Autowired

private val logger = KotlinLogging.logger {}
abstract class WrappedJavaDelegate: JavaDelegate {

    @Autowired var trainingRepository: TrainingProcessRepository? = null

    override fun execute(execution: DelegateExecution) {
        try {
            doExecute(execution)
        } catch (ex: BpmnError) {
            logger.error(ex) { "Execution failed" }
            completeWithError(execution, ex)
            throw ex
        } catch (ex: Throwable) {
            logger.error(ex) { "Execution failed" }
            completeWithError(execution, ex)
            execution.updateWithErrorAndThrow(BpmnError("GENERIC_ERR", "${ex.javaClass.simpleName}: ${ex.message?.take(64)}"))
        }
    }

    private fun completeWithError(execution: DelegateExecution, ex: Throwable) {
        try {
            val procInstance = trainingRepository!!.findByProcessId(execution.processInstanceId)!!
            procInstance.errorMessage = ex.message ?: "${ex.javaClass}: null"
            procInstance.completed = true
            procInstance.errorStacktrace = ex.stackTraceToString()
            trainingRepository!!.save(procInstance)
        } catch (ex: Throwable) {
            // NOP
        }
    }

    abstract fun doExecute(execution: DelegateExecution)
}