import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.util.Random
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Kotlin translation of dungeon.pl.
// Run with: kotlinc -script dungeon.kts

const val NOTHING = 0x00000000

const val BLOCKED = 0x00000001
const val ROOM = 0x00000002
const val CORRIDOR = 0x00000004
const val PERIMETER = 0x00000010
const val ENTRANCE = 0x00000020
const val ROOM_ID = 0x0000FFC0

const val ARCH = 0x00010000
const val DOOR = 0x00020000
const val LOCKED = 0x00040000
const val TRAPPED = 0x00080000
const val SECRET = 0x00100000
const val PORTC = 0x00200000
const val STAIR_DN = 0x00400000
const val STAIR_UP = 0x00800000

const val LABEL = -0x01000000

val OPENSPACE = ROOM or CORRIDOR
val DOORSPACE = ARCH or DOOR or LOCKED or TRAPPED or SECRET or PORTC
val ESPACE = ENTRANCE or DOORSPACE or LABEL
val STAIRS = STAIR_DN or STAIR_UP

val BLOCK_ROOM = BLOCKED or ROOM
val BLOCK_CORR = BLOCKED or PERIMETER or CORRIDOR
val BLOCK_DOOR = BLOCKED or DOORSPACE

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

data class Options(
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

data class Palette(
    val fill: Color,
    val open: Color,
    val openGrid: Color?,
    val wall: Color,
    val door: Color,
    val label: Color,
    val stair: Color,
    val black: Color = Color.BLACK,
    val white: Color = Color.WHITE,
)

data class ImageInfo(
    val cellSize: Int,
    val mapStyle: String,
    val width: Int,
    val height: Int,
    val maxX: Int,
    val maxY: Int,
    val font: Font,
    val charW: Int,
    val charH: Int,
    val charX: Int,
    val charY: Int,
)

class Dungeon(opts: Options) {
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

    fun nextInt(bound: Int): Int = if (bound <= 0) 0 else random.nextInt(bound)
}

val dungeonLayouts = mapOf(
    "Box" to listOf(
        listOf(1, 1, 1),
        listOf(1, 0, 1),
        listOf(1, 1, 1),
    ),
    "Cross" to listOf(
        listOf(0, 1, 0),
        listOf(1, 1, 1),
        listOf(0, 1, 0),
    ),
)

val corridorLayouts = mapOf(
    "Labyrinth" to 0,
    "Bent" to 50,
    "Straight" to 100,
)

val stairEnd = mapOf(
    Direction.NORTH to TunnelCheck(
        walled = listOf(
            Delta(1, -1), Delta(0, -1), Delta(-1, -1), Delta(-1, 0),
            Delta(-1, 1), Delta(0, 1), Delta(1, 1),
        ),
        corridor = listOf(Delta(0, 0), Delta(1, 0), Delta(2, 0)),
        stair = Delta(0, 0),
        next = Delta(1, 0),
    ),
    Direction.SOUTH to TunnelCheck(
        walled = listOf(
            Delta(-1, -1), Delta(0, -1), Delta(1, -1), Delta(1, 0),
            Delta(1, 1), Delta(0, 1), Delta(-1, 1),
        ),
        corridor = listOf(Delta(0, 0), Delta(-1, 0), Delta(-2, 0)),
        stair = Delta(0, 0),
        next = Delta(-1, 0),
    ),
    Direction.WEST to TunnelCheck(
        walled = listOf(
            Delta(-1, 1), Delta(-1, 0), Delta(-1, -1), Delta(0, -1),
            Delta(1, -1), Delta(1, 0), Delta(1, 1),
        ),
        corridor = listOf(Delta(0, 0), Delta(0, 1), Delta(0, 2)),
        stair = Delta(0, 0),
        next = Delta(0, 1),
    ),
    Direction.EAST to TunnelCheck(
        walled = listOf(
            Delta(-1, -1), Delta(-1, 0), Delta(-1, 1), Delta(0, 1),
            Delta(1, 1), Delta(1, 0), Delta(1, -1),
        ),
        corridor = listOf(Delta(0, 0), Delta(0, -1), Delta(0, -2)),
        stair = Delta(0, 0),
        next = Delta(0, -1),
    ),
)

val closeEnd = mapOf(
    Direction.NORTH to TunnelCheck(
        walled = listOf(Delta(0, -1), Delta(1, -1), Delta(1, 0), Delta(1, 1), Delta(0, 1)),
        close = listOf(Delta(0, 0)),
        recurse = Delta(-1, 0),
    ),
    Direction.SOUTH to TunnelCheck(
        walled = listOf(Delta(0, -1), Delta(-1, -1), Delta(-1, 0), Delta(-1, 1), Delta(0, 1)),
        close = listOf(Delta(0, 0)),
        recurse = Delta(1, 0),
    ),
    Direction.WEST to TunnelCheck(
        walled = listOf(Delta(-1, 0), Delta(-1, 1), Delta(0, 1), Delta(1, 1), Delta(1, 0)),
        close = listOf(Delta(0, 0)),
        recurse = Delta(0, -1),
    ),
    Direction.EAST to TunnelCheck(
        walled = listOf(Delta(-1, 0), Delta(-1, -1), Delta(0, -1), Delta(1, -1), Delta(1, 0)),
        close = listOf(Delta(0, 0)),
        recurse = Delta(0, 1),
    ),
)

val colorChain = mapOf(
    "door" to "fill",
    "label" to "fill",
    "stair" to "wall",
    "wall" to "fill",
    "fill" to "black",
)

fun getOpts(): Options = Options()

fun createDungeon(opts: Options): Dungeon {
    var dungeon = Dungeon(opts)
    dungeon = initCells(dungeon)
    dungeon = emplaceRooms(dungeon)
    dungeon = openRooms(dungeon)
    dungeon = labelRooms(dungeon)
    dungeon = corridors(dungeon)
    if (dungeon.addStairs > 0) {
        dungeon = emplaceStairs(dungeon)
    }
    dungeon = cleanDungeon(dungeon)
    return dungeon
}

fun initCells(dungeon: Dungeon): Dungeon {
    for (r in 0..dungeon.nRows) {
        for (c in 0..dungeon.nCols) {
            dungeon.cell[r][c] = NOTHING
        }
    }

    val mask = dungeonLayouts[dungeon.dungeonLayout]
    if (mask != null) {
        maskCells(dungeon, mask)
    } else if (dungeon.dungeonLayout == "Round") {
        roundMask(dungeon)
    }
    return dungeon
}

fun maskCells(dungeon: Dungeon, mask: List<List<Int>>): Dungeon {
    val rScale = mask.size.toDouble() / (dungeon.nRows + 1)
    val cScale = mask.first().size.toDouble() / (dungeon.nCols + 1)
    for (r in 0..dungeon.nRows) {
        for (c in 0..dungeon.nCols) {
            val mr = min(mask.size - 1, (r * rScale).toInt())
            val mc = min(mask.first().size - 1, (c * cScale).toInt())
            if (mask[mr][mc] == 0) {
                dungeon.cell[r][c] = BLOCKED
            }
        }
    }
    return dungeon
}

fun roundMask(dungeon: Dungeon): Dungeon {
    val centerR = dungeon.nRows / 2
    val centerC = dungeon.nCols / 2
    for (r in 0..dungeon.nRows) {
        for (c in 0..dungeon.nCols) {
            val d = sqrt(((r - centerR) * (r - centerR) + (c - centerC) * (c - centerC)).toDouble())
            if (d > centerC) {
                dungeon.cell[r][c] = BLOCKED
            }
        }
    }
    return dungeon
}

fun emplaceRooms(dungeon: Dungeon): Dungeon =
    if (dungeon.roomLayout == "Packed") packRooms(dungeon) else scatterRooms(dungeon)

fun packRooms(dungeon: Dungeon): Dungeon {
    for (i in 0 until dungeon.nI) {
        val r = (i * 2) + 1
        for (j in 0 until dungeon.nJ) {
            val c = (j * 2) + 1
            if ((dungeon.cell[r][c] and ROOM) != 0) {
                continue
            }
            if ((i == 0 || j == 0) && dungeon.nextInt(2) != 0) {
                continue
            }
            emplaceRoom(dungeon, RoomProto(i = i, j = j))
        }
    }
    return dungeon
}

fun scatterRooms(dungeon: Dungeon): Dungeon {
    repeat(allocRooms(dungeon)) {
        emplaceRoom(dungeon, null)
    }
    return dungeon
}

fun allocRooms(dungeon: Dungeon): Int {
    val dungeonArea = dungeon.nCols * dungeon.nRows
    val roomArea = dungeon.roomMax * dungeon.roomMax
    return dungeonArea / roomArea
}

fun emplaceRoom(dungeon: Dungeon, initialProto: RoomProto?): Dungeon {
    if (dungeon.nRooms == 999) {
        return dungeon
    }

    val proto = setRoom(dungeon, initialProto ?: RoomProto())
    val r1 = (proto.i!! * 2) + 1
    val c1 = (proto.j!! * 2) + 1
    val r2 = ((proto.i!! + proto.height!!) * 2) - 1
    val c2 = ((proto.j!! + proto.width!!) * 2) - 1

    if (r1 < 1 || r2 > dungeon.maxRow || c1 < 1 || c2 > dungeon.maxCol) {
        return dungeon
    }

    val hit = soundRoom(dungeon, r1, c1, r2, c2)
    if (hit.blocked || hit.hits.isNotEmpty()) {
        return dungeon
    }

    val roomId = dungeon.nRooms + 1
    dungeon.nRooms = roomId
    dungeon.lastRoomId = roomId

    for (r in r1..r2) {
        for (c in c1..c2) {
            if ((dungeon.cell[r][c] and ENTRANCE) != 0) {
                dungeon.cell[r][c] = dungeon.cell[r][c] and ESPACE.inv()
            } else if ((dungeon.cell[r][c] and PERIMETER) != 0) {
                dungeon.cell[r][c] = dungeon.cell[r][c] and PERIMETER.inv()
            }
            dungeon.cell[r][c] = dungeon.cell[r][c] or ROOM or (roomId shl 6)
        }
    }

    val height = ((r2 - r1) + 1) * 10
    val width = ((c2 - c1) + 1) * 10
    val room = Room(
        id = roomId,
        row = r1,
        col = c1,
        north = r1,
        south = r2,
        west = c1,
        east = c2,
        height = height,
        width = width,
        area = height * width,
    )
    while (dungeon.rooms.size <= roomId) {
        dungeon.rooms.add(null)
    }
    dungeon.rooms[roomId] = room

    for (r in (r1 - 1)..(r2 + 1)) {
        if ((dungeon.cell[r][c1 - 1] and (ROOM or ENTRANCE)) == 0) {
            dungeon.cell[r][c1 - 1] = dungeon.cell[r][c1 - 1] or PERIMETER
        }
        if ((dungeon.cell[r][c2 + 1] and (ROOM or ENTRANCE)) == 0) {
            dungeon.cell[r][c2 + 1] = dungeon.cell[r][c2 + 1] or PERIMETER
        }
    }
    for (c in (c1 - 1)..(c2 + 1)) {
        if ((dungeon.cell[r1 - 1][c] and (ROOM or ENTRANCE)) == 0) {
            dungeon.cell[r1 - 1][c] = dungeon.cell[r1 - 1][c] or PERIMETER
        }
        if ((dungeon.cell[r2 + 1][c] and (ROOM or ENTRANCE)) == 0) {
            dungeon.cell[r2 + 1][c] = dungeon.cell[r2 + 1][c] or PERIMETER
        }
    }

    return dungeon
}

fun setRoom(dungeon: Dungeon, proto: RoomProto): RoomProto {
    if (proto.height == null) {
        proto.height = if (proto.i != null) {
            val available = max(0, dungeon.nI - dungeon.roomBase - proto.i!!)
            val range = min(available, dungeon.roomRadix)
            dungeon.nextInt(range) + dungeon.roomBase
        } else {
            dungeon.nextInt(dungeon.roomRadix) + dungeon.roomBase
        }
    }

    if (proto.width == null) {
        proto.width = if (proto.j != null) {
            val available = max(0, dungeon.nJ - dungeon.roomBase - proto.j!!)
            val range = min(available, dungeon.roomRadix)
            dungeon.nextInt(range) + dungeon.roomBase
        } else {
            dungeon.nextInt(dungeon.roomRadix) + dungeon.roomBase
        }
    }

    if (proto.i == null) {
        proto.i = dungeon.nextInt(dungeon.nI - proto.height!!)
    }
    if (proto.j == null) {
        proto.j = dungeon.nextInt(dungeon.nJ - proto.width!!)
    }
    return proto
}

fun soundRoom(dungeon: Dungeon, r1: Int, c1: Int, r2: Int, c2: Int): SoundRoomResult {
    val hits = mutableMapOf<Int, Int>()
    for (r in r1..r2) {
        for (c in c1..c2) {
            val cell = dungeon.cell[r][c]
            if ((cell and BLOCKED) != 0) {
                return SoundRoomResult(blocked = true)
            }
            if ((cell and ROOM) != 0) {
                val id = (cell and ROOM_ID) ushr 6
                hits[id] = (hits[id] ?: 0) + 1
            }
        }
    }
    return SoundRoomResult(hits = hits)
}

fun openRooms(dungeon: Dungeon): Dungeon {
    for (id in 1..dungeon.nRooms) {
        val room = dungeon.rooms.getOrNull(id) ?: continue
        openRoom(dungeon, room)
    }
    dungeon.connect.clear()
    return dungeon
}

fun openRoom(dungeon: Dungeon, room: Room): Dungeon {
    val sills = doorSills(dungeon, room).toMutableList()
    if (sills.isEmpty()) {
        return dungeon
    }

    val nOpens = allocOpens(room, dungeon)
    var opened = 0
    while (opened < nOpens && sills.isNotEmpty()) {
        val sill = sills.removeAt(dungeon.nextInt(sills.size))
        val doorCell = dungeon.cell[sill.doorR][sill.doorC]
        if ((doorCell and DOORSPACE) != 0) {
            continue
        }

        val outId = sill.outId
        if (outId != null) {
            val connectKey = listOf(room.id, outId).sorted().joinToString(",")
            if (!dungeon.connect.add(connectKey)) {
                continue
            }
        }

        for (x in 0..2) {
            val r = sill.sillR + (sill.dir.di * x)
            val c = sill.sillC + (sill.dir.dj * x)
            dungeon.cell[r][c] = dungeon.cell[r][c] and PERIMETER.inv()
            dungeon.cell[r][c] = dungeon.cell[r][c] or ENTRANCE
        }

        val doorType = doorType(dungeon)
        val door = Door(row = sill.doorR, col = sill.doorC, key = "", type = "", outId = outId)
        when (doorType) {
            ARCH -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] or ARCH
                door.key = "arch"
                door.type = "Archway"
            }
            DOOR -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] or DOOR or ('o'.toInt() shl 24)
                door.key = "open"
                door.type = "Unlocked Door"
            }
            LOCKED -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] or LOCKED or ('x'.toInt() shl 24)
                door.key = "lock"
                door.type = "Locked Door"
            }
            TRAPPED -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] or TRAPPED or ('t'.toInt() shl 24)
                door.key = "trap"
                door.type = "Trapped Door"
            }
            SECRET -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] or SECRET or ('s'.toInt() shl 24)
                door.key = "secret"
                door.type = "Secret Door"
            }
            else -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] or PORTC or ('#'.toInt() shl 24)
                door.key = "portc"
                door.type = "Portcullis"
            }
        }
        room.doors.getOrPut(sill.dir) { mutableListOf() }.add(door)
        opened += 1
    }
    return dungeon
}

fun allocOpens(room: Room, dungeon: Dungeon): Int {
    val roomH = ((room.south - room.north) / 2) + 1
    val roomW = ((room.east - room.west) / 2) + 1
    val flumph = sqrt((roomW * roomH).toDouble()).toInt()
    return flumph + dungeon.nextInt(flumph)
}

fun doorSills(dungeon: Dungeon, room: Room): List<DoorSill> {
    val list = mutableListOf<DoorSill>()
    if (room.north >= 3) {
        for (c in room.west..room.east step 2) {
            checkSill(dungeon, room, room.north, c, Direction.NORTH)?.let(list::add)
        }
    }
    if (room.south <= dungeon.nRows - 3) {
        for (c in room.west..room.east step 2) {
            checkSill(dungeon, room, room.south, c, Direction.SOUTH)?.let(list::add)
        }
    }
    if (room.west >= 3) {
        for (r in room.north..room.south step 2) {
            checkSill(dungeon, room, r, room.west, Direction.WEST)?.let(list::add)
        }
    }
    if (room.east <= dungeon.nCols - 3) {
        for (r in room.north..room.south step 2) {
            checkSill(dungeon, room, r, room.east, Direction.EAST)?.let(list::add)
        }
    }
    return shuffleList(list, dungeon)
}

fun checkSill(dungeon: Dungeon, room: Room, sillR: Int, sillC: Int, dir: Direction): DoorSill? {
    val doorR = sillR + dir.di
    val doorC = sillC + dir.dj
    val doorCell = dungeon.cell[doorR][doorC]
    if ((doorCell and PERIMETER) == 0 || (doorCell and BLOCK_DOOR) != 0) {
        return null
    }

    val outR = doorR + dir.di
    val outC = doorC + dir.dj
    val outCell = dungeon.cell[outR][outC]
    if ((outCell and BLOCKED) != 0) {
        return null
    }

    var outId: Int? = null
    if ((outCell and ROOM) != 0) {
        outId = (outCell and ROOM_ID) ushr 6
        if (outId == room.id) {
            return null
        }
    }

    return DoorSill(
        sillR = sillR,
        sillC = sillC,
        dir = dir,
        doorR = doorR,
        doorC = doorC,
        outId = outId,
    )
}

fun <T> shuffleList(list: List<T>, dungeon: Dungeon): List<T> {
    val copy = list.toMutableList()
    for (i in copy.indices.reversed()) {
        val j = dungeon.nextInt(i + 1)
        val tmp = copy[i]
        copy[i] = copy[j]
        copy[j] = tmp
    }
    return copy
}

fun doorType(dungeon: Dungeon): Int {
    return when (val i = dungeon.nextInt(110)) {
        in 0 until 15 -> ARCH
        in 15 until 60 -> DOOR
        in 60 until 75 -> LOCKED
        in 75 until 90 -> TRAPPED
        in 90 until 100 -> SECRET
        else -> PORTC
    }
}

fun labelRooms(dungeon: Dungeon): Dungeon {
    for (id in 1..dungeon.nRooms) {
        val room = dungeon.rooms.getOrNull(id) ?: continue
        val label = room.id.toString()
        val len = label.length
        val labelR = (room.north + room.south) / 2
        val labelC = ((room.west + room.east - len) / 2) + 1
        for ((offset, ch) in label.withIndex()) {
            dungeon.cell[labelR][labelC + offset] = dungeon.cell[labelR][labelC + offset] or (ch.toInt() shl 24)
        }
    }
    return dungeon
}

fun corridors(dungeon: Dungeon): Dungeon {
    for (i in 1 until dungeon.nI) {
        val r = (i * 2) + 1
        for (j in 1 until dungeon.nJ) {
            val c = (j * 2) + 1
            if ((dungeon.cell[r][c] and CORRIDOR) != 0) {
                continue
            }
            tunnel(dungeon, i, j, null)
        }
    }
    return dungeon
}

fun tunnel(dungeon: Dungeon, i: Int, j: Int, lastDir: Direction?): Dungeon {
    for (dir in tunnelDirs(dungeon, lastDir)) {
        if (openTunnel(dungeon, i, j, dir)) {
            tunnel(dungeon, i + dir.di, j + dir.dj, dir)
        }
    }
    return dungeon
}

fun tunnelDirs(dungeon: Dungeon, lastDir: Direction?): List<Direction> {
    val probability = corridorLayouts[dungeon.corridorLayout] ?: 0
    val dirs = shuffleList(Direction.values().toList(), dungeon).toMutableList()
    if (lastDir != null && probability > 0 && dungeon.nextInt(100) < probability) {
        dirs.add(0, lastDir)
    }
    return dirs
}

fun openTunnel(dungeon: Dungeon, i: Int, j: Int, dir: Direction): Boolean {
    val thisR = (i * 2) + 1
    val thisC = (j * 2) + 1
    val nextR = ((i + dir.di) * 2) + 1
    val nextC = ((j + dir.dj) * 2) + 1
    val midR = (thisR + nextR) / 2
    val midC = (thisC + nextC) / 2
    return if (soundTunnel(dungeon, midR, midC, nextR, nextC)) {
        delveTunnel(dungeon, thisR, thisC, nextR, nextC)
    } else {
        false
    }
}

fun soundTunnel(dungeon: Dungeon, midR: Int, midC: Int, nextR: Int, nextC: Int): Boolean {
    if (nextR < 0 || nextR > dungeon.nRows || nextC < 0 || nextC > dungeon.nCols) {
        return false
    }
    val r1 = min(midR, nextR)
    val r2 = max(midR, nextR)
    val c1 = min(midC, nextC)
    val c2 = max(midC, nextC)
    for (r in r1..r2) {
        for (c in c1..c2) {
            if ((dungeon.cell[r][c] and BLOCK_CORR) != 0) {
                return false
            }
        }
    }
    return true
}

fun delveTunnel(dungeon: Dungeon, thisR: Int, thisC: Int, nextR: Int, nextC: Int): Boolean {
    val r1 = min(thisR, nextR)
    val r2 = max(thisR, nextR)
    val c1 = min(thisC, nextC)
    val c2 = max(thisC, nextC)
    for (r in r1..r2) {
        for (c in c1..c2) {
            dungeon.cell[r][c] = dungeon.cell[r][c] and ENTRANCE.inv()
            dungeon.cell[r][c] = dungeon.cell[r][c] or CORRIDOR
        }
    }
    return true
}

fun emplaceStairs(dungeon: Dungeon): Dungeon {
    val list = stairEnds(dungeon).toMutableList()
    if (dungeon.addStairs <= 0 || list.isEmpty()) {
        return dungeon
    }
    for (i in 0 until dungeon.addStairs) {
        if (list.isEmpty()) {
            break
        }
        val stair = list.removeAt(dungeon.nextInt(list.size))
        val type = if (i < 2) i else dungeon.nextInt(2)
        if (type == 0) {
            dungeon.cell[stair.row][stair.col] = dungeon.cell[stair.row][stair.col] or STAIR_DN or ('d'.toInt() shl 24)
            stair.key = "down"
        } else {
            dungeon.cell[stair.row][stair.col] = dungeon.cell[stair.row][stair.col] or STAIR_UP or ('u'.toInt() shl 24)
            stair.key = "up"
        }
        dungeon.stairs.add(stair)
    }
    return dungeon
}

fun stairEnds(dungeon: Dungeon): List<Stair> {
    val list = mutableListOf<Stair>()
    for (i in 0 until dungeon.nI) {
        val r = (i * 2) + 1
        for (j in 0 until dungeon.nJ) {
            val c = (j * 2) + 1
            if (dungeon.cell[r][c] != CORRIDOR || (dungeon.cell[r][c] and STAIRS) != 0) {
                continue
            }

            for (dir in Direction.values()) {
                val spec = stairEnd[dir] ?: continue
                if (checkTunnel(dungeon, r, c, spec)) {
                    val next = spec.next!!
                    list.add(
                        Stair(
                            row = r,
                            col = c,
                            nextRow = r + next.dr,
                            nextCol = c + next.dc,
                        )
                    )
                    break
                }
            }
        }
    }
    return list
}

fun cleanDungeon(dungeon: Dungeon): Dungeon {
    if (dungeon.removeDeadends > 0) {
        removeDeadends(dungeon)
    }
    fixDoors(dungeon)
    emptyBlocks(dungeon)
    return dungeon
}

fun removeDeadends(dungeon: Dungeon): Dungeon = collapseTunnels(dungeon, dungeon.removeDeadends, closeEnd)

fun collapseTunnels(dungeon: Dungeon, percent: Int, checks: Map<Direction, TunnelCheck>): Dungeon {
    if (percent <= 0) {
        return dungeon
    }
    val collapseAll = percent == 100
    for (i in 0 until dungeon.nI) {
        val r = (i * 2) + 1
        for (j in 0 until dungeon.nJ) {
            val c = (j * 2) + 1
            val cell = dungeon.cell[r][c]
            if ((cell and OPENSPACE) == 0 || (cell and STAIRS) != 0) {
                continue
            }
            if (!collapseAll && dungeon.nextInt(100) >= percent) {
                continue
            }
            collapse(dungeon, r, c, checks)
        }
    }
    return dungeon
}

fun collapse(dungeon: Dungeon, r: Int, c: Int, checks: Map<Direction, TunnelCheck>): Dungeon {
    if ((dungeon.cell[r][c] and OPENSPACE) == 0) {
        return dungeon
    }
    for (dir in Direction.values()) {
        val spec = checks[dir] ?: continue
        if (!checkTunnel(dungeon, r, c, spec)) {
            continue
        }
        for (delta in spec.close) {
            dungeon.cell[r + delta.dr][c + delta.dc] = NOTHING
        }
        spec.open?.let { delta ->
            dungeon.cell[r + delta.dr][c + delta.dc] = dungeon.cell[r + delta.dr][c + delta.dc] or CORRIDOR
        }
        spec.recurse?.let { delta ->
            collapse(dungeon, r + delta.dr, c + delta.dc, checks)
        }
    }
    return dungeon
}

fun checkTunnel(dungeon: Dungeon, r: Int, c: Int, check: TunnelCheck): Boolean {
    for (delta in check.corridor) {
        if (cellAt(dungeon, r + delta.dr, c + delta.dc) != CORRIDOR) {
            return false
        }
    }
    for (delta in check.walled) {
        if ((cellAt(dungeon, r + delta.dr, c + delta.dc) and OPENSPACE) != 0) {
            return false
        }
    }
    return true
}

fun fixDoors(dungeon: Dungeon): Dungeon {
    val fixed = mutableSetOf<Pair<Int, Int>>()
    for (room in dungeon.rooms.filterNotNull()) {
        val directions = room.doors.keys.toList().sortedBy { it.ordinal }
        for (dir in directions) {
            val shiny = mutableListOf<Door>()
            val doors = room.doors[dir].orEmpty()
            for (door in doors) {
                val doorCell = dungeon.cell[door.row][door.col]
                if ((doorCell and OPENSPACE) == 0) {
                    continue
                }

                val key = door.row to door.col
                if (!fixed.add(key)) {
                    shiny.add(door)
                } else {
                    door.outId?.let { outId ->
                        dungeon.rooms.getOrNull(outId)?.let { outRoom ->
                            outRoom.doors.getOrPut(dir.opposite) { mutableListOf() }.add(door)
                        }
                    }
                    shiny.add(door)
                }
            }
            if (shiny.isNotEmpty()) {
                room.doors[dir] = shiny
                dungeon.allDoors.addAll(shiny)
            } else {
                room.doors.remove(dir)
            }
        }
    }
    return dungeon
}

fun emptyBlocks(dungeon: Dungeon): Dungeon {
    for (r in 0..dungeon.nRows) {
        for (c in 0..dungeon.nCols) {
            if ((dungeon.cell[r][c] and BLOCKED) != 0) {
                dungeon.cell[r][c] = NOTHING
            }
        }
    }
    return dungeon
}

fun scaleDungeon(dungeon: Dungeon): ImageInfo {
    val cellSize = dungeon.cellSize
    val width = ((dungeon.nCols + 1) * cellSize) + 1
    val height = ((dungeon.nRows + 1) * cellSize) + 1
    val font = when {
        cellSize > 16 -> Font(Font.MONOSPACED, Font.PLAIN, 14)
        cellSize > 12 -> Font(Font.MONOSPACED, Font.PLAIN, 10)
        else -> Font(Font.MONOSPACED, Font.PLAIN, 8)
    }

    val metricsImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
    val metricsGraphics = metricsImage.createGraphics()
    metricsGraphics.font = font
    val metrics = metricsGraphics.fontMetrics
    val charW = metrics.charWidth('0')
    val charH = metrics.height
    val charX = ((cellSize - charW) / 2) + 1
    val charY = ((cellSize - charH) / 2) + metrics.ascent
    metricsGraphics.dispose()

    return ImageInfo(
        cellSize = cellSize,
        mapStyle = dungeon.mapStyle,
        width = width,
        height = height,
        maxX = width - 1,
        maxY = height - 1,
        font = font,
        charW = charW,
        charH = charH,
        charX = charX,
        charY = charY,
    )
}

fun getPalette(image: ImageInfo): Palette {
    return when (image.mapStyle) {
        "Standard" -> Palette(
            fill = Color(0x00, 0x00, 0x00),
            open = Color(0xFF, 0xFF, 0xFF),
            openGrid = Color(0xCC, 0xCC, 0xCC),
            wall = Color(0x00, 0x00, 0x00),
            door = Color(0x00, 0x00, 0x00),
            label = Color(0x00, 0x00, 0x00),
            stair = Color(0x00, 0x00, 0x00),
        )
        else -> Palette(
            fill = Color(0x00, 0x00, 0x00),
            open = Color(0xFF, 0xFF, 0xFF),
            openGrid = Color(0xCC, 0xCC, 0xCC),
            wall = Color(0x00, 0x00, 0x00),
            door = Color(0x00, 0x00, 0x00),
            label = Color(0x00, 0x00, 0x00),
            stair = Color(0x00, 0x00, 0x00),
        )
    }
}

fun getColor(palette: Palette, key: String): Color? {
    var current: String? = key
    while (current != null) {
        val color = when (current) {
            "door" -> palette.door
            "label" -> palette.label
            "stair" -> palette.stair
            "wall" -> palette.wall
            "fill" -> palette.fill
            "black" -> palette.black
            "white" -> palette.white
            "open" -> palette.open
            else -> null
        }
        if (color != null) {
            return color
        }
        current = colorChain[current]
    }
    return null
}

fun withGraphics(image: BufferedImage, block: (Graphics2D) -> Unit) {
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        block(graphics)
    } finally {
        graphics.dispose()
    }
}

fun deepCopy(image: BufferedImage): BufferedImage {
    val copy = BufferedImage(image.width, image.height, image.type)
    withGraphics(copy) { g -> g.drawImage(image, 0, 0, null) }
    return copy
}

fun baseLayer(dungeon: Dungeon, image: ImageInfo, palette: Palette): BufferedImage {
    val layer = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    withGraphics(layer) { g ->
        g.color = palette.open
        g.fillRect(0, 0, image.width, image.height)
        palette.openGrid?.let { imageGrid(dungeon, image, it, g) }
    }
    return layer
}

fun fillImage(dungeon: Dungeon, image: ImageInfo, palette: Palette): BufferedImage {
    val canvas = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    withGraphics(canvas) { g ->
        g.color = palette.fill
        g.fillRect(0, 0, image.width, image.height)
    }
    return canvas
}

fun imageGrid(dungeon: Dungeon, image: ImageInfo, color: Color, graphics: Graphics2D) {
    when (dungeon.grid) {
        "None" -> Unit
        "Hex" -> hexGrid(image, color, graphics)
        else -> squareGrid(image, color, graphics)
    }
}

fun squareGrid(image: ImageInfo, color: Color, graphics: Graphics2D) {
    graphics.color = color
    val dim = image.cellSize
    var x = 0
    while (x <= image.maxX) {
        graphics.drawLine(x, 0, x, image.maxY)
        x += dim
    }
    var y = 0
    while (y <= image.maxY) {
        graphics.drawLine(0, y, image.maxX, y)
        y += dim
    }
}

fun hexGrid(image: ImageInfo, color: Color, graphics: Graphics2D) {
    val dim = image.cellSize.toDouble()
    val dy = dim / 2.0
    val dx = dim / 3.4641016151
    val nCol = (image.width / (3 * dx)).toInt()
    val nRow = (image.height / dy).toInt()
    val previousHint = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.color = color

    for (i in 0 until nCol) {
        val x1 = (i * (3 * dx)).toInt()
        val x2 = (x1 + dx).toInt()
        val x3 = (x1 + (3 * dx)).toInt()
        for (j in 0 until nRow) {
            val y1 = (j * dy).toInt()
            val y2 = (y1 + dy).toInt()
            if ((i + j) % 2 == 1) {
                graphics.drawLine(x1, y1, x2, y2)
                graphics.drawLine(x2, y2, x3, y2)
            } else {
                graphics.drawLine(x2, y1, x1, y2)
            }
        }
    }

    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousHint)
}

fun openCells(dungeon: Dungeon, image: ImageInfo, baseLayer: BufferedImage, canvas: BufferedImage) {
    withGraphics(canvas) { g ->
        val dim = image.cellSize
        for (r in 0..dungeon.nRows) {
            val y1 = r * dim
            val y2 = y1 + dim + 1
            for (c in 0..dungeon.nCols) {
                if ((dungeon.cell[r][c] and OPENSPACE) == 0) {
                    continue
                }
                val x1 = c * dim
                val x2 = x1 + dim + 1
                g.drawImage(baseLayer, x1, y1, x2, y2, x1, y1, x2, y2, null)
            }
        }
    }
}

fun imageWalls(dungeon: Dungeon, image: ImageInfo, palette: Palette, canvas: BufferedImage) {
    val wallColor = palette.wall
    withGraphics(canvas) { g ->
        g.color = wallColor
        val dim = image.cellSize
        for (r in 0..dungeon.nRows) {
            val y1 = r * dim
            val y2 = y1 + dim
            for (c in 0..dungeon.nCols) {
                if ((dungeon.cell[r][c] and OPENSPACE) == 0) {
                    continue
                }
                val x1 = c * dim
                val x2 = x1 + dim

                if ((cellAt(dungeon, r - 1, c - 1) and OPENSPACE) == 0) {
                    canvas.setRGB(x1, y1, wallColor.rgb)
                }
                if ((cellAt(dungeon, r - 1, c) and OPENSPACE) == 0) {
                    g.drawLine(x1, y1, x2, y1)
                }
                if ((cellAt(dungeon, r, c - 1) and OPENSPACE) == 0) {
                    g.drawLine(x1, y1, x1, y2)
                }
                if ((cellAt(dungeon, r, c + 1) and OPENSPACE) == 0) {
                    g.drawLine(x2, y1, x2, y2)
                }
                if ((cellAt(dungeon, r + 1, c) and OPENSPACE) == 0) {
                    g.drawLine(x1, y2, x2, y2)
                }
            }
        }
    }
}

fun imageDoors(dungeon: Dungeon, image: ImageInfo, palette: Palette, canvas: BufferedImage) {
    if (dungeon.allDoors.isEmpty()) {
        return
    }

    val archColor = getColor(palette, "wall") ?: palette.wall
    val doorColor = getColor(palette, "door") ?: palette.door

    withGraphics(canvas) { g ->
        val dim = image.cellSize
        val aPx = dim / 6
        val dTx = dim / 4
        val tTx = dim / 3

        for (door in dungeon.allDoors) {
            val r = door.row
            val c = door.col
            val y1 = r * dim
            val y2 = y1 + dim
            val x1 = c * dim
            val x2 = x1 + dim

            val vertical = (cellAt(dungeon, r, c - 1) and OPENSPACE) != 0
            val xc = if (vertical) (x1 + x2) / 2 else null
            val yc = if (!vertical) (y1 + y2) / 2 else null
            val attr = doorAttr(door)

            if (attr["wall"] == true) {
                g.color = archColor
                if (xc != null) {
                    g.drawLine(xc, y1, xc, y2)
                } else if (yc != null) {
                    g.drawLine(x1, yc, x2, yc)
                }
            }

            if (attr["secret"] == true) {
                g.color = doorColor
                if (xc != null) {
                    val yMid = (y1 + y2) / 2
                    g.drawLine(xc - 1, yMid - dTx, xc + 2, yMid - dTx)
                    g.drawLine(xc - 2, yMid - dTx + 1, xc - 2, yMid - 1)
                    g.drawLine(xc - 1, yMid, xc + 1, yMid)
                    g.drawLine(xc + 2, yMid + 1, xc + 2, yMid + dTx - 1)
                    g.drawLine(xc - 2, yMid + dTx, xc + 1, yMid + dTx)
                } else if (yc != null) {
                    val xMid = (x1 + x2) / 2
                    g.drawLine(xMid - dTx, yc - 2, xMid - dTx, yc + 1)
                    g.drawLine(xMid - dTx + 1, yc + 2, xMid - 1, yc + 2)
                    g.drawLine(xMid, yc - 1, xMid, yc + 1)
                    g.drawLine(xMid + 1, yc - 2, xMid + dTx - 1, yc - 2)
                    g.drawLine(xMid + dTx, yc - 1, xMid + dTx, yc + 2)
                }
            }

            if (attr["arch"] == true) {
                g.color = archColor
                if (xc != null) {
                    g.fillRect(xc - 1, y1, 3, aPx + 1)
                    g.fillRect(xc - 1, y2 - aPx, 3, aPx + 1)
                } else if (yc != null) {
                    g.fillRect(x1, yc - 1, aPx + 1, 3)
                    g.fillRect(x2 - aPx, yc - 1, aPx + 1, 3)
                }
            }

            if (attr["door"] == true) {
                g.color = doorColor
                if (xc != null) {
                    g.drawRect(xc - dTx, y1 + aPx + 1, dTx * 2, (y2 - aPx - 1) - (y1 + aPx + 1))
                } else if (yc != null) {
                    g.drawRect(x1 + aPx + 1, yc - dTx, (x2 - aPx - 1) - (x1 + aPx + 1), dTx * 2)
                }
            }

            if (attr["lock"] == true) {
                g.color = doorColor
                if (xc != null) {
                    g.drawLine(xc, y1 + aPx + 1, xc, y2 - aPx - 1)
                } else if (yc != null) {
                    g.drawLine(x1 + aPx + 1, yc, x2 - aPx - 1, yc)
                }
            }

            if (attr["trap"] == true) {
                g.color = doorColor
                if (xc != null) {
                    val yMid = (y1 + y2) / 2
                    g.drawLine(xc - tTx, yMid, xc + tTx, yMid)
                } else if (yc != null) {
                    val xMid = (x1 + x2) / 2
                    g.drawLine(xMid, yc - tTx, xMid, yc + tTx)
                }
            }

            if (attr["portc"] == true) {
                g.color = doorColor
                if (xc != null) {
                    var y = y1 + aPx + 2
                    while (y < y2 - aPx) {
                        canvas.setRGB(xc, y, doorColor.rgb)
                        y += 2
                    }
                } else if (yc != null) {
                    var x = x1 + aPx + 2
                    while (x < x2 - aPx) {
                        canvas.setRGB(x, yc, doorColor.rgb)
                        x += 2
                    }
                }
            }
        }
    }
}

fun doorAttr(door: Door): Map<String, Boolean> {
    val attr = mutableMapOf<String, Boolean>()
    when (door.key) {
        "arch" -> attr["arch"] = true
        "open" -> {
            attr["arch"] = true
            attr["door"] = true
        }
        "lock" -> {
            attr["arch"] = true
            attr["door"] = true
            attr["lock"] = true
        }
        "trap" -> {
            attr["arch"] = true
            attr["door"] = true
            attr["trap"] = true
            if (door.desc?.contains("Lock") == true) {
                attr["lock"] = true
            }
        }
        "secret" -> {
            attr["wall"] = true
            attr["arch"] = true
            attr["secret"] = true
        }
        "portc" -> {
            attr["arch"] = true
            attr["portc"] = true
        }
    }
    return attr
}

fun imageLabels(dungeon: Dungeon, image: ImageInfo, palette: Palette, canvas: BufferedImage) {
    val color = getColor(palette, "label") ?: palette.label
    withGraphics(canvas) { g ->
        g.color = color
        g.font = image.font
        val dim = image.cellSize
        for (r in 0..dungeon.nRows) {
            for (c in 0..dungeon.nCols) {
                if ((dungeon.cell[r][c] and OPENSPACE) == 0) {
                    continue
                }
                val char = cellLabel(dungeon.cell[r][c]) ?: continue
                val x = (c * dim) + image.charX
                val y = (r * dim) + image.charY
                g.drawString(char.toString(), x, y)
            }
        }
    }
}

fun cellLabel(cell: Int): Char? {
    val code = (cell ushr 24) and 0xFF
    if (code == 0) {
        return null
    }
    val char = code.toChar()
    return if (char.isDigit()) char else null
}

fun imageStairs(dungeon: Dungeon, image: ImageInfo, palette: Palette, canvas: BufferedImage) {
    if (dungeon.stairs.isEmpty()) {
        return
    }

    val color = getColor(palette, "stair") ?: palette.stair
    withGraphics(canvas) { g ->
        g.color = color
        val dim = image.cellSize
        val sPx = dim / 2
        val tPx = (dim / 20) + 2

        for (stair in dungeon.stairs) {
            when {
                stair.nextRow > stair.row -> {
                    val xc = ((stair.col + 0.5) * dim).toInt()
                    val y1 = stair.row * dim
                    val y2 = (stair.nextRow + 1) * dim
                    var y = y1
                    while (y < y2) {
                        val dx = if (stair.key == "down") (((y - y1).toDouble() / (y2 - y1)) * sPx).toInt() else sPx
                        g.drawLine(xc - dx, y, xc + dx, y)
                        y += tPx
                    }
                }
                stair.nextRow < stair.row -> {
                    val xc = ((stair.col + 0.5) * dim).toInt()
                    val y1 = (stair.row + 1) * dim
                    val y2 = stair.nextRow * dim
                    var y = y1
                    while (y > y2) {
                        val dx = if (stair.key == "down") (((y - y1).toDouble() / (y2 - y1)) * sPx).toInt() else sPx
                        g.drawLine(xc - dx, y, xc + dx, y)
                        y -= tPx
                    }
                }
                stair.nextCol > stair.col -> {
                    val x1 = stair.col * dim
                    val x2 = (stair.nextCol + 1) * dim
                    val yc = ((stair.row + 0.5) * dim).toInt()
                    var x = x1
                    while (x < x2) {
                        val dy = if (stair.key == "down") (((x - x1).toDouble() / (x2 - x1)) * sPx).toInt() else sPx
                        g.drawLine(x, yc - dy, x, yc + dy)
                        x += tPx
                    }
                }
                stair.nextCol < stair.col -> {
                    val x1 = (stair.col + 1) * dim
                    val x2 = stair.nextCol * dim
                    val yc = ((stair.row + 0.5) * dim).toInt()
                    var x = x1
                    while (x > x2) {
                        val dy = if (stair.key == "down") (((x - x1).toDouble() / (x2 - x1)) * sPx).toInt() else sPx
                        g.drawLine(x, yc - dy, x, yc + dy)
                        x -= tPx
                    }
                }
            }
        }
    }
}

fun cellAt(dungeon: Dungeon, r: Int, c: Int): Int {
    if (r !in 0..dungeon.nRows || c !in 0..dungeon.nCols) {
        return NOTHING
    }
    return dungeon.cell[r][c]
}

fun imageDungeon(dungeon: Dungeon): String {
    val image = scaleDungeon(dungeon)
    val palette = getPalette(image)
    val baseLayer = baseLayer(dungeon, image, palette)
    val canvas = fillImage(dungeon, image, palette)

    openCells(dungeon, image, baseLayer, canvas)
    imageWalls(dungeon, image, palette, canvas)
    imageDoors(dungeon, image, palette, canvas)
    imageLabels(dungeon, image, palette, canvas)
    imageStairs(dungeon, image, palette, canvas)

    val gif = File("${dungeon.seed}.gif")
    if (ImageIO.write(canvas, "gif", gif)) {
        return gif.name
    }

    val png = File("${dungeon.seed}.png")
    ImageIO.write(canvas, "png", png)
    return png.name
}

val opts = getOpts()
val dungeon = createDungeon(opts)
println(imageDungeon(dungeon))
