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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListJobAllocationsResponse(
        @SerialName("ID")
        val id: String,
        @SerialName("Name")
        val name: String,
        @SerialName("JobID")
        val jobId: String
)

@Serializable
data class Allocation(
        @SerialName("ID")
        val id: String,
        @SerialName("Name")
        val name: String,
        @SerialName("JobID")
        val jobId: String,
        @SerialName("TaskResources")
        val taskResources: Map<String, Resources>,
        @SerialName("Resources")
        val resources: Resources
)

@Serializable
data class Resources(
        @SerialName("CPU")
        val cpu: Int,
        @SerialName("Networks")
        val networks: List<Network>?
)

@Serializable
data class Network(
        @SerialName("IP")
        val ip: String,
        @SerialName("DynamicPorts")
        val dynamicPorts: List<DynamicPort>
)

@Serializable
data class DynamicPort(
        @SerialName("Label")
        val label: String,
        @SerialName("To")
        val to: Int,
        @SerialName("Value")
        val value: Int
)
