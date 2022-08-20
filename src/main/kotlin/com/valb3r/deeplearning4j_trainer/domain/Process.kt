package com.valb3r.deeplearning4j_trainer.domain

import com.valb3r.deeplearning4j_trainer.flowable.dto.Context
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import javax.persistence.*

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
abstract class Process<out T : Context>  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    open var trainedModelPath: String = "N/A"
    open var businessKey: String = "N/A"
    open var processDefinitionName: String = "N/A"

    @Column(unique = true)
    open var processId: String = "N/A"

    @Lob
    open var errorStacktrace: String? = null
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

    abstract fun getCtx(): T?

    fun modelPath(latest: Boolean): String {
        if (latest) {
            return trainedModelPath
        }

        return bestPerformingTrainedModelPath!!
    }
}