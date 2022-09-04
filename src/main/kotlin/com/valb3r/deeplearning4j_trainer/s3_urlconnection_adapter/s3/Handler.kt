package com.valb3r.deeplearning4j_trainer.s3_urlconnection_adapter.s3

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.valb3r.deeplearning4j_trainer.config.S3Config
import com.valb3r.deeplearning4j_trainer.storage.S3SystemStorage
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Field
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.util.*

class S3UrlStreamHandlerFactory(private val originalFactory: URLStreamHandlerFactory?): URLStreamHandlerFactory {
    override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
        if (protocol == "s3") {
            return Handler()
        }

        return originalFactory?.createURLStreamHandler(protocol)
    }
}


// Name MUST BE Handler
open class Handler : URLStreamHandler() {

    @Throws(IOException::class)
    override fun openConnection(url: URL): URLConnection {
        return S3URLConnection(url)
    }

    class S3URLConnection(url: URL?) : URLConnection(url) {

        @Throws(IOException::class)
        override fun connect() {
            connected = true
        }

        override fun getInputStream(): InputStream {
            val url = url.toURI().toASCIIString()
            return S3SystemStorage(url.decodeS3CredentialsFromUrl()).read(url.decodeS3Url())
        }
    }

    companion object {

        fun registerV2() {
            val factoryField: Field = URL::class.java.getDeclaredField("factory")
            factoryField.setAccessible(true)
            val lockField: Field = URL::class.java.getDeclaredField("streamHandlerLock")
            lockField.setAccessible(true)

            // use same lock as in java.net.URL.setURLStreamHandlerFactory

            // use same lock as in java.net.URL.setURLStreamHandlerFactory
            synchronized(lockField.get(null)) {
                val urlStreamHandlerFactory = factoryField.get(null) as URLStreamHandlerFactory?
                // Reset the value to prevent Error due to a factory already defined
                factoryField.set(null, null)
                URL.setURLStreamHandlerFactory(S3UrlStreamHandlerFactory(urlStreamHandlerFactory))
            }
        }

        fun String.encodeS3CredentialsToUrl(config: S3Config): String {
            return "$this?c=${Base64.getUrlEncoder().encodeToString(ObjectMapper().registerModule(KotlinModule.Builder().build()).writeValueAsBytes(config))}"
        }

        fun String.decodeS3CredentialsFromUrl(): S3Config {
            val base64 = this.split("?c=")[1]
            return ObjectMapper().registerModule(KotlinModule.Builder().build()).readValue(Base64.getUrlDecoder().decode(base64), S3Config::class.java)
        }

        fun String.decodeS3Url(): String {
            return this.split("?c=")[0]
        }
    }
}