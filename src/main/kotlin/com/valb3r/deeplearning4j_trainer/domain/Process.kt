package com.valb3r.deeplearning4j_trainer.domain

import com.valb3r.deeplearning4j_trainer.flowable.dto.Context
import com.valb3r.deeplearning4j_trainer.flowable.dto.InputContext
import org.hibernate.annotations.UpdateTimestamp
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.persistence.*
import javax.persistence.Transient

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
abstract class Process<out T : Context>  {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE)
    @SequenceGenerator(name = SEQUENCE, sequenceName = SEQUENCE, allocationSize = 50)
    open var id: Long? = null

    open var trainedModelPath: String = "N/A"
    open var businessKey: String = "N/A"
    open var processDefinitionName: String = "N/A"

    @Column(unique = true)
    open var processId: String = "N/A"

    @Lob
    open var errorStacktrace: ByteArray? = null

    open var errorMessage: String? = null
    open var notes: String? = null
    open var completed = false

    open var currentEpoch: Long? = null
    open var bestPerformingEpoch: Long? = null
    open var bestPerformingTrainedModelPath: String? = null

    @UpdateTimestamp
    open var updatedAt: Instant = Instant.EPOCH

    @OneToOne(mappedBy = "process", cascade = [CascadeType.ALL])
    open var dataset: Dataset? = null

    @Lob
    open var inputCtx: ByteArray? = null
    open var forceStop: Boolean = false // FIXME - It is a hack, should fix flowable cancellation properly

    abstract fun getCtx(): T?

    @Transient
    fun inputCtx(): InputContext? {
        return inputCtx?.let { domainCtxMapper.readValue(it, InputContext::class.java) }
    }

    @Transient
    fun modelPath(latest: Boolean): String {
        if (latest) {
            return trainedModelPath
        }

        return bestPerformingTrainedModelPath!!
    }

    @Transient
    fun getRevertedStacktrace(): String? {
        return errorStacktrace?.let { String(it, StandardCharsets.UTF_8) }?.lines()?.reversed()?.joinToString("\n")
    }
}