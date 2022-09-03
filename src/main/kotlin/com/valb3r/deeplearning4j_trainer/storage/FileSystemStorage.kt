package com.valb3r.deeplearning4j_trainer.storage

import org.springframework.stereotype.Service
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.stream.Collectors

private const val FILE_PROTO = "file://"

private const val BUFF_SIZE = 10 * 1024 * 1024

@Service
class FileSystemStorage: StorageSystem {

    override fun supports(path: String): Boolean {
        return path.startsWith(FILE_PROTO)
    }

    override fun list(path: String, recursively: Boolean): List<String> {
        if (recursively) {
            return Files.walk(path.asFile().toPath()).map { "$FILE_PROTO$it" }.collect(Collectors.toList())
        }

        return path.asFile().listFiles()?.map { "$FILE_PROTO$it" } ?: emptyList()
    }

    override fun read(path: String): InputStream {
        return BufferedInputStream(path.asFile().inputStream(), BUFF_SIZE)
    }

    override fun write(path: String): OutputStream {
        if (!path.asFile().parentFile.exists()) {
            path.asFile().parentFile.mkdirs()
        }
        
        return BufferedOutputStream(path.asFile().outputStream(), BUFF_SIZE)
    }

    override fun move(from: String, to: String): Boolean {
        return from.asFile().renameTo(to.asFile())
    }

    override fun exists(path: String): Boolean {
        return path.asFile().exists()
    }

    override fun remove(path: String): Boolean {
        return path.asFile().delete()
    }

    private fun String.asFile(): File {
        return File(this.substring(FILE_PROTO.length))
    }
}