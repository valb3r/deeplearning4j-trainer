package com.valb3r.deeplearning4j_trainer.flowable

import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvParser
import com.valb3r.deeplearning4j_trainer.storage.Storage
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer


class FstSerDe {

    // Writes as:
    // size(int),headerName1
    // size(int),headerName2
    // size(int),vectorFloat[data of field 1],
    // size(int),vectorFloat[data of field 2]
    fun csvToBin(file: String, suffix: String, mapper: CsvMapper, storage: Storage): SerOutp {
        val fstFileName = "$file$suffix"
        var count = 0
        storage.write(fstFileName).use { fof ->
            val iter: MappingIterator<List<String>> = mapper.readerForListOf(String::class.java)
                .with(CsvParser.Feature.WRAP_AS_ARRAY)
                .readValues(storage.read(file))
            var headerWritten = false
            iter.forEachRemaining { row ->
                if (!headerWritten) {
                    fof.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(row.size).array())
                    row.forEach { headerName ->
                        val bytes = headerName.encodeToByteArray()
                        fof.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                        fof.write(bytes)
                    }
                    headerWritten = true
                } else {
                    row.forEach { data ->
                        val floatVals = data.split(";").map { it.toFloat() }
                        fof.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(floatVals.size).array())
                        val byteFloatBuf = ByteBuffer.allocate(Float.SIZE_BYTES * floatVals.size)
                        val floatBuf = byteFloatBuf.asFloatBuffer()
                        floatVals.forEach { floatBuf.put(it) }
                        fof.write(byteFloatBuf.array())
                    }
                    count++
                }
            }
        }

        return SerOutp(fstFileName, count)
    }

    data class SerOutp(val fstFileName: String, val numRows: Int)

    class FstIterator(file: String, storage: Storage): Iterator<Map<String, FloatArray>>, Closeable {

        private val fIf = storage.read(file)
        private val headerNames: List<String>
        private var vectorSize: Int = 0

        init {
            val headers = mutableListOf<String>()
            val numHeadersBytes = ByteBuffer.allocate(Int.SIZE_BYTES)
            fIf.read(numHeadersBytes.array())
            (0 until numHeadersBytes.int).forEach { _ ->
                val headerNameLenBytes = ByteBuffer.allocate(Int.SIZE_BYTES)
                fIf.read(headerNameLenBytes.array())
                val headerNameBytes = ByteBuffer.allocate(headerNameLenBytes.int)
                fIf.read(headerNameBytes.array())
                val headerName = headerNameBytes.array().decodeToString()
                headers.add(headerName)
            }

            headerNames = headers
        }

        override fun hasNext(): Boolean {
            if (vectorSize > 0) {
                return true
            }

            if (vectorSize < 0) {
                return false
            }

            vectorSize = readVectorSize()
            return vectorSize > 0
        }

        override fun next(): Map<String, FloatArray> {
            if (0 == vectorSize) {
                vectorSize = readVectorSize()
            }
            val result = mutableMapOf<String, FloatArray>()
            headerNames.forEach { name ->
                val vectorBytes = ByteBuffer.allocate(vectorSize * Float.SIZE_BYTES)
                fIf.read(vectorBytes.array())
                val buf = vectorBytes.asFloatBuffer()
                result[name] = (0 until vectorSize).map { buf.get() }.toFloatArray()
                vectorSize = readVectorSize()
            }
            return result
        }

        fun skipNext() {
            if (0 == vectorSize) {
                vectorSize = readVectorSize()
            }

            headerNames.forEach { name ->
                fIf.skip((vectorSize * Float.SIZE_BYTES).toLong())
                vectorSize = readVectorSize()
            }
        }

        override fun close() {
            fIf.close()
        }

        private fun readVectorSize(): Int {
            val vectorSizeBytes = ByteBuffer.allocate(Int.SIZE_BYTES)
            if (fIf.read(vectorSizeBytes.array()) < 0) {
                return -1
            }
            return vectorSizeBytes.int
        }
    }
}