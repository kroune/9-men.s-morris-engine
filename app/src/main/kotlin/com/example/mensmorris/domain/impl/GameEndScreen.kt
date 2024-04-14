package com.example.mensmorris.domain.impl

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.example.mensmorris.BUTTON_WIDTH
import com.example.mensmorris.activity
import com.example.mensmorris.common.utils.AppTheme
import com.example.mensmorris.common.gameBoard.GameBoard
import com.example.mensmorris.domain.GameScreenModel
import com.example.mensmorris.model.impl.WelcomeViewModel

/**
 * screen that is shown at the end
 */
class GameEndScreen(
    override var gameBoard: GameBoard
) : ViewModel(), GameScreenModel {

    private val pos = gameBoard.pos

    @Composable
    override fun InvokeRender() {
        AppTheme {
            DrawButtons()
            gameBoard.Draw()
        }
    }

    /**
     * draws screen after the game has ended
     */
    @Composable
    private fun DrawButtons() {
        Box(
            modifier = Modifier
                .fillMaxWidth(), Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(0.dp, BUTTON_WIDTH * 0.5f, 0.dp, 0.dp)
                    .fillMaxSize(), Alignment.Center
            ) {
                Text(fontSize = 30.sp, text = "Game has ended")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(), Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .size(BUTTON_WIDTH * if (pos.value.pieceToMove) 1.5f else 1f)
                        .background(Color.Green, CircleShape), Alignment.Center
                ) {
                    Text(color = Color.Blue, text = pos.value.freePieces.first.toString())
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(), Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(BUTTON_WIDTH * if (!pos.value.pieceToMove) 1.5f else 1f)
                        .background(Color.Blue, CircleShape), Alignment.Center
                ) {
                    Text(color = Color.Green, text = pos.value.freePieces.second.toString())
                }
            }
            Box(
                modifier = Modifier
                    .padding(0.dp, BUTTON_WIDTH * 10, 0.dp, 0.dp)
                    .fillMaxSize(), Alignment.Center
            ) {
                Button(onClick = {
                    activity.setContent {
                        WelcomeViewModel().Invoke()
                    }
                }) {
                    Text("Reset")
                }
            }
        }
    }
}
