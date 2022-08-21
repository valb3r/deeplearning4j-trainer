package com.valb3r.deeplearning4j_trainer.storage

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.*
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import kotlin.math.min

// Amazon S3 has maximum chunks of up to 5GB, max chunk count is 10_000
const val BUFFER_SIZE = 1024 * 1024 * 100 // max upload size is 1Tb in 100Mb chunks

class MultipartS3OutputStream(
    private val bucketName: String,
    private val objectName: String,
    private val amazonS3: AmazonS3,
    executorService: ExecutorService?
) : OutputStream() {

    private val completionService: CompletionService<UploadPartResult>
    private var currentOutputStream: CustomizableByteArrayOutputStream? = newOutputStream()
    private var multiPartUploadResult: InitiateMultipartUploadResult? = null
    private var partCounter = 1

    init {
        completionService = ExecutorCompletionService(executorService)
    }

    @Synchronized
    override fun write(bytes: ByteArray, off: Int, len: Int) {
        var remainingSizeToWrite = len
        var inputPosition = off
        do {
            val availableCapacity = BUFFER_SIZE - currentOutputStream!!.size()
            val bytesToWrite = min(availableCapacity, remainingSizeToWrite)
            currentOutputStream!!.write(bytes, inputPosition, bytesToWrite)
            inputPosition += bytesToWrite
            remainingSizeToWrite -= bytesToWrite
            initiateMultipartRequestAndCommitPartIfNeeded()
        } while (remainingSizeToWrite > 0)
    }

    @Synchronized
    override fun write(b: Int) {
        currentOutputStream!!.write(b)
        initiateMultipartRequestAndCommitPartIfNeeded()
    }

    @Synchronized
    override fun close() {
        if (currentOutputStream == null) {
            return
        }
        if (isMultiPartUpload) {
            finishMultiPartUpload()
        } else {
            finishSimpleUpload()
        }
    }

    private fun initiateMultipartRequestAndCommitPartIfNeeded() {
        if (currentOutputStream!!.size() != BUFFER_SIZE) {
            return
        }

        initiateMultiPartIfNeeded()
        val content = currentOutputStream!!.bufferOrCopy
        val size = currentOutputStream!!.size()

        currentOutputStream = newOutputStream()
        completionService.submit {
            amazonS3.uploadPart(
                UploadPartRequest()
                    .withBucketName(bucketName)
                    .withKey(objectName)
                    .withUploadId(multiPartUploadResult!!.uploadId)
                    .withInputStream(ByteArrayInputStream(content))
                    .withPartNumber(partCounter)
                    .withLastPart(false)
                    .withPartSize(size.toLong())
            )
        }
        ++partCounter
    }

    private val isMultiPartUpload: Boolean
        get() = multiPartUploadResult != null

    private fun finishSimpleUpload() {
        val objectMetadata = ObjectMetadata()
        val size = currentOutputStream!!.size()
        objectMetadata.contentLength = size.toLong()
        val content = currentOutputStream!!.bufferOrCopy

        currentOutputStream = null
        amazonS3.putObject(
            bucketName,
            objectName,
            ByteArrayInputStream(content, 0, size),
            objectMetadata
        )
    }

    private fun finishMultiPartUpload() {
        sendLastChunkIfNeeded()
        try {
            val partETags = multiPartsUploads
            amazonS3.completeMultipartUpload(
                CompleteMultipartUploadRequest(
                    multiPartUploadResult!!.bucketName,
                    multiPartUploadResult!!.key,
                    multiPartUploadResult!!.uploadId,
                    partETags
                )
            )
        } catch (e: ExecutionException) {
            abortMultiPartUpload()
        } catch (e: InterruptedException) {
            abortMultiPartUpload()
            Thread.currentThread().interrupt()
        } finally {
            currentOutputStream = null
        }
    }

    private fun sendLastChunkIfNeeded() {
        // empty file can be created only using simple upload:
        if (currentOutputStream!!.size() == 0) {
            partCounter--
            return
        }

        val content = currentOutputStream!!.bufferOrCopy
        val size = currentOutputStream!!.size()

        currentOutputStream = null
        completionService.submit {
            amazonS3.uploadPart(
                UploadPartRequest()
                    .withBucketName(bucketName)
                    .withKey(objectName)
                    .withUploadId(multiPartUploadResult!!.uploadId)
                    .withInputStream(ByteArrayInputStream(content))
                    .withPartNumber(partCounter)
                    .withLastPart(true)
                    .withPartSize(size.toLong())
            )
        }
    }

    private fun initiateMultiPartIfNeeded() {
        if (multiPartUploadResult != null) {
            return
        }

        multiPartUploadResult = amazonS3.initiateMultipartUpload(InitiateMultipartUploadRequest(bucketName, objectName))
    }

    private fun abortMultiPartUpload() {
        if (!isMultiPartUpload) {
            return
        }

        amazonS3.abortMultipartUpload(
            AbortMultipartUploadRequest(
                multiPartUploadResult!!.bucketName,
                multiPartUploadResult!!.key,
                multiPartUploadResult!!.uploadId
            )
        )
    }

    private val multiPartsUploads: List<PartETag>
        get() {
            val result: MutableList<PartETag> = ArrayList(partCounter)
            for (i in 0 until partCounter) {
                val partResult = completionService.take().get()
                result.add(partResult.partETag)
            }
            return result
        }

    private fun newOutputStream(): CustomizableByteArrayOutputStream {
        return CustomizableByteArrayOutputStream(32, BUFFER_SIZE, 0.5)
    }

    class CustomizableByteArrayOutputStream(
        initialCapacity: Int,
        maxArraySize: Int,
        fillFactorToCopy: Double
    ) : OutputStream() {

        private val fillFactorToCopy: Double
        private val maxArraySize: Int
        private val initialCapacity: Int
        private var buffer: ByteArray
        private var count = 0

        init {
            require(initialCapacity > 0) { "Initial capacity must positive: $initialCapacity" }
            require(maxArraySize > 0) { "Max array size must be positive: $maxArraySize" }
            require(!(fillFactorToCopy < 0 || fillFactorToCopy > 1.0)) { "Fill factor must be in [0, 1]: $fillFactorToCopy" }
            this.initialCapacity = initialCapacity
            buffer = ByteArray(this.initialCapacity)
            this.maxArraySize = maxArraySize
            this.fillFactorToCopy = fillFactorToCopy
        }

        @Synchronized
        override fun write(byteToWrite: Int) {
            ensureCapacity(count + 1)
            buffer[count] = byteToWrite.toByte()
            count += 1
        }

        @Synchronized
        override fun write(buffer: ByteArray, off: Int, len: Int) {
            if (off < 0 || off > buffer.size || len < 0 || off + len - buffer.size > 0) {
                throw IndexOutOfBoundsException()
            }
            ensureCapacity(count + len)
            System.arraycopy(buffer, off, this.buffer, count, len)
            count += len
        }

        @get:Synchronized
        val bufferOrCopy: ByteArray
            get() {
                val fillFactor = count.toDouble() / buffer.size
                return if (fillFactor >= fillFactorToCopy) {
                    buffer
                } else buffer.copyOf(count)
            }

        @Synchronized
        fun size(): Int {
            return count
        }

        override fun close() {
            // NOP
        }

        private fun ensureCapacity(minCapacity: Int) {
            if (minCapacity <= buffer.size) {
                return
            }
            grow(minCapacity)
        }

        private fun grow(minCapacity: Int) {
            val oldCapacity = buffer.size
            var newCapacity = min(oldCapacity shl 1, maxArraySize)

            if (newCapacity < minCapacity) {
                newCapacity = minCapacity
            }

            if (newCapacity > maxArraySize) {
                throw OutOfMemoryError()
            }

            buffer = buffer.copyOf(newCapacity)
        }
    }
}