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
data class DispatchPayloadRequest(
        @SerialName("Payload")
        val payload: String = "",
        @SerialName("Meta")
        val metadata: Map<String, String>? = null
)

@Serializable
data class DispatchResponse(
        @SerialName("Index")
        val index: Int,
        @SerialName("JobCreateIndex")
        val jobCreateIndex: Int,
        @SerialName("EvalCreateIndex")
        val evalCreateIndex: Int,
        @SerialName("EvalID")
        val evalId: String,
        @SerialName("DispatchedJobID")
        val dispatchedJobId: String
)
