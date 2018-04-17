package com.decathlon.k8s.service

import io.kubernetes.client.ApiException
import io.kubernetes.client.JSON
import io.kubernetes.client.Pair
import io.kubernetes.client.util.KubeConfig
import io.kubernetes.client.util.SSLUtils
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.*
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory


val json = JSON()

@Throws(ApiException::class)
fun listNamespaceCall(config: KubeConfig): WebClient.RequestBodySpec {
    // create path and map variables
    val localVarPath = "/api/v1/namespaces"

    val localVarQueryParams = ArrayList<Pair>()
    val localVarCollectionQueryParams = ArrayList<Pair>()

    localVarQueryParams.add(Pair("watch", true.toString()))

    val localVarHeaderParams = HashMap<String, String>()

    val localVarAccepts = arrayOf("application/json", "application/yaml", "application/vnd.kubernetes.protobuf", "application/json;stream=watch", "application/vnd.kubernetes.protobuf;stream=watch")
    val localVarAccept = "application/json;stream=watch" // apiClient.selectHeaderAccept(localVarAccepts)
    if (localVarAccept != null) localVarHeaderParams["Accept"] = localVarAccept

    val localVarContentTypes = arrayOf("*/*")
    val localVarContentType = "*/*" // apiClient.selectHeaderContentType(localVarContentTypes)
    localVarHeaderParams["Content-Type"] = localVarContentType

    val localVarAuthNames = arrayOf("BearerToken")
    return buildCall(localVarPath, "GET", localVarQueryParams, localVarCollectionQueryParams, localVarHeaderParams, localVarAuthNames, config)
}

private fun buildCall(path: String, method: String, queryParams: List<Pair>, collectionQueryParams: List<Pair>, headerParams: Map<String, String>, authNames: Array<String>, config: KubeConfig): WebClient.RequestBodySpec {
    // Add header if needed
    // client.updateParamsForAuth(authNames, queryParams, headerParams)

    val builder=applySslSettings(config)

    val url = buildUrl(config, path, queryParams, collectionQueryParams)  // add parameter

    val req = builder.baseUrl(url).build().method(org.springframework.http.HttpMethod.valueOf(method))

    for ((key, value) in headerParams) {
        req.header(key, value)
    }

    var contentType: String? = headerParams.get("Content-Type")
    // ensuring a default content type
    if (contentType == null) {
        contentType = "application/json"
    }

    return req
}

/**
 * Apply SSL related settings to httpClient according to the current values of
 * verifyingSsl and sslCaCert.
 */
private fun applySslSettings(config: KubeConfig): WebClient.Builder {
    val appContext = SslContextBuilder.forClient()

    try {
        if (!config.verifySSL()) {
            val hostnameVerifier = HostnameVerifier { hostname, session -> true }
            // TODO

            appContext.trustManager(InsecureTrustManagerFactory.INSTANCE)
        } else if (config.certificateAuthorityData != null) {
            val cert= KubeConfig.getDataOrFile(
                    config.certificateAuthorityData, config.certificateAuthorityFile);

            val password: CharArray? = null // Any password will work.
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val certificates = certificateFactory.generateCertificates(ByteArrayInputStream(cert))
            if (certificates.isEmpty()) {
                throw IllegalArgumentException("expected non-empty set of trusted certificates")
            }
            val caKeyStore = newEmptyKeyStore(password)
            var index = 0
            for (certificate in certificates) {
                val certificateAlias = "ca" + Integer.toString(index++)
                caKeyStore.setCertificateEntry(certificateAlias, certificate)
            }
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(caKeyStore)
            appContext.trustManager(trustManagerFactory)

        }

        if (config.getClientCertificateData() != null) {
            val clientCert = KubeConfig.getDataOrFile(
                    config.getClientCertificateData(), config.getClientCertificateFile())
            val clientKey = KubeConfig.getDataOrFile(config.getClientKeyData(), config.getClientKeyFile())

            val dataString = String(clientKey)

            var algo = ""
            if (dataString.indexOf("BEGIN EC PRIVATE KEY") != -1) {
                algo = "EC"
            }
            if (dataString.indexOf("BEGIN RSA PRIVATE KEY") != -1) {
                algo = "RSA"
            }

            val keyStore = SSLUtils.createKeyStore(clientCert, clientKey, algo, "", null, null)
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, "".toCharArray())

            appContext.keyManager(kmf)
        }
    } catch (e: GeneralSecurityException) {
        throw RuntimeException(e)
    }

    val httpConnector = ReactorClientHttpConnector { opt -> opt.sslContext(appContext.build()) }
    return WebClient.builder().clientConnector(httpConnector)

}

@Throws(GeneralSecurityException::class)
private fun newEmptyKeyStore(password: CharArray?): KeyStore {
    try {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, password)
        return keyStore
    } catch (e: IOException) {
        throw AssertionError(e)
    }

}

/**
 * Build full URL by concatenating base path, the given sub path and query parameters.
 *
 * @param path The sub path
 * @param queryParams The query parameters
 * @param collectionQueryParams The collection query parameters
 * @return The full URL
 */
fun buildUrl(config: KubeConfig, path: String, queryParams: List<Pair>?, collectionQueryParams: List<Pair>?): String {
    val url = StringBuilder()
    url.append(config.server).append(path)

    if (queryParams != null && !queryParams.isEmpty()) {
        // support (constant) query string in `path`, e.g. "/posts?draft=1"
        var prefix: String? = if (path.contains("?")) "&" else "?"
        for (param in queryParams) {
            if (param.value != null) {
                if (prefix != null) {
                    url.append(prefix)
                    prefix = null
                } else {
                    url.append("&")
                }
                val value = parameterToString(param.value)
                url.append(escapeString(param.name)).append("=").append(escapeString(value))
            }
        }
    }

    if (collectionQueryParams != null && !collectionQueryParams.isEmpty()) {
        var prefix: String? = if (url.toString().contains("?")) "&" else "?"
        for (param in collectionQueryParams) {
            if (param.value != null) {
                if (prefix != null) {
                    url.append(prefix)
                    prefix = null
                } else {
                    url.append("&")
                }
                val value = parameterToString(param.value)
                // collection query parameter value already escaped as part of parameterToPairs
                url.append(escapeString(param.name)).append("=").append(value)
            }
        }
    }

    return url.toString()
}

fun escapeString(str: String): String {
    try {
        return URLEncoder.encode(str, "utf8").replace("\\+".toRegex(), "%20")
    } catch (e: UnsupportedEncodingException) {
        return str
    }
}

fun parameterToString(param: Any?): String {
    if (param == null) {
        return ""
    } else if (param is Date || param is DateTime || param is LocalDate) {
        //Serialize to json string and remove the " enclosing characters
        val jsonStr = json.serialize(param)
        return jsonStr.substring(1, jsonStr.length - 1)
    } else if (param is Collection<*>) {
        val b = StringBuilder()
        for (o in (param as Collection<*>?)!!) {
            if (b.length > 0) {
                b.append(",")
            }
            b.append(o.toString())
        }
        return b.toString()
    } else {
        return param.toString()
    }
}