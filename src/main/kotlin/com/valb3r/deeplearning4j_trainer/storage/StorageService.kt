package com.valb3r.deeplearning4j_trainer.storage

import org.apache.commons.io.IOUtils
import org.aspectj.weaver.tools.cache.SimpleCacheFactory.path
import org.springframework.stereotype.Service
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

@Service
class StorageService(private val storages: List<StorageSystem>): Storage {

    override fun list(path: String, recursively: Boolean): List<String> {
        return path.storage().list(path, recursively)
    }

    override fun read(path: String): InputStream {
        return path.storage().read(path)
    }

    override fun write(path: String): OutputStream {
        return path.storage().write(path)
    }

    override fun move(from: String, to: String): Boolean {
        return from.storage().move(from, to)
    }

    override fun exists(path: String): Boolean {
        return path.storage().exists(path)
    }

    override fun remove(path: String): Boolean {
        return path.storage().remove(path)
    }

    private fun String.storage(): StorageSystem {
        return storages.first { it.supports(this) }
    }
}

fun String.resolve(other: String): String {
    var name = other
    if (name.startsWith(".")) {
        throw IllegalArgumentException("Dot paths not allowed")
    }

    if (name.contains("://")) {
        name = name.split("/").last()
    }

    if (this.endsWith("/")) {
        return "$this$name"
    }

    return "$this/$name"
}

fun InputStream.wrapToByteBuffer(): ByteBuffer {
    var bytes: ByteArray?
    BufferedInputStream(this).use {
        bytes = IOUtils.toByteArray(it)
    }

    return ByteBuffer.wrap(bytes)
}

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