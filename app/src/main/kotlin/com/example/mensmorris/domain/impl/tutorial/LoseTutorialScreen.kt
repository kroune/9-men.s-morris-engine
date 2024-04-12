package com.example.mensmorris.domain.impl.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.mensmorris.BLUE_
import com.example.mensmorris.BUTTON_WIDTH
import com.example.mensmorris.EMPTY
import com.example.mensmorris.GREEN
import com.example.mensmorris.common.Position
import com.example.mensmorris.common.gameBoard.GameBoard
import com.example.mensmorris.domain.ScreenModel
import com.example.mensmorris.domain.impl.PieceCountFragment

/**
 * this screen tells about information indicators provide
 */
class LoseTutorialScreen : ScreenModel {
    private val position = Position(
        // @formatter:off
        arrayOf(
            BLUE_,                  BLUE_,                  BLUE_,
                    GREEN,          EMPTY,          EMPTY,
                            EMPTY,  EMPTY,  BLUE_,
            EMPTY,  GREEN,  EMPTY,          EMPTY,  EMPTY,  EMPTY,
                            EMPTY,  EMPTY,  EMPTY,
                    EMPTY,          EMPTY,          EMPTY,
            EMPTY,                  BLUE_,                  EMPTY
        ),
        // @formatter:on
        freePieces = Pair(0u, 0u), pieceToMove = true, removalCount = 0
    )

    private val gameBoard = GameBoard(position)

    private val pieceCountFragment = PieceCountFragment(gameBoard.pos)

    @Composable
    override fun InvokeRender() {
        // TODO: add animations
        pieceCountFragment.InvokeRender()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(15))
                .padding(bottom = BUTTON_WIDTH * 3),
            Alignment.BottomCenter
        ) {
            gameBoard.Draw()
            Column {
                Text(
                    text = "If you have only 3 pieces (including the ones you can place) you loose"
                )
            }
        }
    }
}
