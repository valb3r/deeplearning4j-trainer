package com.valb3r.deeplearning4j_trainer.s3_urlconnection_adapter.s3

import com.valb3r.deeplearning4j_trainer.storage.S3SystemStorage
import liquibase.pro.packaged.`is`
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

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
            return S3SystemStorage.INSTANCE.read(url.toURI().toASCIIString())
        }
    }

    companion object {

        fun register() {
            val packageName = Handler::class.qualifiedName!!.replace("\\.\\w+\\.\\w+$".toRegex(), "")
            var registered: String? = System.getProperty("java.protocol.handler.pkgs")
            registered = if (true == registered?.isNotBlank()) "|$registered" else ""
            System.setProperty("java.protocol.handler.pkgs", "$packageName$registered")
        }
    }
}