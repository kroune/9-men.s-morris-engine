package com.kroune.nineMensMorrisApp.viewModel.useCases

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.kroune.nineMensMorrisApp.common.SERVER_ADDRESS
import com.kroune.nineMensMorrisApp.common.USER_API
import com.kroune.nineMensMorrisApp.data.remote.Common.network
import com.kroune.nineMensMorrisApp.data.remote.account.AccountInfoRepositoryI
import com.kroune.nineMensMorrisApp.viewModel.impl.game.GameBoardViewModel
import com.kroune.nineMensMorrisLib.Position
import com.kroune.nineMensMorrisLib.gameStartPosition
import com.kroune.nineMensMorrisLib.move.Movement
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * online game use case
 */
class OnlineGameUseCase(
    private val accountInfoRepository: AccountInfoRepositoryI,
    viewModelScope: CoroutineScope
) {
    /**
     * our web socket session
     */
    private var session: DefaultClientWebSocketSession? = null

    /**
     * time left for move
     */
    val timeLeft: MutableState<Long> = mutableStateOf(30)

    /**
     * switches to [Dispatchers.IO] and makes sure to cancel previous job
     */
    fun createGameConnection(gameId: Long) {
        gameJob?.cancel()
        gameJob = CoroutineScope(Dispatchers.IO).launch {
            exceptionHandler(gameId)
        }
    }

    init {
        viewModelScope.launch {
            while (true) {
                delay(1.seconds)
                // we just stop updating left time, since we expect the game to end soon
                timeLeft.value = (timeLeft.value - 1).coerceAtLeast(0)
            }
        }
    }

    private suspend fun exceptionHandler(gameId: Long) {
        try {
            connection(gameId)
        } catch (_: ClosedReceiveChannelException) {
            return
        } catch (e: Exception) {
            println("error accessing playing game")
            e.printStackTrace()
            delay(2000)
            exceptionHandler(gameId)
        }
    }

    private suspend fun connection(gameId: Long) {
        val jwtTokenState =
            accountInfoRepository.jwtTokenState.value ?: error("jwt token cannot be null")
        network.webSocket("ws$SERVER_ADDRESS$USER_API/game",
            request = {
                url {
                    parameters["jwtToken"] = jwtTokenState
                    parameters["gameId"] = gameId.toString()
                }
            }) {
            session = this
            isGreen.value = get().toBooleanStrict()
            gameBoard.pos.value = Json.decodeFromString<Position>(get())
            enemyId.emit(get().toLong())
            println("enemyId - ${enemyId.value}")
            while (true) {
                // receive the server's data
                val serverMessage = Json.decodeFromString<Movement>(get())
                // this is a special way to encode game end
                if (serverMessage.startIndex == null && serverMessage.endIndex == null) {
                    gameEnded.value = true
                    close(CloseReason(200, "game ended"))
                    break
                }
                gameBoard.pos.value = serverMessage.producePosition(gameBoard.pos.value)
                timeLeft.value = 30
                if (gameBoard.pos.value.pieceToMove == isGreen.value) {
                    gameBoard.handleHighLighting()
                } else {
                    gameBoard.moveHints.value = listOf()
                }
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.get(): String {
        val text = (incoming.receive() as Frame.Text).readText()
        return text
    }

    private var gameJob: Job? = null

    /**
     * id of the enemy
     */
    var enemyId: MutableStateFlow<Long?> = MutableStateFlow(null)

    /**
     * tells if the game has ended
     */
    val gameEnded = mutableStateOf(false)


    /**
     * tells if user is green or blue
     */
    var isGreen: MutableState<Boolean?> = mutableStateOf(null)

    /**
     * our game board
     */
    val gameBoard = GameBoardViewModel(
        pos = gameStartPosition,
        onClick = { index -> this.response(index) },
        onGameEnd = {
            println("Game ended")
        }
    )

    /**
     * sends information that we gave up
     */
    fun giveUp() {
        CoroutineScope(Dispatchers.IO).launch {
            val string = Json.encodeToString<Movement>(Movement(null, null))
            println("user gave up")
            session!!.send(string)
        }
    }

    private fun GameBoardUseCase.response(index: Int) {
        // check if we can make this move
        if (isGreen.value == gameBoard.pos.value.pieceToMove) {
            val move = gameBoard.getMovement(index)
            handleClick(index)
            if (gameBoard.pos.value.pieceToMove == isGreen.value) {
                handleHighLighting()
            } else {
                // we can't make any move if it isn't our move
                gameBoard.moveHints.value = listOf()
            }
            move?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    val string = Json.encodeToString<Movement>(it)
                    // post our move
                    session!!.send(string)
                    timeLeft.value = 30
                }
            }
        }
    }
}
