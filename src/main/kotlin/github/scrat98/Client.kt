package github.scrat98

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import mu.KLogging
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

fun main() = runBlocking {
    Client(this.coroutineContext).start()
}

class Client(override val coroutineContext: CoroutineContext) : CoroutineScope {

    fun start() {
        launch {
            processIncomingMessages()
        }
        launch {
            startSendingMessages()
        }
    }

    private val wsSession = AtomicReference<WebSocketSession?>(null)

    private suspend fun processIncomingMessages() {
        while (isActive) {
            runCatching {
                client.ws("ws://127.0.0.1:8080/ws") {
                    wsSession.compareAndSet(null, this)
                    incoming.consumeEach { frame ->
                        val message = frame as Frame.Text
                        logger.info { "Received message: ${message.readText()}" }
                    }
                }
            }.onFailure {
                wsSession.set(null)
                logger.error(it) { "Websocket connection failed" }
            }
        }
    }

    private suspend fun startSendingMessages() {
        while (isActive) {
            delay(2000)
            runCatching {
                wsSession.get()?.send("From client: " + UUID.randomUUID().toString())
            }.onFailure {
                logger.error(it) { "Error while sending message to the server" }
            }
        }
    }

    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 5.seconds.inWholeMilliseconds
            extensions {
                install(WebSocketDeflateExtension) {
//                    clientNoContextTakeOver = true
//                    serverNoContextTakeOver = true
                }
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10.seconds.inWholeMilliseconds
            socketTimeoutMillis = 60.seconds.inWholeMilliseconds
        }

        install(Logging)
    }

    private companion object : KLogging()
}