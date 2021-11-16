/*
 * Flydroid is a self hosted platform for Android emulation.
 *
 * Copyright (C) 2020-2021 by Frederic-Charles Barthelery.
 *
 * This file is part of Flydroid.
 *
 * Flydroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Flydroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Flydroid.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.geekorum.flydroid.apiserver.nomad

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import java.time.Duration

interface NomadService {
    suspend fun dispatchJob(jobId: String, payload: String = "", metadata: Map<String, String>): DispatchResponse
    suspend fun stopJob(jobId: String): StopJobResponse
    suspend fun listJobAllocations(jobId: String): List<ListJobAllocationsResponse>
    suspend fun readAllocation(allocId: String): Allocation
    suspend fun listJobs(): List<ListJobResponse>
    suspend fun readJob(jobId: String): Job?
    suspend fun listJobAllocations(jobId: String, timeout: Duration): List<ListJobAllocationsResponse>
}

class NomadServiceException(
        message: String? = "Error with Nomad service",
        cause: Throwable? = null
) : Exception(message, cause)

class HttpClientNomadService(
        httpClient: HttpClient,
        private val baseUrl: HttpUrl,
        private val nomadToken: String? = null
) : NomadService {

    private inline fun <reified T> runOrThrow(block: () -> T): T = runCatching(block).getOrElse {
        throw NomadServiceException(cause = it)
    }

    private val httpClient: HttpClient = httpClient.config {
        nomadToken?.let {
            defaultRequest {
                header("X-Nomad-Token", it)
            }
        }
    }

    override suspend fun dispatchJob(jobId: String, payload: String, metadata: Map<String, String>): DispatchResponse = runOrThrow {
        val payloadRequest = DispatchPayloadRequest(payload, metadata)
        val url = "${baseUrl}v1/job/$jobId/dispatch"
        httpClient.post(url) {
            contentType(ContentType.Application.Json)
            body = payloadRequest
        }
    }

    override suspend fun stopJob(jobId: String): StopJobResponse = runOrThrow {
        val url = "${baseUrl}v1/job/$jobId"
        httpClient.delete(url) {
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun listJobAllocations(jobId: String): List<ListJobAllocationsResponse> = runOrThrow {
        val url = "${baseUrl}v1/job/$jobId/allocations"
        httpClient.get(url)
    }

    override suspend fun listJobAllocations(jobId: String, timeout: Duration): List<ListJobAllocationsResponse> = runOrThrow {
        return withTimeoutOrNull(timeout.toMillis()) {
            var result: List<ListJobAllocationsResponse>
            var nomadIndex: String? = null
            do {
                var url = "${baseUrl}v1/job/$jobId/allocations"
                nomadIndex?.let { url += "?index=$it&wait=30s" }
                val response: HttpResponse = httpClient.get(url) {
                    timeout {
                        socketTimeoutMillis = Duration.ofSeconds(40).toMillis()
                    }
                }
                nomadIndex = response.headers["X-Nomad-Index"]
                result = response.receive()
            } while(result.isEmpty())
            result
        } ?: emptyList()
    }


    override suspend fun readAllocation(allocId: String): Allocation = runOrThrow {
        val url = "${baseUrl}v1/allocation/$allocId"
        httpClient.get(url)
    }

    override suspend fun listJobs(): List<ListJobResponse> = runOrThrow {
        val url = "${baseUrl}v1/jobs"
        httpClient.get(url)
    }

    override suspend fun readJob(jobId: String): Job? = runOrThrow {
        try {
            val url = "${baseUrl}v1/job/$jobId"
            httpClient.get(url)
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                return@runOrThrow null
            }
            throw e
        }
    }
}
