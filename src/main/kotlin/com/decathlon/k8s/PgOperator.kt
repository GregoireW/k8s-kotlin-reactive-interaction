package com.decathlon.k8s

import com.decathlon.k8s.service.listNamespaceCall
import io.kubernetes.client.models.V1Namespace
import io.kubernetes.client.models.V1Status
import io.kubernetes.client.util.KubeConfig
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.core.ParameterizedTypeReference
import java.io.FileReader

data class WatchResponse<T>(val type: String, val `object`: T?, val status: V1Status?)


@SpringBootApplication
class DemoApplication: CommandLineRunner{


    override fun run(vararg args: String?) {

        val reader= FileReader("/opt/kube/.kube_config_cluster.yml")
        val config=KubeConfig.loadKubeConfig(reader)

        val req = listNamespaceCall(config)

        val ref= object: ParameterizedTypeReference<WatchResponse<V1Namespace>>(){}
        req.retrieve().bodyToFlux(ref).map{ println(it)}.subscribe { println("done") }

    }
}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}