package com.valb3r.deeplearning4j_trainer.domain

import javax.persistence.*

@Entity
class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var name: String? = null

    @OneToOne
    var process: Process<*>? = null

    @OneToMany(mappedBy = "dataset", cascade = [CascadeType.ALL])
    var files: List<DatasetFile>? = null
}