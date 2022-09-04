package com.valb3r.deeplearning4j_trainer.storage.interfaces

import java.io.InputStream
import java.io.OutputStream

interface Storage {

    fun list(path: String, recursively: Boolean = true): List<String>
    fun read(path: String): InputStream
    fun write(path: String): OutputStream

    /**
     * Move within 1 filesystem (Note - might not be atomic!)
     * Also, for S3 it is atomic only up to 5Gb size.
     */
    fun move(from: String, to: String): Boolean

    fun exists(path: String): Boolean
    fun remove(path: String): Boolean
}

interface StorageSystem: Storage {

    fun supports(path: String): Boolean
}