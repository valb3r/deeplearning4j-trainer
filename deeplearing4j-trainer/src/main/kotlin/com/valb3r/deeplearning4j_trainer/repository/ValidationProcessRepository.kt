package com.valb3r.deeplearning4j_trainer.repository

import com.valb3r.deeplearning4j_trainer.domain.ValidationProcess
import org.springframework.data.repository.CrudRepository
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import org.springframework.stereotype.Repository

@Repository
@RepositoryRestResource
interface ValidationProcessRepository: CrudRepository<ValidationProcess, Long> {

    fun findByProcessId(processId: String): ValidationProcess?
}