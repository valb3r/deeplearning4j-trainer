package com.valb3r.deeplearning4j_trainer.storage

import com.valb3r.deeplearning4j_trainer.config.S3Config
import com.valb3r.deeplearning4j_trainer.storage.interfaces.StorageSystem
import com.valb3r.deeplearning4j_trainer.storage.s3.S3Storage
import org.springframework.stereotype.Service
import java.io.InputStream
import java.io.OutputStream


@Service
class S3SystemStorage(conf: S3Config): StorageSystem {

    private val s3SystemStorage: S3Storage

    init {
        s3SystemStorage = S3Storage(conf.toS3Config())
    }

    override fun list(path: String, recursively: Boolean): List<String> {
        return s3SystemStorage.list(path, recursively)
    }

    override fun read(path: String): InputStream {
        return s3SystemStorage.read(path)
    }

    override fun write(path: String): OutputStream {
        return s3SystemStorage.write(path)
    }

    override fun move(from: String, to: String): Boolean {
        return s3SystemStorage.move(from, to)
    }

    override fun exists(path: String): Boolean {
        return s3SystemStorage.exists(path)
    }

    override fun remove(path: String): Boolean {
        return s3SystemStorage.remove(path)
    }

    override fun supports(path: String): Boolean {
        return s3SystemStorage.supports(path)
    }
}