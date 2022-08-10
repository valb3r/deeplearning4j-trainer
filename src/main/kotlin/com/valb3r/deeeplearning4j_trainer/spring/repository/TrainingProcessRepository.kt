package com.valb3r.deeeplearning4j_trainer.spring.repository

import com.valb3r.deeeplearning4j_trainer.spring.domain.TrainingProcess
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.CrudRepository
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import org.springframework.stereotype.Repository

@Repository
@RepositoryRestResource
interface TrainingProcessRepository: CrudRepository<TrainingProcess, Long> {

    fun findByProcessId(processId: String): TrainingProcess?
    fun findAllByOrderByUpdatedAtDesc(page: Pageable): List<TrainingProcess>
}