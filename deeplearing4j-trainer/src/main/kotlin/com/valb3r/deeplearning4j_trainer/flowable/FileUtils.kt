package com.valb3r.deeplearning4j_trainer.flowable

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.valb3r.deeplearning4j_trainer.classloaders.DynamicClassLoader
import com.valb3r.deeplearning4j_trainer.config.AppContext
import com.valb3r.deeplearning4j_trainer.config.S3Config
import com.valb3r.deeplearning4j_trainer.flowable.serde.FstSerDe
import com.valb3r.deeplearning4j_trainer.s3_urlconnection_adapter.s3.Handler.Companion.encodeS3CredentialsToUrl
import com.valb3r.deeplearning4j_trainer.storage.interfaces.Storage
import com.valb3r.deeplearning4j_trainer.storage.resolve
import com.valb3r.deeplearning4j_trainer.storage.s3.isS3
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

fun String.isBinDataFile(): Boolean {
    return this.endsWith(".csv.data.bin")
}

fun String.isJarDataFile(): Boolean {
    return this.endsWith(".bin.jar")
}

fun String.isCsvDataFile(): Boolean {
    return this.endsWith(".csv")
}

fun String.asJarloadClass(clazz: String) {
    val loader = DynamicClassLoader.INSTANCE
    val url = if (this.isS3()) {
        this.encodeS3CredentialsToUrl(AppContext.CONTEXT.getBean(S3Config::class.java).toS3Config())
    } else {
        this
    }

    loader.add(URL(url))
    Class.forName(clazz, true, loader)
}


fun extractZipFilesAndDeleteArch(inputFolder: String, storage: Storage) {
    val zipFiles = storage.list(inputFolder).filter { it.endsWith(".zip") }
    for (zipFile in zipFiles) {
        ZipInputStream(storage.read(zipFile)).use { zis ->
            while (true) {
                val zipEntry: ZipEntry = zis.nextEntry ?: break
                if (zipEntry.isDirectory) {
                    throw IllegalArgumentException("Directories are not allowed in ZIP files, found ${zipEntry.name} in $zipFile")
                }

                val buffer = ByteArray(1024_00)
                storage.write(inputFolder.resolve(zipEntry.name)).use { fos ->
                    while (true) {
                        val len = zis.read(buffer)
                        if (len <= 0) break
                        fos.write(buffer, 0, len)
                    }
                }
                zis.closeEntry()
            }
        }

        storage.remove(zipFile)
    }
}

fun csvToBinAndRemoveSrc(
    file: String,
    mapper: CsvMapper,
    result: MutableList<String>,
    storage: Storage
): Long {
    val parsed = FstSerDe().csvToBin(file, ".data.bin", mapper, storage)
    result += parsed.fstFileName
    storage.remove(file)
    return parsed.numRows.toLong()
}