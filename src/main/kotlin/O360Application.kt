package com.ikea.o360

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class O360Application

fun main(args: Array<String>) {
    runApplication<O360Application>(*args)
}