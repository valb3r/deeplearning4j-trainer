package com.valb3r.deeplearning4j_trainer.flowable.serde

import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvParser
import com.valb3r.deeplearning4j_trainer.storage.Storage
import java.io.Closeable
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun bigEndianBuff(sz: Int): ByteBuffer {
    val buff = ByteBuffer.allocate(sz)
    buff.order(ByteOrder.BIG_ENDIAN)
    return buff
}

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
                    fof.write(bigEndianBuff(Int.SIZE_BYTES).putInt(row.size).array())
                    row.forEach { headerName ->
                        val bytes = headerName.encodeToByteArray()
                        fof.write(bigEndianBuff(Int.SIZE_BYTES).putInt(bytes.size).array())
                        fof.write(bytes)
                    }
                    headerWritten = true
                } else {
                    row.forEach { data ->
                        val floatVals = data.split(";").map { it.toFloat() }
                        fof.write(bigEndianBuff(Int.SIZE_BYTES).putInt(floatVals.size).array())
                        val byteFloatBuf = bigEndianBuff(Float.SIZE_BYTES * floatVals.size)
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

    class FstIterator(file: String, storage: Storage): DataIterator {

        private val fIf = storage.read(file)
        private val headerNames: List<String>
        private var vectorSize: Int = 0

        init {
            val headers = mutableListOf<String>()
            val numHeadersBytes = bigEndianBuff(Int.SIZE_BYTES)
            readExact(numHeadersBytes.array())
            (0 until numHeadersBytes.int).forEach { _ ->
                val headerNameLenBytes = bigEndianBuff(Int.SIZE_BYTES)
                readExact(headerNameLenBytes.array())
                val headerNameBytes = bigEndianBuff(headerNameLenBytes.int)
                readExact(headerNameBytes.array())
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
                val vectorBytes = bigEndianBuff(vectorSize * Float.SIZE_BYTES)
                readExact(vectorBytes.array())
                val buf = vectorBytes.asFloatBuffer()
                result[name] = (0 until vectorSize).map { buf.get() }.toFloatArray()
                vectorSize = readVectorSize()
            }
            return result
        }

        override fun skipNext() {
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
            val vectorSizeBytes = bigEndianBuff(Int.SIZE_BYTES)
            if (readExact(vectorSizeBytes.array()) < 0) {
                return -1
            }
            return vectorSizeBytes.int
        }

        private fun readExact(buff: ByteArray): Int {
            var capacity = buff.size
            var off = 0
            while (capacity > 0) {
                val bytesRead = fIf.read(buff, off, capacity)
                if (bytesRead < 0) {
                    return -1
                }
                capacity -= bytesRead
                off += bytesRead
            }
            return buff.size
        }
    }
}

class JarIterator(
    integrationClazz: String,
    params: Map<String, String>,
): DataIterator {

    private val clazz: Class<*> = Class.forName(integrationClazz, true, ClassLoader.getSystemClassLoader())
    private val clazzInstance = clazz.getConstructor(Map::class.java).newInstance(params)

    private val hasNextI: Method = clazz.getDeclaredMethod("hasNext")
    private val nextI: Method = clazz.getDeclaredMethod("next")

    override fun hasNext(): Boolean {
        return hasNextI.invoke(clazzInstance) as Boolean
    }

    override fun next(): Map<String, FloatArray> {
        return nextI.invoke(clazzInstance) as Map<String, FloatArray>
    }

    override fun skipNext() {
        next()
    }

    override fun close() {
        // NOP
    }
}

interface DataIterator: Iterator<Map<String, FloatArray>>, Closeable {

    fun skipNext()
}