package org.usvm.service

import com.squareup.wire.GrpcClient
import okhttp3.OkHttpClient
import okhttp3.Protocol

const val DEFAULT_PORT = 8080

fun grpcClient(port: Int = DEFAULT_PORT): GrpcClient {
    val okClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .build()
    return GrpcClient.Builder()
        .client(okClient)
        .baseUrl("http://0.0.0.0:$port")
        .build()
}
