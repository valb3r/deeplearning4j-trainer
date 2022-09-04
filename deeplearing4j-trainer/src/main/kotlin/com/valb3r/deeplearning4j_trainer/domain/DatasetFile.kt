package com.valb3r.deeplearning4j_trainer.domain

import javax.persistence.*

@Entity
class DatasetFile {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE)
    @SequenceGenerator(name = SEQUENCE, sequenceName = SEQUENCE, allocationSize = 50)
    var id: Long? = null

    var path: String? = null
    var origHash: String? = null

    @ManyToOne
    var dataset: Dataset? = null
}