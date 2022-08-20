package com.valb3r.deeplearning4j_trainer.domain

import javax.persistence.*

@Entity
class DatasetFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var path: String? = null
    var origHash: String? = null

    @ManyToOne
    var dataset: Dataset? = null
}