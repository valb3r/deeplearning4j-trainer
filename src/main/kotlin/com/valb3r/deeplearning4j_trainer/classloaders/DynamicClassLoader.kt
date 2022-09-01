package com.valb3r.deeplearning4j_trainer.classloaders

import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Paths

// Add -Djava.system.class.loader=com.valb3r.deeplearning4j_trainer.classloaders.DynamicClassLoader
// Kudos to https://github.com/update4j/update4j/blob/master/src/main/java/org/update4j/DynamicClassLoader.java
class DynamicClassLoader
    /*
     * Required when this classloader is used as the system classloader
     */
    @JvmOverloads
    constructor(parent: ClassLoader? = Thread.currentThread().contextClassLoader) : URLClassLoader(arrayOfNulls(0), parent)
{

    fun add(url: URL) {
        addURL(url)
    }

    /*
     *  Required for Java Agents when this classloader is used as the system classloader
     */
    @Throws(IOException::class)
    private fun appendToClassPathForInstrumentation(jarfile: String) {
        add(Paths.get(jarfile).toRealPath().toUri().toURL())
    }

    companion object {
        init {
            registerAsParallelCapable()
        }

        fun findAncestor(cl: ClassLoader?): DynamicClassLoader? {
            var current = cl
            do {
                if (current is DynamicClassLoader) {
                    return current
                }
                current = current!!.parent
            } while (current != null)
            return null
        }
    }
}