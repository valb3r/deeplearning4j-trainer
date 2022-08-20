package com.valb3r.deeplearning4j_trainer.flowable

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

fun extractZipFiles(inputFolder: File) {
    val zipFiles = inputFolder.listFiles()!!.map { it.absolutePath }.filter { it.endsWith(".zip") }
    for (zipFile in zipFiles) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            while (true) {
                val zipEntry: ZipEntry = zis.nextEntry ?: break
                if (zipEntry.isDirectory) {
                    throw IllegalArgumentException("Directories are not allowed in ZIP files, found ${zipEntry.name} in $zipFile")
                }

                val buffer = ByteArray(1024)
                FileOutputStream(inputFolder.resolve(zipEntry.name)).use { fos ->
                    while (true) {
                        val len = zis.read(buffer)
                        if (len <= 0) break
                        fos.write(buffer, 0, len)
                    }
                }
                zis.closeEntry()
            }
        }

        File(zipFile).delete()
    }
}