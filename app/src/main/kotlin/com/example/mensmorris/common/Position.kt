package com.example.mensmorris.common

import com.example.mensmorris.BLUE_COLOR
import com.example.mensmorris.CIRCLE
import com.example.mensmorris.DEPTH_COST
import com.example.mensmorris.ENEMY_UNFINISHED_TRIPLES_COST
import com.example.mensmorris.GREEN_COLOR
import com.example.mensmorris.LOST_GAME_COST
import com.example.mensmorris.NONE_COLOR
import com.example.mensmorris.PIECES_TO_FLY
import com.example.mensmorris.PIECE_COST
import com.example.mensmorris.POSSIBLE_TRIPLE_COST
import com.example.mensmorris.UNFINISHED_TRIPLES_COST
import com.example.mensmorris.common.utils.CacheUtils.occurredPositions
import com.example.mensmorris.common.utils.GameUtils
import com.example.mensmorris.get
import com.example.mensmorris.plus

/**
 * used for storing position data
 * @param positions all pieces
 * @param freePieces pieces we can still place: first - green, second - blue
 * both should be <= 8
 * @see longHashCode
 * @param greenPiecesAmount used for fast evaluation & game state checker (stores green pieces)
 * @param bluePiecesAmount used for fast evaluation & game state checker (stores blue pieces)
 * @param pieceToMove piece going to move next
 * @param removalCount amount of pieces to remove
 * always <= 2
 * @see longHashCode
 */
@Suppress("EqualsOrHashCode")
class Position(
    var positions: Array<Boolean?>,
    var freePieces: Pair<UByte, UByte> = Pair(0U, 0U),
    var greenPiecesAmount: UByte = (positions.count { it == true } + freePieces.first),
    var bluePiecesAmount: UByte = (positions.count { it == false } + freePieces.second),
    var pieceToMove: Boolean,
    var removalCount: Byte = 0
) {
    /**
     * evaluates position
     * @return pair, where first is eval. for green and the second one for blue
     */
    fun evaluate(depth: UByte = 0u): Pair<Int, Int> {
        if (greenPiecesAmount < PIECES_TO_FLY) {
            val depthCost = depth.toInt() * DEPTH_COST
            return Pair(LOST_GAME_COST + depthCost, Int.MAX_VALUE - depthCost)
        }
        if (bluePiecesAmount < PIECES_TO_FLY) {
            val depthCost = depth.toInt() * DEPTH_COST
            return Pair(Int.MAX_VALUE - depthCost, LOST_GAME_COST + depthCost)
        }
        var greenEvaluation = 0
        var blueEvaluation = 0

        val greenPieces = (greenPiecesAmount.toInt() + if (pieceToMove) removalCount.toInt() else 0)
        val bluePieces = (bluePiecesAmount.toInt() + if (!pieceToMove) removalCount.toInt() else 0)

        greenEvaluation += (greenPieces - bluePieces) * PIECE_COST
        blueEvaluation += (bluePieces - greenPieces) * PIECE_COST

        val (unfinishedTriples, findBlockedTriples) = triplesEvaluation()

        val greenUnfinishedTriplesDelta =
            (unfinishedTriples.first - unfinishedTriples.second * ENEMY_UNFINISHED_TRIPLES_COST)
        greenEvaluation += greenUnfinishedTriplesDelta * UNFINISHED_TRIPLES_COST


        val blueUnfinishedTriplesDelta =
            (unfinishedTriples.second - unfinishedTriples.first * ENEMY_UNFINISHED_TRIPLES_COST)
        blueEvaluation += blueUnfinishedTriplesDelta * UNFINISHED_TRIPLES_COST

        greenEvaluation +=
            (findBlockedTriples.first - findBlockedTriples.second) * POSSIBLE_TRIPLE_COST
        blueEvaluation +=
            (findBlockedTriples.second - findBlockedTriples.first) * POSSIBLE_TRIPLE_COST

        return Pair(greenEvaluation, blueEvaluation)
    }

    /**
     * @return pair of unfinished triples and blocked triples
     */
    fun triplesEvaluation(): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        var greenUnfinishedTriples = 0
        var blueUnfinishedTriples = 0
        var greenBlockedTriples = 0
        var blueBlockedTriples = 0
        for (ints in triplesMap) {
            var greenPieces = 0
            var bluePieces = 0
            ints.forEach {
                when (positions[it]) {
                    true -> {
                        greenPieces++
                    }

                    false -> {
                        bluePieces++
                    }

                    null -> {}
                }
            }
            if (greenPieces == 2 && bluePieces == 0) {
                greenUnfinishedTriples++
            }
            if (greenPieces == 0 && bluePieces == 2) {
                blueUnfinishedTriples++
            }
            if (greenPieces == 2 && bluePieces == 1) {
                greenBlockedTriples++
            }
            if (greenPieces == 1 && bluePieces == 2) {
                blueBlockedTriples++
            }
        }
        return Pair(
            Pair(greenUnfinishedTriples, blueUnfinishedTriples),
            Pair(greenBlockedTriples, blueBlockedTriples)
        )
    }

    /**
     * @return true if game has ended
     */
    private fun gameEnded(): Boolean {
        return greenPiecesAmount < PIECES_TO_FLY || bluePiecesAmount < PIECES_TO_FLY
    }

    /**
     * @param depth current depth
     * @color color of the piece we are finding a move for
     * @return possible positions and there evaluation
     */
    fun solve(
        depth: UByte
    ): Pair<Pair<Int, Int>, MutableList<Movement>> {
        if (depth == 0.toUByte() || gameEnded()) {
            return Pair(evaluate(depth), mutableListOf())
        }
        // for all possible positions, we try to solve them
        val positions: MutableList<Pair<Pair<Int, Int>, MutableList<Movement>>> = mutableListOf()
        generateMoves(depth).forEach {
            val result = it.producePosition(this).solve((depth - 1u).toUByte())
            if (depth != 1.toUByte() && result.second.isEmpty()) {
                return@forEach
            }
            result.second.add(it)
            positions.add(result)
        }
        if (positions.isEmpty()) {
            // if we can't make a move, we lose
            return Pair(
                if (!pieceToMove) {
                    Pair(LOST_GAME_COST, Int.MAX_VALUE)
                } else {
                    Pair(Int.MAX_VALUE, LOST_GAME_COST)
                }, mutableListOf()
            )
        }
        return positions.maxBy {
            it.first[pieceToMove]
        }
    }

    /**
     * @return a copy of the current position
     */
    fun copy(): Position {
        return Position(
            positions.clone(),
            freePieces,
            greenPiecesAmount,
            bluePiecesAmount,
            pieceToMove,
            removalCount
        )
    }

    /**
     * @param move the last move we have performed
     * @return the amount of removes we need to perform
     */
    fun removalAmount(move: Movement): Byte {
        if (move.endIndex == null) return 0

        return removeChecker[move.endIndex]!!.count { list ->
            list.all { positions[it] == pieceToMove }
        }.toByte()
    }

    /**
     * @return possible movements
     */
    fun generateMoves(currentDepth: UByte = 0u, ignoreCache: Boolean = false): List<Movement> {
        val str = longHashCode()
        if (!ignoreCache) {
            // check if we can abort calculation / use our previous result
            occurredPositions[str]?.let {
                if (it.second >= currentDepth) {
                    return listOf()
                } else {
                    occurredPositions[str] = Pair(it.first, currentDepth)
                    return it.first
                }
            }
        }
        val generatedList = when (gameState()) {
            GameUtils.GameState.Placement -> {
                generatePlacementMovements()
            }

            GameUtils.GameState.End -> {
                listOf()
            }

            GameUtils.GameState.Flying -> {
                generateFlyingMovements()
            }

            GameUtils.GameState.Normal -> {
                generateNormalMovements()
            }

            GameUtils.GameState.Removing -> {
                generateRemovalMoves()
            }
        }
        // store our work into our hashMap
        occurredPositions[str] = Pair(generatedList, currentDepth)
        return generatedList
    }

    private fun generateRemovalMoves(): List<Movement> {
        val possibleMove: MutableList<Movement> = mutableListOf()
        positions.forEachIndexed { index, piece ->
            if (piece == !pieceToMove) {
                possibleMove.add(Movement(index, null))
            }
        }
        return possibleMove
    }

    /**
     * @return all possible normal movements
     */
    private fun generateNormalMovements(): List<Movement> {
        val possibleMove: MutableList<Movement> = mutableListOf()
        positions.forEachIndexed { startIndex, piece ->
            if (piece == pieceToMove) {
                moveProvider[startIndex]!!.forEach { endIndex ->
                    if (positions[endIndex] == null) {
                        possibleMove.add(Movement(startIndex, endIndex))
                    }
                }
            }
        }
        return possibleMove
    }

    /**
     * @return all possible flying movements
     */
    private fun generateFlyingMovements(): List<Movement> {
        val possibleMove: MutableList<Movement> = mutableListOf()
        positions.forEachIndexed { startIndex, piece ->
            if (piece == pieceToMove) {
                positions.forEachIndexed { endIndex, endPiece ->
                    if (endPiece == null) {
                        possibleMove.add(Movement(startIndex, endIndex))
                    }
                }
            }
        }
        return possibleMove
    }

    /**
     * @return possible piece placements
     */
    private fun generatePlacementMovements(): List<Movement> {
        val possibleMove: MutableList<Movement> = mutableListOf()
        positions.forEachIndexed { endIndex, piece ->
            if (piece == null) {
                possibleMove.add(Movement(null, endIndex))
            }
        }
        return possibleMove
    }

    /**
     * @return state of the game
     */
    fun gameState(): GameUtils.GameState {
        return when {
            (gameEnded()) -> {
                GameUtils.GameState.End
            }

            (removalCount > 0) -> {
                GameUtils.GameState.Removing
            }

            (freePieces[pieceToMove] > 0U) -> {
                GameUtils.GameState.Placement
            }

            ((pieceToMove && greenPiecesAmount == PIECES_TO_FLY) ||
                    (!pieceToMove && bluePiecesAmount == PIECES_TO_FLY)) -> {
                GameUtils.GameState.Flying
            }

            else -> GameUtils.GameState.Normal
        }
    }


    /**
     * displays position
     * used for testing purpose
     */
    fun display() {
        val c = positions.map {
            if (it == null) {
                //GRAY_CIRCLE
                CIRCLE
            } else {
                if (!it) {
                    //BLUE_CIRCLE
                    BLUE_COLOR + CIRCLE + NONE_COLOR
                } else {
                    //GREEN_CIRCLE
                    GREEN_COLOR + CIRCLE + NONE_COLOR
                }
            }
        }
        println(
            """$NONE_COLOR
        ${c[0]}-----------------${c[1]}-----------------${c[2]}
        |                  |                  |
        |     ${c[3]}-----------${c[4]}-----------${c[5]}     |
        |     |            |            |     |
        |     |     ${c[6]}-----${c[7]}-----${c[8]}     |     |
        |     |     |             |     |     |
        ${c[9]}----${c[10]}----${c[11]}             ${c[12]}----${c[13]}----${c[14]}
        |     |     |             |     |     |
        |     |     ${c[15]}-----${c[16]}-----${c[17]}     |     |
        |     |            |            |     |
        |     ${c[18]}-----------${c[19]}-----------${c[20]}     |
        |                  |                  |
        ${c[21]}-----------------${c[22]}-----------------${c[23]}
        """.trimIndent()
        )
        println()
    }

    /**
     * used for easier writing of auto tests
     */
    @Suppress("unused")
    fun display2() {
        val c = positions.map {
            if (it == null) {
                "empty()"
            } else {
                if (it == false) {
                    "blue()"
                } else {
                    "green()"
                }
            }
        }
        @Suppress("LongLine") println(
            """
        Position(
            mutableListOf(
                ${c[0]},                                    ${c[1]},                                     ${c[2]},
                                ${c[3]},                    ${c[4]},                    ${c[5]},
                                            ${c[6]},        ${c[7]},        ${c[8]},
                ${c[9]},        ${c[10]},   ${c[11]},                       ${c[12]},   ${c[13]},        ${c[14]},
                                            ${c[15]},       ${c[16]},       ${c[17]},
                                ${c[18]},                   ${c[19]},                   ${c[20]},
                ${c[21]},                                   ${c[22]},                                    ${c[23]}
            ),
            freePieces = Pair(${freePieces.first}u, ${freePieces.second}u),
            pieceToMove = ${pieceToMove},
            removalCount = $removalCount
        )
        """.trimIndent()
        )
    }

    /**
     * this function is needed for unit tests
     * especially needed is comparison with other positions
     */
    override fun equals(other: Any?): Boolean {
        if (other !is Position) {
            return super.equals(other)
        }
        for (i in positions.indices) {
            if (positions[i] != other.positions[i]) {
                return false
            }
        }
        return freePieces == other.freePieces && pieceToMove == other.pieceToMove
    }

    /**
     * used for caching, replaces hashcode
     * this "hash" function has no collisions
     * each result is 28 symbols long
     * TODO: rewrite it
     */
    override fun toString(): String {
        return (if (pieceToMove) "1" else "0") + removalCount.toString() + positions.joinToString(
            separator = ""
        ) {
            when (it) {
                null -> "2"
                true -> "1"
                false -> "0"
            }
        } + freePieces.first + freePieces.second
    }

    /**
     * used for caching, replaces hashcode
     * this "hash" function has no collisions
     * each result is 31 symbols long
     * TODO: try to compress it
     * TODO: rewite UNIT tests for this
     * (1){pieceToMove}(1){removalCount}(24){positions}(3){freePieces.first}(3){freePieces.second}
     */
    private fun longHashCode(): Long {
        var result = 0L
        // 3^30 = 205891132094649
        result += removalCount * 205891132094649
        //3^29 = 68630377364883
        var pow329 = 68630377364883
        positions.forEach {
            result += when (it) {
                null -> 2
                true -> 1
                false -> 0
            } * pow329
            pow329 /= 3
        }
        result += freePieces.first.toString(3).toInt() * 9
        result += freePieces.second.toString(3).toInt() * 1
        if (pieceToMove) {
            result *= -1
        }
        return result
    }
}
