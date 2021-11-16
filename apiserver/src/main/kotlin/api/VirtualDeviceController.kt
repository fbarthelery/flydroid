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
package com.geekorum.flydroid.apiserver.api

import com.geekorum.flydroid.apiserver.nomad.Allocation
import com.geekorum.flydroid.apiserver.nomad.Job
import com.geekorum.flydroid.apiserver.nomad.NomadService
import kotlinx.coroutines.flow.*
import java.time.Duration

class VirtualDeviceController(
        private val nomadService: NomadService,
        val nomadDevices: Map<String, String>
) {

    enum class DeviceState {
        NOT_FOUND, CREATE_IN_PROGRESS, CREATED
    }

    /**
     * Create a VirtualDevice asynchronously.
     * The device is created asynchronously when nomad can allocate it.
     * @return id to track device creation
     */
    suspend fun createVirtualDevice(request: StartRequest): String {
        val jobId = nomadDevices[request.image] ?: throw UnknownImageException()
        val dispatchJob = nomadService.dispatchJob(jobId, metadata = request.toMap())
        return dispatchJob.dispatchedJobId
    }

    @Throws(UnknownImageException::class, AllocationException::class)
    suspend fun startVirtualDevice(request: StartRequest): VirtualDevice {
        val jobId = nomadDevices[request.image] ?: throw UnknownImageException()

        val allocation = with(nomadService) {
            val dispatchResponse = dispatchJob(jobId, metadata = request.toMap())
            val result = listJobAllocations(dispatchResponse.dispatchedJobId, Duration.ofMinutes(1))
            if (result.isEmpty()) {
                stopJob(dispatchResponse.dispatchedJobId)
                throw AllocationException("Unable to allocate device")
            }
            val allocId = result.first().id
            readAllocation(allocId)
        }

        return allocation.toVirtualDevice()
    }

    /**
     * Return a created VirtualDevice or null if not
     */
    suspend fun findVirtualDevice(deviceId: String): VirtualDevice? {
        val allocation = with(nomadService) {
            readJob(deviceId)?.let {
                if (it.isStopped) {
                    null
                } else {
                    readJobAllocation(it.id)
                }
            }
        }
        return allocation?.toVirtualDevice()
    }

    suspend fun getVirtualDeviceState(deviceId: String): DeviceState {
        with(nomadService) {
            val job = readJob(deviceId)?.takeUnless { it.isStopped } ?: return DeviceState.NOT_FOUND
            val allocations = listJobAllocations(job.id, Duration.ofMinutes(1))
            if (allocations.isEmpty()) {
                return DeviceState.CREATE_IN_PROGRESS
            }
            return DeviceState.CREATED
        }
    }

    suspend fun stopVirtualDevice(deviceId: String): VirtualDevice? {
        val allocation = with(nomadService) {
            readJob(deviceId)?.let {
                readJobAllocation(it.id)
            }
        }

        return allocation?.let {
            nomadService.stopJob(it.jobId)
            it.toVirtualDevice()
        }
    }

    suspend fun stopVirtualDeviceByName(name: String): VirtualDevice? {
        val allocation = with(nomadService) {
            val job = findRunningJobWithMetadataName(name)
            job?.let {
                readJobAllocation(it.id)
            }
        }

        return allocation?.let {
            nomadService.stopJob(it.jobId)
            it.toVirtualDevice()
        }
    }

    private suspend fun readJobAllocation(jobId: String): Allocation? {
        return with(nomadService) {
            val allocations = listJobAllocations(jobId)
            if (allocations.isEmpty()) {
                null
            } else {
                readAllocation(allocations.first().id)
            }
        }
    }

    private suspend fun NomadService.findRunningJobWithMetadataName(name: String): Job? {
        return try {
            listJobs().asFlow()
                    .map {
                        readJob(it.id)
                    }.filterNotNull()
                    .filter {
                        !it.isStopped
                    }.filter {
                        it.metadata?.get("name") == name
                    }.first()
        } catch (e: NoSuchElementException) {
            null
        }
    }

}

private fun Allocation.toVirtualDevice(): VirtualDevice {
    val network = resources.networks!!.first()
    val ip = network.ip
    val adbPort = network.dynamicPorts.first { it.label == "adb" }.value
    val consolePort = network.dynamicPorts.first { it.label == "console" }.value
    val grpcPort = network.dynamicPorts.first { it.label == "grpc" }.value
    return VirtualDevice(jobId, ip, adbPort, consolePort, grpcPort)
}

class UnknownImageException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
class AllocationException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

