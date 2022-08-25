package com.valb3r.deeplearning4j_trainer.flowable

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.valb3r.deeplearning4j_trainer.storage.Storage
import com.valb3r.deeplearning4j_trainer.storage.resolve
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

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