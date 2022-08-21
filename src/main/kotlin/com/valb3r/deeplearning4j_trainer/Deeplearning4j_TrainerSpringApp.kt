package com.valb3r.deeplearning4j_trainer

import com.valb3r.deeplearning4j_trainer.config.DirectoriesConfig
import com.valb3r.deeplearning4j_trainer.config.S3Config
import com.valb3r.deeplearning4j_trainer.config.UsersConfig
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties

@EnableConfigurationProperties(DirectoriesConfig::class, UsersConfig::class, S3Config::class)
@SpringBootApplication(scanBasePackageClasses = [Deeplearning4j_TrainerSpringApp::class])
class Deeplearning4j_TrainerSpringApp {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(Deeplearning4j_TrainerSpringApp::class.java, *args)
        }
    }
}