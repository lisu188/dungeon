package pl.andrz.dungeon

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class DungeonGenerator(private val options: DungeonOptions = DungeonOptions()) {
    fun generate(): Dungeon = createDungeon(options)
}

private fun createDungeon(opts: DungeonOptions): Dungeon =
    Dungeon(opts)
        .let(::initCells)
        .let(::emplaceRooms)
        .let(::openRooms)
        .let(::labelRooms)
        .let(::corridors)
        .let { dungeon -> if (dungeon.addStairs > 0) emplaceStairs(dungeon) else dungeon }
        .let(::cleanDungeon)

private fun initCells(dungeon: Dungeon): Dungeon {
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

private fun maskCells(dungeon: Dungeon, mask: List<List<Int>>): Dungeon {
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

private fun roundMask(dungeon: Dungeon): Dungeon {
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

private fun emplaceRooms(dungeon: Dungeon): Dungeon =
    if (dungeon.roomLayout == "Packed") packRooms(dungeon) else scatterRooms(dungeon)

private fun packRooms(dungeon: Dungeon): Dungeon {
    for (i in 0 until dungeon.nI) {
        val r = (i * 2) + 1
        for (j in 0 until dungeon.nJ) {
            val c = (j * 2) + 1
            if (dungeon.cell[r][c] has ROOM) {
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

private fun scatterRooms(dungeon: Dungeon): Dungeon {
    repeat(allocRooms(dungeon)) {
        emplaceRoom(dungeon, null)
    }
    return dungeon
}

private fun allocRooms(dungeon: Dungeon): Int {
    val dungeonArea = dungeon.nCols * dungeon.nRows
    val roomArea = dungeon.roomMax * dungeon.roomMax
    return dungeonArea / roomArea
}

private fun emplaceRoom(dungeon: Dungeon, initialProto: RoomProto?): Dungeon {
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
            if (dungeon.cell[r][c] has ENTRANCE) {
                dungeon.cell[r][c] = dungeon.cell[r][c] withoutFlag ESPACE
            } else if (dungeon.cell[r][c] has PERIMETER) {
                dungeon.cell[r][c] = dungeon.cell[r][c] withoutFlag PERIMETER
            }
            dungeon.cell[r][c] = dungeon.cell[r][c] withFlag ROOM withFlag (roomId shl 6)
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

private fun setRoom(dungeon: Dungeon, proto: RoomProto): RoomProto {
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

private fun soundRoom(dungeon: Dungeon, r1: Int, c1: Int, r2: Int, c2: Int): SoundRoomResult {
    val hits = mutableMapOf<Int, Int>()
    for (r in r1..r2) {
        for (c in c1..c2) {
            val cell = dungeon.cell[r][c]
            if (cell has BLOCKED) {
                return SoundRoomResult(blocked = true)
            }
            if (cell has ROOM) {
                val id = cell.roomIdValue
                hits[id] = (hits[id] ?: 0) + 1
            }
        }
    }
    return SoundRoomResult(hits = hits)
}

private fun openRooms(dungeon: Dungeon): Dungeon {
    for (id in 1..dungeon.nRooms) {
        val room = dungeon.rooms.getOrNull(id) ?: continue
        openRoom(dungeon, room)
    }
    dungeon.connect.clear()
    return dungeon
}

private fun openRoom(dungeon: Dungeon, room: Room): Dungeon {
    val sills = doorSills(dungeon, room).toMutableList()
    if (sills.isEmpty()) {
        return dungeon
    }

    val nOpens = allocOpens(room, dungeon)
    var opened = 0
    while (opened < nOpens && sills.isNotEmpty()) {
        val sill = sills.removeAt(dungeon.nextInt(sills.size))
        val doorCell = dungeon.cell[sill.doorR][sill.doorC]
        if (doorCell has DOORSPACE) {
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
            dungeon.cell[r][c] = dungeon.cell[r][c] withoutFlag PERIMETER withFlag ENTRANCE
        }

        val doorType = doorType(dungeon)
        val door = Door(row = sill.doorR, col = sill.doorC, key = "", type = "", outId = outId)
        when (doorType) {
            ARCH -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] withFlag ARCH
                door.key = "arch"
                door.type = "Archway"
            }
            DOOR -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] withFlag DOOR withFlag glyph('o')
                door.key = "open"
                door.type = "Unlocked Door"
            }
            LOCKED -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] withFlag LOCKED withFlag glyph('x')
                door.key = "lock"
                door.type = "Locked Door"
            }
            TRAPPED -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] withFlag TRAPPED withFlag glyph('t')
                door.key = "trap"
                door.type = "Trapped Door"
            }
            SECRET -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] withFlag SECRET withFlag glyph('s')
                door.key = "secret"
                door.type = "Secret Door"
            }
            else -> {
                dungeon.cell[sill.doorR][sill.doorC] = dungeon.cell[sill.doorR][sill.doorC] withFlag PORTC withFlag glyph('#')
                door.key = "portc"
                door.type = "Portcullis"
            }
        }
        room.doors.getOrPut(sill.dir) { mutableListOf() }.add(door)
        opened += 1
    }
    return dungeon
}

private fun allocOpens(room: Room, dungeon: Dungeon): Int {
    val roomH = ((room.south - room.north) / 2) + 1
    val roomW = ((room.east - room.west) / 2) + 1
    val flumph = sqrt((roomW * roomH).toDouble()).toInt()
    return flumph + dungeon.nextInt(flumph)
}

private fun doorSills(dungeon: Dungeon, room: Room): List<DoorSill> =
    buildList {
        if (room.north >= 3) {
            for (c in room.west..room.east step 2) {
                checkSill(dungeon, room, room.north, c, Direction.NORTH)?.let(::add)
            }
        }
        if (room.south <= dungeon.nRows - 3) {
            for (c in room.west..room.east step 2) {
                checkSill(dungeon, room, room.south, c, Direction.SOUTH)?.let(::add)
            }
        }
        if (room.west >= 3) {
            for (r in room.north..room.south step 2) {
                checkSill(dungeon, room, r, room.west, Direction.WEST)?.let(::add)
            }
        }
        if (room.east <= dungeon.nCols - 3) {
            for (r in room.north..room.south step 2) {
                checkSill(dungeon, room, r, room.east, Direction.EAST)?.let(::add)
            }
        }
    }.let { shuffleList(it, dungeon) }

private fun checkSill(dungeon: Dungeon, room: Room, sillR: Int, sillC: Int, dir: Direction): DoorSill? {
    val doorR = sillR + dir.di
    val doorC = sillC + dir.dj
    val doorCell = dungeon.cell[doorR][doorC]
    if (doorCell hasNot PERIMETER || doorCell has BLOCK_DOOR) {
        return null
    }

    val outR = doorR + dir.di
    val outC = doorC + dir.dj
    val outCell = dungeon.cell[outR][outC]
    if (outCell has BLOCKED) {
        return null
    }

    var outId: Int? = null
    if (outCell has ROOM) {
        outId = outCell.roomIdValue
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

private fun <T> shuffleList(list: List<T>, dungeon: Dungeon): List<T> {
    val copy = list.toMutableList()
    for (i in copy.indices.reversed()) {
        val j = dungeon.nextInt(i + 1)
        val tmp = copy[i]
        copy[i] = copy[j]
        copy[j] = tmp
    }
    return copy
}

private fun doorType(dungeon: Dungeon): Int =
    when (dungeon.nextInt(110)) {
        in 0 until 15 -> ARCH
        in 15 until 60 -> DOOR
        in 60 until 75 -> LOCKED
        in 75 until 90 -> TRAPPED
        in 90 until 100 -> SECRET
        else -> PORTC
    }

private fun labelRooms(dungeon: Dungeon): Dungeon {
    for (id in 1..dungeon.nRooms) {
        val room = dungeon.rooms.getOrNull(id) ?: continue
        val label = room.id.toString()
        val len = label.length
        val labelR = (room.north + room.south) / 2
        val labelC = ((room.west + room.east - len) / 2) + 1
        for ((offset, ch) in label.withIndex()) {
            dungeon.cell[labelR][labelC + offset] = dungeon.cell[labelR][labelC + offset] withFlag glyph(ch)
        }
    }
    return dungeon
}

private fun corridors(dungeon: Dungeon): Dungeon {
    for (i in 1 until dungeon.nI) {
        val r = (i * 2) + 1
        for (j in 1 until dungeon.nJ) {
            val c = (j * 2) + 1
            if (dungeon.cell[r][c] has CORRIDOR) {
                continue
            }
            tunnel(dungeon, i, j, null)
        }
    }
    return dungeon
}

private fun tunnel(dungeon: Dungeon, i: Int, j: Int, lastDir: Direction?): Dungeon {
    for (dir in tunnelDirs(dungeon, lastDir)) {
        if (openTunnel(dungeon, i, j, dir)) {
            tunnel(dungeon, i + dir.di, j + dir.dj, dir)
        }
    }
    return dungeon
}

private fun tunnelDirs(dungeon: Dungeon, lastDir: Direction?): List<Direction> {
    val probability = corridorLayouts[dungeon.corridorLayout] ?: 0
    val dirs = shuffleList(Direction.entries.toList(), dungeon).toMutableList()
    if (lastDir != null && probability > 0 && dungeon.nextInt(100) < probability) {
        dirs.add(0, lastDir)
    }
    return dirs
}

private fun openTunnel(dungeon: Dungeon, i: Int, j: Int, dir: Direction): Boolean {
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

private fun soundTunnel(dungeon: Dungeon, midR: Int, midC: Int, nextR: Int, nextC: Int): Boolean {
    if (nextR < 0 || nextR > dungeon.nRows || nextC < 0 || nextC > dungeon.nCols) {
        return false
    }
    val r1 = min(midR, nextR)
    val r2 = max(midR, nextR)
    val c1 = min(midC, nextC)
    val c2 = max(midC, nextC)
    for (r in r1..r2) {
        for (c in c1..c2) {
            if (dungeon.cell[r][c] has BLOCK_CORR) {
                return false
            }
        }
    }
    return true
}

private fun delveTunnel(dungeon: Dungeon, thisR: Int, thisC: Int, nextR: Int, nextC: Int): Boolean {
    val r1 = min(thisR, nextR)
    val r2 = max(thisR, nextR)
    val c1 = min(thisC, nextC)
    val c2 = max(thisC, nextC)
    for (r in r1..r2) {
        for (c in c1..c2) {
            dungeon.cell[r][c] = dungeon.cell[r][c] withoutFlag ENTRANCE withFlag CORRIDOR
        }
    }
    return true
}

private fun emplaceStairs(dungeon: Dungeon): Dungeon {
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
            dungeon.cell[stair.row][stair.col] = dungeon.cell[stair.row][stair.col] withFlag STAIR_DN withFlag glyph('d')
            stair.key = "down"
        } else {
            dungeon.cell[stair.row][stair.col] = dungeon.cell[stair.row][stair.col] withFlag STAIR_UP withFlag glyph('u')
            stair.key = "up"
        }
        dungeon.stairs.add(stair)
    }
    return dungeon
}

private fun stairEnds(dungeon: Dungeon): List<Stair> =
    buildList {
        for (i in 0 until dungeon.nI) {
            val r = (i * 2) + 1
            for (j in 0 until dungeon.nJ) {
                val c = (j * 2) + 1
                if (dungeon.cell[r][c] != CORRIDOR || dungeon.cell[r][c] has STAIRS) {
                    continue
                }

                for (dir in Direction.entries) {
                    val spec = stairEnd[dir] ?: continue
                    if (checkTunnel(dungeon, r, c, spec)) {
                        val next = spec.next!!
                        add(
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
    }

private fun cleanDungeon(dungeon: Dungeon): Dungeon {
    if (dungeon.removeDeadends > 0) {
        removeDeadends(dungeon)
    }
    fixDoors(dungeon)
    emptyBlocks(dungeon)
    return dungeon
}

private fun removeDeadends(dungeon: Dungeon): Dungeon =
    collapseTunnels(dungeon, dungeon.removeDeadends, closeEnd)

private fun collapseTunnels(dungeon: Dungeon, percent: Int, checks: Map<Direction, TunnelCheck>): Dungeon {
    if (percent <= 0) {
        return dungeon
    }
    val collapseAll = percent == 100
    for (i in 0 until dungeon.nI) {
        val r = (i * 2) + 1
        for (j in 0 until dungeon.nJ) {
            val c = (j * 2) + 1
            val cell = dungeon.cell[r][c]
            if (cell hasNot OPENSPACE || cell has STAIRS) {
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

private fun collapse(dungeon: Dungeon, r: Int, c: Int, checks: Map<Direction, TunnelCheck>): Dungeon {
    if (dungeon.cell[r][c] hasNot OPENSPACE) {
        return dungeon
    }
    for (dir in Direction.entries) {
        val spec = checks[dir] ?: continue
        if (!checkTunnel(dungeon, r, c, spec)) {
            continue
        }
        for (delta in spec.close) {
            dungeon.cell[r + delta.dr][c + delta.dc] = NOTHING
        }
        spec.open?.let { delta ->
            dungeon.cell[r + delta.dr][c + delta.dc] = dungeon.cell[r + delta.dr][c + delta.dc] withFlag CORRIDOR
        }
        spec.recurse?.let { delta ->
            collapse(dungeon, r + delta.dr, c + delta.dc, checks)
        }
    }
    return dungeon
}

private fun checkTunnel(dungeon: Dungeon, r: Int, c: Int, check: TunnelCheck): Boolean {
    for (delta in check.corridor) {
        if (dungeon.cellAt(r + delta.dr, c + delta.dc) != CORRIDOR) {
            return false
        }
    }
    for (delta in check.walled) {
        if (dungeon.cellAt(r + delta.dr, c + delta.dc) has OPENSPACE) {
            return false
        }
    }
    return true
}

private fun fixDoors(dungeon: Dungeon): Dungeon {
    val fixed = mutableSetOf<Pair<Int, Int>>()
    for (room in dungeon.rooms.filterNotNull()) {
        val directions = room.doors.keys.toList().sortedBy { it.ordinal }
        for (dir in directions) {
            val shiny = mutableListOf<Door>()
            val doors = room.doors[dir].orEmpty()
            for (door in doors) {
                val doorCell = dungeon.cell[door.row][door.col]
                if (doorCell hasNot OPENSPACE) {
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

private fun emptyBlocks(dungeon: Dungeon): Dungeon {
    for (r in 0..dungeon.nRows) {
        for (c in 0..dungeon.nCols) {
            if (dungeon.cell[r][c] has BLOCKED) {
                dungeon.cell[r][c] = NOTHING
            }
        }
    }
    return dungeon
}
