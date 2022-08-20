package com.valb3r.deeplearning4j_trainer.flowable

import com.valb3r.deeplearning4j_trainer.repository.ProcessRepository
import mu.KotlinLogging
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.flowable.engine.delegate.JavaDelegate
import org.springframework.beans.factory.annotation.Autowired
import java.nio.charset.StandardCharsets.UTF_8

private val logger = KotlinLogging.logger {}
abstract class WrappedJavaDelegate: JavaDelegate {

    @Autowired var processRepository: ProcessRepository? = null

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
            throw BpmnError("INPUT_ERR")
        }
    }

    private fun completeWithError(execution: DelegateExecution, ex: Throwable) {
        try {
            val procInstance = processRepository!!.findByProcessId(execution.processInstanceId)!!
            procInstance.errorMessage = ex.message ?: "${ex.javaClass}: null"
            procInstance.completed = true
            procInstance.errorStacktrace = ex.stackTraceToString().toByteArray(UTF_8)
            processRepository!!.save(procInstance)
        } catch (ex: Throwable) {
            // NOP
        }
    }

    abstract fun doExecute(execution: DelegateExecution)
}