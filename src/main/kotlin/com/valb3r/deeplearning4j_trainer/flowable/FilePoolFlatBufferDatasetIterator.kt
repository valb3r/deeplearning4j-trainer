package com.valb3r.deeplearning4j_trainer.flowable

import com.valb3r.deeplearning4j_trainer.storage.Storage
import org.nd4j.linalg.dataset.api.MultiDataSet
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator
import org.nd4j.linalg.factory.Nd4j


class FilePoolFlatBufferDatasetIterator(
    private val storage: Storage,
    private val batchSize: Int,
    private val featureNames: List<String>,
    private val labelNames: List<String>,
    dataFiles: List<String>,
    private var binIter: FstSerDe.FstIterator? = null,
    private var dataFilePool: MutableSet<String> = linkedSetOf(*dataFiles.toTypedArray()),
    private var fetchedSize: Long = 0,
    private var resultSetIdName: String? = null,
    var resultSetIds: MutableList<Float>? = null,
    var computedDatasetSize: Long = 0L
): MultiDataSetIterator {

    override fun hasNext(): Boolean {
        if (noBinIterOrEmpty()) {
            if (dataFilePool.isEmpty()) { // No data left
                return false
            }
        }

        return true
    }

    override fun next(num: Int): MultiDataSet {
        val features = mutableMapOf<String, MutableList<FloatArray>>()
        val labels = mutableMapOf<String, MutableList<FloatArray>>()
        for (ind in 0 until num) {
            if (!hasNext()) {
                break
            }
            if (noBinIterOrEmpty()) {
                val file = dataFilePool.first()
                dataFilePool.remove(file)
                binIter = FstSerDe.FstIterator(file, storage)
            }

            val entry = binIter!!.next()

            if (null != resultSetIdName) {
                if (null == resultSetIds) {
                    resultSetIds = ArrayList()
                }

                resultSetIds!!.add(entry[resultSetIdName!!]!![0])
            }

            featureNames.forEach { name ->
                entry[name]?.let { vals ->
                    features.computeIfAbsent(name) { mutableListOf() }.add(vals)
                }
            }
            labelNames.forEach { name ->
                entry[name]?.let { vals ->
                    labels.computeIfAbsent(name) { mutableListOf() }.add(vals)
                }
            }
            computedDatasetSize++
        }

        if (features.isEmpty()) {
            throw IllegalArgumentException("No features were read")
        }

        if (labels.isEmpty()) {
            throw IllegalArgumentException("No labels were read")
        }

        fetchedSize += num

        return org.nd4j.linalg.dataset.MultiDataSet(
            featureNames.map { features[it]!! }.map { Nd4j.create(it.toTypedArray()) }.toTypedArray(),
            labelNames.map { labels[it]!! }.map { Nd4j.create(it.toTypedArray()) }.toTypedArray(),
        )
    }

    override fun next(): MultiDataSet {
        return next(batchSize)
    }

    fun skipNext() {
        if (noBinIterOrEmpty()) {
            if (dataFilePool.isEmpty()) { // No data left
                throw IllegalArgumentException("Empty")
            }
            val file = dataFilePool.first()
            dataFilePool.remove(file)
            binIter = FstSerDe.FstIterator(file, storage)
        }

        binIter!!.skipNext()
    }

    private fun noBinIterOrEmpty() = null == binIter || !binIter!!.hasNext()

    override fun remove() {
        TODO("Not yet implemented")
    }

    override fun setPreProcessor(preProcessor: MultiDataSetPreProcessor?) {
        TODO("Not yet implemented")
    }

    override fun getPreProcessor(): MultiDataSetPreProcessor {
        TODO("Not yet implemented")
    }

    override fun resetSupported(): Boolean {
        return false
    }

    override fun asyncSupported(): Boolean {
        return false
    }

    override fun reset() {
        TODO("Not yet implemented")
    }
}