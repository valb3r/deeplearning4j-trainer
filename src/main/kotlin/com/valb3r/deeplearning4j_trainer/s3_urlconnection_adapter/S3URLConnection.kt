package com.valb3r.deeplearning4j_trainer.s3_urlconnection_adapter

import java.io.IOException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

class S3URLConnection(url: URL?) : URLConnection(url) {

    @Throws(IOException::class)
    override fun connect() {
        println("Connected!")
    }
}

// Name MUST BE Handler
class Handler : URLStreamHandler() {

    @Throws(IOException::class)
    override fun openConnection(url: URL): URLConnection {
        return S3URLConnection(url)
    }

    companion object {

        fun register() {
            val packageName = S3URLConnection::class.qualifiedName!!.replace("\\.\\w+$".toRegex(), "")
            var registered: String? = System.getProperty("java.protocol.handler.pkgs")
            registered = if (true == registered?.isNotBlank()) "|$registered" else ""
            System.setProperty("java.protocol.handler.pkgs", "$packageName$registered")
        }
    }
}