package pl.andrz.dungeon

import java.time.Instant
import kotlin.random.Random

internal const val NOTHING = 0x00000000

internal const val BLOCKED = 0x00000001
internal const val ROOM = 0x00000002
internal const val CORRIDOR = 0x00000004
internal const val PERIMETER = 0x00000010
internal const val ENTRANCE = 0x00000020
internal const val ROOM_ID = 0x0000FFC0

internal const val ARCH = 0x00010000
internal const val DOOR = 0x00020000
internal const val LOCKED = 0x00040000
internal const val TRAPPED = 0x00080000
internal const val SECRET = 0x00100000
internal const val PORTC = 0x00200000
internal const val STAIR_DN = 0x00400000
internal const val STAIR_UP = 0x00800000

internal const val LABEL = -0x01000000

internal val OPENSPACE = ROOM or CORRIDOR
internal val DOORSPACE = ARCH or DOOR or LOCKED or TRAPPED or SECRET or PORTC
internal val ESPACE = ENTRANCE or DOORSPACE or LABEL
internal val STAIRS = STAIR_DN or STAIR_UP

internal val BLOCK_ROOM = BLOCKED or ROOM
internal val BLOCK_CORR = BLOCKED or PERIMETER or CORRIDOR
internal val BLOCK_DOOR = BLOCKED or DOORSPACE

internal infix fun Int.has(flag: Int): Boolean = this and flag != 0
internal infix fun Int.hasNot(flag: Int): Boolean = !(this has flag)
internal infix fun Int.withFlag(flag: Int): Int = this or flag
internal infix fun Int.withoutFlag(flag: Int): Int = this and flag.inv()
internal val Int.roomIdValue: Int
    get() = (this and ROOM_ID) ushr 6

internal fun glyph(char: Char): Int = char.code shl 24

enum class Direction(val di: Int, val dj: Int) {
    NORTH(-1, 0),
    SOUTH(1, 0),
    WEST(0, -1),
    EAST(0, 1);

    val opposite: Direction
        get() = when (this) {
            NORTH -> SOUTH
            SOUTH -> NORTH
            WEST -> EAST
            EAST -> WEST
        }
}

data class Delta(val dr: Int, val dc: Int)

data class TunnelCheck(
    val walled: List<Delta> = emptyList(),
    val corridor: List<Delta> = emptyList(),
    val stair: Delta? = null,
    val next: Delta? = null,
    val close: List<Delta> = emptyList(),
    val open: Delta? = null,
    val recurse: Delta? = null,
)

data class DungeonOptions(
    val seed: Long = Instant.now().epochSecond,
    val nRows: Int = 39,
    val nCols: Int = 39,
    val dungeonLayout: String = "None",
    val roomMin: Int = 3,
    val roomMax: Int = 9,
    val roomLayout: String = "Scattered",
    val corridorLayout: String = "Bent",
    val removeDeadends: Int = 50,
    val addStairs: Int = 2,
    val mapStyle: String = "Standard",
    val cellSize: Int = 18,
    val grid: String = "Square",
)

data class RoomProto(
    var i: Int? = null,
    var j: Int? = null,
    var height: Int? = null,
    var width: Int? = null,
)

data class Door(
    val row: Int,
    val col: Int,
    var key: String,
    var type: String,
    var outId: Int? = null,
    var desc: String? = null,
)

data class Room(
    val id: Int,
    val row: Int,
    val col: Int,
    val north: Int,
    val south: Int,
    val west: Int,
    val east: Int,
    val height: Int,
    val width: Int,
    val area: Int,
    val doors: MutableMap<Direction, MutableList<Door>> = mutableMapOf(),
)

data class Stair(
    val row: Int,
    val col: Int,
    val nextRow: Int,
    val nextCol: Int,
    var key: String = "",
)

data class DoorSill(
    val sillR: Int,
    val sillC: Int,
    val dir: Direction,
    val doorR: Int,
    val doorC: Int,
    val outId: Int? = null,
)

data class SoundRoomResult(
    val blocked: Boolean = false,
    val hits: MutableMap<Int, Int> = mutableMapOf(),
)

class Dungeon(opts: DungeonOptions) {
    val seed: Long = opts.seed
    var nRows: Int = opts.nRows
    var nCols: Int = opts.nCols
    val dungeonLayout: String = opts.dungeonLayout
    val roomMin: Int = opts.roomMin
    val roomMax: Int = opts.roomMax
    val roomLayout: String = opts.roomLayout
    val corridorLayout: String = opts.corridorLayout
    val removeDeadends: Int = opts.removeDeadends
    val addStairs: Int = opts.addStairs
    val mapStyle: String = opts.mapStyle
    val cellSize: Int = opts.cellSize
    val grid: String = opts.grid

    var nI: Int
    var nJ: Int
    var maxRow: Int
    var maxCol: Int
    var nRooms: Int = 0
    var lastRoomId: Int = 0
    val roomBase: Int
    val roomRadix: Int

    val cell: Array<IntArray>
    val rooms: MutableList<Room?> = mutableListOf(null)
    val stairs: MutableList<Stair> = mutableListOf()
    val allDoors: MutableList<Door> = mutableListOf()
    val connect: MutableSet<String> = mutableSetOf()
    val random: Random = Random(seed)

    init {
        nI = nRows / 2
        nJ = nCols / 2
        nRows = nI * 2
        nCols = nJ * 2
        maxRow = nRows - 1
        maxCol = nCols - 1
        roomBase = (roomMin + 1) / 2
        roomRadix = ((roomMax - roomMin) / 2) + 1
        cell = Array(nRows + 1) { IntArray(nCols + 1) { NOTHING } }
    }

    fun nextInt(bound: Int): Int = bound.takeIf { it > 0 }?.let(random::nextInt) ?: 0
}

internal fun Dungeon.cellAt(r: Int, c: Int): Int =
    if (r !in 0..nRows || c !in 0..nCols) NOTHING else cell[r][c]
