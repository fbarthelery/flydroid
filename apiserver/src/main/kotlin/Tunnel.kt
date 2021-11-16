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
package com.geekorum.flydroid.apiserver

import io.ktor.http.cio.websocket.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.time.delay
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.ConnectException
import java.time.Duration
import java.util.*

private var tunnelId: Long = 0

fun Route.wstunnel() {
    route("/wstunnel") {
        route("/{protocol}/{host}/{port}") {
            webSocket {
                val protocol = call.parameters["protocol"]!!
                val host = call.parameters["host"]!!
                val port = call.parameters["port"]!!.toInt()
                val destination = Destination(protocol, host, port)
                val tunnel = Tunnel(tunnelId = "${tunnelId++}",
                        webSocketSession = this,
                        destination = destination)
                try {
                    log.info("Creating tunnel ${tunnel.tunnelId} to  $destination")
                    tunnel.start().join()
                } finally {
                    log.info("End of tunnel ${tunnel.tunnelId} to $destination")
                }
            }
        }
    }
}

private data class Destination(
        val protocol: String,
        val host: String,
        val port: Int
)

private class Tunnel(
        val tunnelId: String = UUID.randomUUID().toString(),
        private val webSocketSession: DefaultWebSocketSession,
        private val destination: Destination
) {

    private val logger: Logger = LoggerFactory.getLogger("Tunnel")
    private val scope = CoroutineScope(webSocketSession.coroutineContext + Job()
            + CoroutineName("tunnel-$tunnelId"))

    private lateinit var socket: Socket
    private lateinit var destinationIncoming: ReceiveChannel<Frame>
    private lateinit var destinationOutgoing: ByteWriteChannel

    suspend fun start() = scope.launch {
        try {
            connectToDestination()
            createDestinationChannels()
            runTransferringLoop()
        } catch (e: ConnectException) {
            logger.debug("${tunnelId}: Unable to connect", e)
            webSocketSession.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Unable to connect to destination $destination"))
        } finally {
            logger.info("${tunnelId}: Closing tunnel")
            scope.cancel()
        }
    }

    private suspend fun runTransferringLoop() {
        try {
            logger.trace("${tunnelId}: start transferring packet")
            while (scope.isActive) {
                transferPacket()
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.debug("${tunnelId}: One end of the tunnel was closed", e)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun transferPacket() {
        select<Unit> {
            webSocketSession.incoming.onReceiveCatching { frameOrClosed ->
                if (frameOrClosed.isClosed) {
                    val closeReason = if (webSocketSession.closeReason.isCompleted)
                        webSocketSession.closeReason.await()
                    else null
                    throw ClosedReceiveChannelException("websocket closed reason $closeReason").apply {
                        initCause(frameOrClosed.exceptionOrNull())
                    }
                }
                frameOrClosed.getOrNull()?.let {
                    destinationOutgoing.writeFully(it.buffer)
                }
            }

            destinationIncoming.onReceiveCatching { frameOrClosed ->
                if (frameOrClosed.isClosed) {
                    throw ClosedReceiveChannelException("destination socket closed").apply {
                        initCause(frameOrClosed.exceptionOrNull())
                    }
                }
                frameOrClosed.getOrNull()?.let {
                    webSocketSession.outgoing.send(it)
                }
            }
        }
    }

    private suspend fun connectToDestination() {
        val s = tryConnectOrRetry().also {
            socket = it
        }

        scope.coroutineContext[Job]!!.invokeOnCompletion {
            logger.debug("$tunnelId: close destination socket")
            s.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createDestinationChannels() {
        destinationOutgoing = socket.openWriteChannel(autoFlush = true)
        destinationIncoming = scope.produce {
            val openReadChannel = socket.openReadChannel()
            val buffer = ByteArray(4096)
            do {
                val read: Int = openReadChannel.readAvailable(buffer)
                if (read > 0) {
                    val packet = buildPacket {
                        writeFully(buffer, length = read)
                    }
                    val frame = Frame.Binary(true, packet)
                    send(frame)
                }
            } while (read > 0)
        }
    }

    private suspend fun tryConnectOrRetry(): Socket {
        var delayS = 2.0
        repeat(5) {
            try {
                logger.trace("$tunnelId: making connection to $destination")
                return aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().connect(destination.host, destination.port) {}
            } catch (e: IOException) {
                if (it < 4) {
                    logger.debug("$tunnelId: try ${it + 1}/5 : fail to connect to destination. Retry in ${delayS}s")
                    delay(Duration.ofSeconds(delayS.toLong()))
                    delayS *= 2.0
                } else {
                    throw e
                }
            }
        }
        error("Should never get there")
    }
}
