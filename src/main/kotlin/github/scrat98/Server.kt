package github.scrat98

import mu.KLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    runApplication<Server>(*args)
}

@SpringBootApplication
@EnableWebSocket
class Server : WebSocketConfigurer {

    init {
        thread {
            while (true) {
                Thread.sleep(2000)
                val wsSession = wsSession.get() ?: continue
                if (!wsSession.isOpen) continue
                runCatching {
                    wsSession.sendMessage(TextMessage("From server: " + UUID.randomUUID().toString()))
                }.onFailure {
                    logger.error(it) { "Error while sending message to the client" }
                }
            }
        }
    }

    private val wsSession = AtomicReference<WebSocketSession?>(null)

    private val wsHandler = object : WebSocketHandler {
        override fun afterConnectionEstablished(session: WebSocketSession) {
            wsSession.compareAndSet(null, session)
            logger.info { "Connection established: ${session.id}" }
        }

        override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
            val textMessage = message as TextMessage
            logger.info { "Received message: ${textMessage.payload}" }
        }

        override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
            logger.error(exception) { "Ws error: ${session.id}" }
        }

        override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
            wsSession.set(null)
            logger.info { "Connection closed: ${session.id}" }
        }

        override fun supportsPartialMessages(): Boolean {
            return false
        }
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(wsHandler, "/ws")
            .setAllowedOrigins("*")
    }

    private companion object : KLogging()
}
