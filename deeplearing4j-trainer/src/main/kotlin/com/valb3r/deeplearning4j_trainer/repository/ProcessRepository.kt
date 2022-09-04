package com.valb3r.deeplearning4j_trainer.repository

import com.valb3r.deeplearning4j_trainer.flowable.dto.Context
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ProcessRepository: CrudRepository<com.valb3r.deeplearning4j_trainer.domain.Process<Context>, Long> {

    fun findByProcessId(processId: String): com.valb3r.deeplearning4j_trainer.domain.Process<Context>?
    fun findAllByOrderByUpdatedAtDesc(page: Pageable): List<com.valb3r.deeplearning4j_trainer.domain.Process<Context>>
}
