package com.valb3r.deeplearning4j_trainer.flowable

import com.valb3r.deeplearning4j_trainer.domain.Process
import com.valb3r.deeplearning4j_trainer.flowable.dto.Context
import com.valb3r.deeplearning4j_trainer.repository.ProcessRepository
import mu.KotlinLogging
import org.flowable.engine.ManagementService
import org.flowable.engine.delegate.BpmnError
import org.flowable.engine.delegate.DelegateExecution
import org.flowable.engine.delegate.JavaDelegate
import org.flowable.spring.SpringProcessEngineConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionOperations
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManager
import javax.persistence.TemporalType

private val logger = KotlinLogging.logger {}
private val activeExecutionsForHeartbeat = ConcurrentHashMap.newKeySet<String>()

abstract class WrappedJavaDelegate: JavaDelegate {

    @Autowired var processRepository: ProcessRepository? = null
    @Autowired var txOper: TransactionOperations? = null

    override fun execute(execution: DelegateExecution) {
        val name = Thread.currentThread().name
        val proc = processRepository!!.findByProcessId(execution.processInstanceId)

        try {
            Thread.currentThread().name = execution.procName()
            activeExecutionsForHeartbeat.add(execution.id)
            logger.info { "Executing step ${this.javaClass.simpleName} of ${execution.processInstanceId}" }
            handleForceStopIfNeeded(proc)
            doExecute(execution)
            logger.info { "Done executing step ${this.javaClass.simpleName}" }
        } catch (ex: BpmnError) {
            logger.error(ex) { "Execution failed" }
            completeWithError(execution, ex)
            throw ex
        } catch (ex: Throwable) {
            logger.error(ex) { "Execution failed" }
            completeWithError(execution, ex)
            throw BpmnError("INPUT_ERR")
        } finally {
            Thread.currentThread().name = name
            activeExecutionsForHeartbeat.remove(execution.id)
        }

        logger.info { "Wrapper done" }
    }

    fun DelegateExecution.procName(): String {
        if (null != processInstanceBusinessKey) {
            return "$id - $processInstanceBusinessKey"
        }

        return id
    }

    private fun handleForceStopIfNeeded(proc: Process<Context>?) {
        if (true != proc?.forceStop) {
            return
        }

        txOper!!.executeWithoutResult {
            proc.forceStop = false
            processRepository!!.save(proc)
        }
        throw IllegalArgumentException("Forced stop")
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
            logger.error(ex) { "Failed ${ex.message}" }
        }
    }

    abstract fun doExecute(execution: DelegateExecution)
}

@Service
class HeartbeatLeaseExtender(
    private val manager: ManagementService,
    private val config: SpringProcessEngineConfiguration,
    private val em: EntityManager
) {
    @Scheduled(fixedDelayString = "\${flowable-executor.heartbeat.extend-schedule:PT1M}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun extendLockLease() {
        val leaseTime = config.asyncExecutor.asyncJobLockTimeInMillis
        for (execId in activeExecutionsForHeartbeat) {
            logger.info { "Extending lease for $execId" }
            try {
                val jobs = manager.createJobQuery().executionId(execId).locked().list()
                jobs.forEach {
                    val query = em.createNativeQuery("UPDATE act_ru_job SET lock_exp_time_ = ?2 WHERE id_= ?1 AND lock_exp_time_ IS NOT NULL")
                    query.setParameter(1, it.id)
                    val gregorianCalendar = GregorianCalendar()
                    gregorianCalendar.time = config.clock.currentTime
                    gregorianCalendar.add(Calendar.MILLISECOND, leaseTime)
                    query.setParameter(2, gregorianCalendar.time, TemporalType.TIMESTAMP)
                    query.executeUpdate()
                }
                logger.info { "Extended lease for $execId for ${leaseTime}ms" }
            } catch (ex: Throwable) {
                logger.error(ex) { "Failed updating lease for $execId" }
            }
        }
    }
}