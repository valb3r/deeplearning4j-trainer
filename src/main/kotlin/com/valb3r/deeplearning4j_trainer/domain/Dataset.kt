package com.valb3r.deeplearning4j_trainer.domain

import javax.persistence.*

@Entity
class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE)
    @SequenceGenerator(name = SEQUENCE, sequenceName = SEQUENCE, allocationSize = 50)
    var id: Long? = null

    var name: String? = null

    @OneToOne
    var process: Process<*>? = null

    @OneToMany(mappedBy = "dataset", cascade = [CascadeType.ALL])
    var files: List<DatasetFile>? = null
}