package com.kroune.nineMensMorrisApp.data.local.impl.game

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import com.kr8ne.mensMorris.Position
import com.kr8ne.mensMorris.gameStartPosition
import com.kr8ne.mensMorris.move.Movement
import com.kroune.NetworkResponse
import com.kroune.nineMensMorrisApp.ONLINE_GAME_SCREEN
import com.kroune.nineMensMorrisApp.WELCOME_SCREEN
import com.kroune.nineMensMorrisApp.common.SERVER_ADDRESS
import com.kroune.nineMensMorrisApp.common.USER_API
import com.kroune.nineMensMorrisApp.data.local.interfaces.DataI
import com.kroune.nineMensMorrisApp.data.remote.Common.jwtToken
import com.kroune.nineMensMorrisApp.data.remote.Common.network
import com.kroune.nineMensMorrisApp.data.remote.Common.networkScope
import com.kroune.nineMensMorrisApp.data.remote.game.GameRepository
import com.kroune.nineMensMorrisApp.ui.impl.game.GameBoardScreen
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.utils.io.printStack
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * data for online game screen
 */
class OnlineGameData(
    private val gameId: Long,
    private val navController: NavHostController?,
    private val gameRepository: GameRepository = GameRepository()
) : DataI() {

    /**
     * tells if user is green or blue
     */
    var isGreen: MutableState<Boolean?> = mutableStateOf(null)

    /**
     * our game board
     */
    val gameBoard = GameBoardScreen(
        pos = gameStartPosition,
        onClick = { index -> this.response(index) },
        navController = null
    )

    private fun GameBoardData.response(index: Int) {
        // check if we can make this move
        println("isGreen - $isGreen; pieceToMove - ${gameBoard.pos.value.pieceToMove}")
        if (isGreen.value == gameBoard.pos.value.pieceToMove) {
            gameBoard.viewModel.data.getMovement(index)?.let {
                println("added move")
                gameRepository.movesQueue.add(it)
            }
            handleClick(index)
            handleHighLighting()
        }
    }

    override fun invokeBackend() {
        CoroutineScope(networkScope).launch {
            try {
                val jwtTokenState = jwtToken ?: error("jwt token cannot be null")
                network.webSocket("ws${SERVER_ADDRESS}${USER_API}/game",
                    request = {
                        url {
                            parameters["jwtToken"] = jwtTokenState
                            parameters["gameId"] = gameId.toString()
                        }
                    }) {
                    val isGreenData = (incoming.receive() as Frame.Text).readText()
                    val isGreenText = Json.decodeFromString<NetworkResponse>(isGreenData)
                    isGreen.value = isGreenText.message.toBoolean()

                    val positionServerData = (incoming.receive() as Frame.Text).readText()
                    val positionString =
                        Json.decodeFromString<NetworkResponse>(positionServerData).message!!
                    val newPosition = Json.decodeFromString<Position>(positionString)
                    gameBoard.pos.value = newPosition
                    while (true) {
                        // send all our moves one by one
                        val moveToSend = gameRepository.movesQueue.poll()
                        if (moveToSend != null) {
                            val string = Json.encodeToString<Movement>(moveToSend)
                            // post our move
                            send(string)
                        }
                        try {
                            // receive the server's data
                            val serverMessage =
                                incoming.tryReceive().getOrNull() as? Frame.Text ?: continue
                            val serverResponse =
                                Json.decodeFromString<NetworkResponse>(serverMessage.readText())
                            when (serverResponse.code) {
                                410 -> {
                                    // game ended
                                    println("game ended")
                                    navController?.navigate(WELCOME_SCREEN)
                                }

                                200 -> {
                                    val movement =
                                        Json.decodeFromString<Movement>(serverResponse.message!!)
                                    // apply move
                                    println("new move")
                                    gameBoard.viewModel.data.processMove(movement)
                                }

                                else -> {
                                    // we reload our game
                                    navController?.navigate("$ONLINE_GAME_SCREEN/$gameId")
                                    println("smth went wrong, reloading")
                                }
                            }
                        } catch (e: Exception) {
                            println("error when receiving move")
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                println("error accessing ${"${SERVER_ADDRESS}${USER_API}/game; gameId = $gameId"}")
                e.printStack()
                // we try to relaunch this shit
                invokeBackend()
            }
        }
    }
}
