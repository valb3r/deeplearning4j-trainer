package com.valb3r.deeeplearning4j_trainer.spring

import com.valb3r.deeeplearning4j_trainer.spring.config.DirectoriesConfig
import com.valb3r.deeeplearning4j_trainer.spring.config.UsersConfig
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties

@EnableConfigurationProperties(DirectoriesConfig::class, UsersConfig::class)
@SpringBootApplication(scanBasePackageClasses = [Deeplearing4jTrainerSpringApp::class])
class Deeplearing4jTrainerSpringApp {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(Deeplearing4jTrainerSpringApp::class.java, *args)
        }
    }
}