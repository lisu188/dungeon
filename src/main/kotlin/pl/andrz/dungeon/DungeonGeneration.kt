package pl.andrz.dungeon

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class DungeonGenerator(private val options: DungeonOptions = DungeonOptions()) {
    fun generate(): Dungeon = createDungeon(options)
}

private fun createDungeon(options: DungeonOptions): Dungeon =
    Dungeon(options)
        .let(::initCells)
        .let(::emplaceRooms)
        .let(::openRooms)
        .let(::labelRooms)
        .let(::corridors)
        .let { dungeon -> if (dungeon.addStairs > 0) emplaceStairs(dungeon) else dungeon }
        .let(::cleanDungeon)

private fun initCells(dungeon: Dungeon): Dungeon {
    for (row in 0..dungeon.nRows) {
        for (column in 0..dungeon.nCols) {
            dungeon.cell[row][column] = NOTHING
        }
    }

    val layoutMask = dungeonLayouts[dungeon.dungeonLayout]
    if (layoutMask != null) {
        maskCells(dungeon, layoutMask)
    } else if (dungeon.dungeonLayout == "Round") {
        roundMask(dungeon)
    }
    return dungeon
}

private fun maskCells(dungeon: Dungeon, mask: List<List<Int>>): Dungeon {
    val rowScale = mask.size.toDouble() / (dungeon.nRows + 1)
    val columnScale = mask.first().size.toDouble() / (dungeon.nCols + 1)
    for (row in 0..dungeon.nRows) {
        for (column in 0..dungeon.nCols) {
            val maskRow = min(mask.size - 1, (row * rowScale).toInt())
            val maskColumn = min(mask.first().size - 1, (column * columnScale).toInt())
            if (mask[maskRow][maskColumn] == 0) {
                dungeon.cell[row][column] = BLOCKED
            }
        }
    }
    return dungeon
}

private fun roundMask(dungeon: Dungeon): Dungeon {
    val centerRow = dungeon.nRows / 2
    val centerColumn = dungeon.nCols / 2
    for (row in 0..dungeon.nRows) {
        for (column in 0..dungeon.nCols) {
            val distanceFromCenter = sqrt(
                ((row - centerRow) * (row - centerRow) + (column - centerColumn) * (column - centerColumn)).toDouble()
            )
            if (distanceFromCenter > centerColumn) {
                dungeon.cell[row][column] = BLOCKED
            }
        }
    }
    return dungeon
}

private fun emplaceRooms(dungeon: Dungeon): Dungeon =
    if (dungeon.roomLayout == "Packed") packRooms(dungeon) else scatterRooms(dungeon)

private fun packRooms(dungeon: Dungeon): Dungeon {
    for (roomRowIndex in 0 until dungeon.nI) {
        val row = (roomRowIndex * 2) + 1
        for (roomColumnIndex in 0 until dungeon.nJ) {
            val column = (roomColumnIndex * 2) + 1
            if (dungeon.cell[row][column] has ROOM) {
                continue
            }
            if ((roomRowIndex == 0 || roomColumnIndex == 0) && dungeon.nextInt(2) != 0) {
                continue
            }
            emplaceRoom(dungeon, RoomProto(i = roomRowIndex, j = roomColumnIndex))
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
    val largestRoomArea = dungeon.roomMax * dungeon.roomMax
    return dungeonArea / largestRoomArea
}

private fun emplaceRoom(dungeon: Dungeon, initialProto: RoomProto?): Dungeon {
    if (dungeon.nRooms == 999) {
        return dungeon
    }

    val roomPrototype = setRoom(dungeon, initialProto ?: RoomProto())
    val topRow = (roomPrototype.i!! * 2) + 1
    val leftColumn = (roomPrototype.j!! * 2) + 1
    val bottomRow = ((roomPrototype.i!! + roomPrototype.height!!) * 2) - 1
    val rightColumn = ((roomPrototype.j!! + roomPrototype.width!!) * 2) - 1

    if (topRow < 1 || bottomRow > dungeon.maxRow || leftColumn < 1 || rightColumn > dungeon.maxCol) {
        return dungeon
    }

    val collisionResult = soundRoom(dungeon, topRow, leftColumn, bottomRow, rightColumn)
    if (collisionResult.blocked || collisionResult.hits.isNotEmpty()) {
        return dungeon
    }

    val roomId = dungeon.nRooms + 1
    dungeon.nRooms = roomId
    dungeon.lastRoomId = roomId

    for (row in topRow..bottomRow) {
        for (column in leftColumn..rightColumn) {
            if (dungeon.cell[row][column] has ENTRANCE) {
                dungeon.cell[row][column] = dungeon.cell[row][column] withoutFlag ESPACE
            } else if (dungeon.cell[row][column] has PERIMETER) {
                dungeon.cell[row][column] = dungeon.cell[row][column] withoutFlag PERIMETER
            }
            dungeon.cell[row][column] = dungeon.cell[row][column] withFlag ROOM withFlag (roomId shl 6)
        }
    }

    val roomHeight = ((bottomRow - topRow) + 1) * 10
    val roomWidth = ((rightColumn - leftColumn) + 1) * 10
    val room = Room(
        id = roomId,
        row = topRow,
        col = leftColumn,
        north = topRow,
        south = bottomRow,
        west = leftColumn,
        east = rightColumn,
        height = roomHeight,
        width = roomWidth,
        area = roomHeight * roomWidth,
    )
    while (dungeon.rooms.size <= roomId) {
        dungeon.rooms.add(null)
    }
    dungeon.rooms[roomId] = room

    for (row in (topRow - 1)..(bottomRow + 1)) {
        if ((dungeon.cell[row][leftColumn - 1] and (ROOM or ENTRANCE)) == 0) {
            dungeon.cell[row][leftColumn - 1] = dungeon.cell[row][leftColumn - 1] or PERIMETER
        }
        if ((dungeon.cell[row][rightColumn + 1] and (ROOM or ENTRANCE)) == 0) {
            dungeon.cell[row][rightColumn + 1] = dungeon.cell[row][rightColumn + 1] or PERIMETER
        }
    }
    for (column in (leftColumn - 1)..(rightColumn + 1)) {
        if ((dungeon.cell[topRow - 1][column] and (ROOM or ENTRANCE)) == 0) {
            dungeon.cell[topRow - 1][column] = dungeon.cell[topRow - 1][column] or PERIMETER
        }
        if ((dungeon.cell[bottomRow + 1][column] and (ROOM or ENTRANCE)) == 0) {
            dungeon.cell[bottomRow + 1][column] = dungeon.cell[bottomRow + 1][column] or PERIMETER
        }
    }

    return dungeon
}

private fun setRoom(dungeon: Dungeon, roomPrototype: RoomProto): RoomProto {
    if (roomPrototype.height == null) {
        roomPrototype.height = if (roomPrototype.i != null) {
            val remainingRowSlots = max(0, dungeon.nI - dungeon.roomBase - roomPrototype.i!!)
            val availableHeightRange = min(remainingRowSlots, dungeon.roomRadix)
            dungeon.nextInt(availableHeightRange) + dungeon.roomBase
        } else {
            dungeon.nextInt(dungeon.roomRadix) + dungeon.roomBase
        }
    }

    if (roomPrototype.width == null) {
        roomPrototype.width = if (roomPrototype.j != null) {
            val remainingColumnSlots = max(0, dungeon.nJ - dungeon.roomBase - roomPrototype.j!!)
            val availableWidthRange = min(remainingColumnSlots, dungeon.roomRadix)
            dungeon.nextInt(availableWidthRange) + dungeon.roomBase
        } else {
            dungeon.nextInt(dungeon.roomRadix) + dungeon.roomBase
        }
    }

    if (roomPrototype.i == null) {
        roomPrototype.i = dungeon.nextInt(dungeon.nI - roomPrototype.height!!)
    }
    if (roomPrototype.j == null) {
        roomPrototype.j = dungeon.nextInt(dungeon.nJ - roomPrototype.width!!)
    }
    return roomPrototype
}

private fun soundRoom(dungeon: Dungeon, topRow: Int, leftColumn: Int, bottomRow: Int, rightColumn: Int): SoundRoomResult {
    val roomHits = mutableMapOf<Int, Int>()
    for (row in topRow..bottomRow) {
        for (column in leftColumn..rightColumn) {
            val cell = dungeon.cell[row][column]
            if (cell has BLOCKED) {
                return SoundRoomResult(blocked = true)
            }
            if (cell has ROOM) {
                val roomId = cell.roomIdValue
                roomHits[roomId] = (roomHits[roomId] ?: 0) + 1
            }
        }
    }
    return SoundRoomResult(hits = roomHits)
}

private fun openRooms(dungeon: Dungeon): Dungeon {
    for (roomId in 1..dungeon.nRooms) {
        val room = dungeon.rooms.getOrNull(roomId) ?: continue
        openRoom(dungeon, room)
    }
    dungeon.connect.clear()
    return dungeon
}

private fun openRoom(dungeon: Dungeon, room: Room): Dungeon {
    val availableSills = doorSills(dungeon, room).toMutableList()
    if (availableSills.isEmpty()) {
        return dungeon
    }

    val targetOpeningCount = allocOpens(room, dungeon)
    var createdOpeningCount = 0
    while (createdOpeningCount < targetOpeningCount && availableSills.isNotEmpty()) {
        val selectedSill = availableSills.removeAt(dungeon.nextInt(availableSills.size))
        val doorCell = dungeon.cell[selectedSill.doorR][selectedSill.doorC]
        if (doorCell has DOORSPACE) {
            continue
        }

        val connectedRoomId = selectedSill.outId
        if (connectedRoomId != null) {
            val connectionKey = listOf(room.id, connectedRoomId).sorted().joinToString(",")
            if (!dungeon.connect.add(connectionKey)) {
                continue
            }
        }

        for (stepOffset in 0..2) {
            val row = selectedSill.sillR + (selectedSill.dir.di * stepOffset)
            val column = selectedSill.sillC + (selectedSill.dir.dj * stepOffset)
            dungeon.cell[row][column] = dungeon.cell[row][column] withoutFlag PERIMETER withFlag ENTRANCE
        }

        val doorType = doorType(dungeon)
        val placedDoor = Door(row = selectedSill.doorR, col = selectedSill.doorC, key = "", type = "", outId = connectedRoomId)
        when (doorType) {
            ARCH -> {
                dungeon.cell[selectedSill.doorR][selectedSill.doorC] = dungeon.cell[selectedSill.doorR][selectedSill.doorC] withFlag ARCH
                placedDoor.key = "arch"
                placedDoor.type = "Archway"
            }
            DOOR -> {
                dungeon.cell[selectedSill.doorR][selectedSill.doorC] = dungeon.cell[selectedSill.doorR][selectedSill.doorC] withFlag DOOR withFlag glyph('o')
                placedDoor.key = "open"
                placedDoor.type = "Unlocked Door"
            }
            LOCKED -> {
                dungeon.cell[selectedSill.doorR][selectedSill.doorC] = dungeon.cell[selectedSill.doorR][selectedSill.doorC] withFlag LOCKED withFlag glyph('x')
                placedDoor.key = "lock"
                placedDoor.type = "Locked Door"
            }
            TRAPPED -> {
                dungeon.cell[selectedSill.doorR][selectedSill.doorC] = dungeon.cell[selectedSill.doorR][selectedSill.doorC] withFlag TRAPPED withFlag glyph('t')
                placedDoor.key = "trap"
                placedDoor.type = "Trapped Door"
            }
            SECRET -> {
                dungeon.cell[selectedSill.doorR][selectedSill.doorC] = dungeon.cell[selectedSill.doorR][selectedSill.doorC] withFlag SECRET withFlag glyph('s')
                placedDoor.key = "secret"
                placedDoor.type = "Secret Door"
            }
            else -> {
                dungeon.cell[selectedSill.doorR][selectedSill.doorC] = dungeon.cell[selectedSill.doorR][selectedSill.doorC] withFlag PORTC withFlag glyph('#')
                placedDoor.key = "portc"
                placedDoor.type = "Portcullis"
            }
        }
        room.doors.getOrPut(selectedSill.dir) { mutableListOf() }.add(placedDoor)
        createdOpeningCount += 1
    }
    return dungeon
}

private fun allocOpens(room: Room, dungeon: Dungeon): Int {
    val roomHeightInCells = ((room.south - room.north) / 2) + 1
    val roomWidthInCells = ((room.east - room.west) / 2) + 1
    val baseOpeningCount = sqrt((roomWidthInCells * roomHeightInCells).toDouble()).toInt()
    return baseOpeningCount + dungeon.nextInt(baseOpeningCount)
}

private fun doorSills(dungeon: Dungeon, room: Room): List<DoorSill> =
    buildList {
        if (room.north >= 3) {
            for (column in room.west..room.east step 2) {
                checkSill(dungeon, room, room.north, column, Direction.NORTH)?.let(::add)
            }
        }
        if (room.south <= dungeon.nRows - 3) {
            for (column in room.west..room.east step 2) {
                checkSill(dungeon, room, room.south, column, Direction.SOUTH)?.let(::add)
            }
        }
        if (room.west >= 3) {
            for (row in room.north..room.south step 2) {
                checkSill(dungeon, room, row, room.west, Direction.WEST)?.let(::add)
            }
        }
        if (room.east <= dungeon.nCols - 3) {
            for (row in room.north..room.south step 2) {
                checkSill(dungeon, room, row, room.east, Direction.EAST)?.let(::add)
            }
        }
    }.let { shuffleList(it, dungeon) }

private fun checkSill(dungeon: Dungeon, room: Room, sillRow: Int, sillColumn: Int, direction: Direction): DoorSill? {
    val doorRow = sillRow + direction.di
    val doorColumn = sillColumn + direction.dj
    val doorCell = dungeon.cell[doorRow][doorColumn]
    if (doorCell hasNot PERIMETER || doorCell has BLOCK_DOOR) {
        return null
    }

    val adjacentRow = doorRow + direction.di
    val adjacentColumn = doorColumn + direction.dj
    val adjacentCell = dungeon.cell[adjacentRow][adjacentColumn]
    if (adjacentCell has BLOCKED) {
        return null
    }

    var adjacentRoomId: Int? = null
    if (adjacentCell has ROOM) {
        adjacentRoomId = adjacentCell.roomIdValue
        if (adjacentRoomId == room.id) {
            return null
        }
    }

    return DoorSill(
        sillR = sillRow,
        sillC = sillColumn,
        dir = direction,
        doorR = doorRow,
        doorC = doorColumn,
        outId = adjacentRoomId,
    )
}

private fun <T> shuffleList(list: List<T>, dungeon: Dungeon): List<T> {
    val shuffledItems = list.toMutableList()
    for (currentIndex in shuffledItems.indices.reversed()) {
        val swapIndex = dungeon.nextInt(currentIndex + 1)
        val currentValue = shuffledItems[currentIndex]
        shuffledItems[currentIndex] = shuffledItems[swapIndex]
        shuffledItems[swapIndex] = currentValue
    }
    return shuffledItems
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
    for (roomId in 1..dungeon.nRooms) {
        val room = dungeon.rooms.getOrNull(roomId) ?: continue
        val roomLabel = room.id.toString()
        val labelLength = roomLabel.length
        val labelRow = (room.north + room.south) / 2
        val labelColumn = ((room.west + room.east - labelLength) / 2) + 1
        for ((characterOffset, labelCharacter) in roomLabel.withIndex()) {
            dungeon.cell[labelRow][labelColumn + characterOffset] =
                dungeon.cell[labelRow][labelColumn + characterOffset] withFlag glyph(labelCharacter)
        }
    }
    return dungeon
}

private fun corridors(dungeon: Dungeon): Dungeon {
    for (gridRowIndex in 1 until dungeon.nI) {
        val row = (gridRowIndex * 2) + 1
        for (gridColumnIndex in 1 until dungeon.nJ) {
            val column = (gridColumnIndex * 2) + 1
            if (dungeon.cell[row][column] has CORRIDOR) {
                continue
            }
            tunnel(dungeon, gridRowIndex, gridColumnIndex, null)
        }
    }
    return dungeon
}

private fun tunnel(dungeon: Dungeon, gridRowIndex: Int, gridColumnIndex: Int, lastDirection: Direction?): Dungeon {
    for (direction in tunnelDirs(dungeon, lastDirection)) {
        if (openTunnel(dungeon, gridRowIndex, gridColumnIndex, direction)) {
            tunnel(dungeon, gridRowIndex + direction.di, gridColumnIndex + direction.dj, direction)
        }
    }
    return dungeon
}

private fun tunnelDirs(dungeon: Dungeon, lastDirection: Direction?): List<Direction> {
    val probability = corridorLayouts[dungeon.corridorLayout] ?: 0
    val directions = shuffleList(Direction.entries.toList(), dungeon).toMutableList()
    if (lastDirection != null && probability > 0 && dungeon.nextInt(100) < probability) {
        directions.add(0, lastDirection)
    }
    return directions
}

private fun openTunnel(dungeon: Dungeon, gridRowIndex: Int, gridColumnIndex: Int, direction: Direction): Boolean {
    val startRow = (gridRowIndex * 2) + 1
    val startColumn = (gridColumnIndex * 2) + 1
    val destinationRow = ((gridRowIndex + direction.di) * 2) + 1
    val destinationColumn = ((gridColumnIndex + direction.dj) * 2) + 1
    val middleRow = (startRow + destinationRow) / 2
    val middleColumn = (startColumn + destinationColumn) / 2
    return if (soundTunnel(dungeon, middleRow, middleColumn, destinationRow, destinationColumn)) {
        delveTunnel(dungeon, startRow, startColumn, destinationRow, destinationColumn)
    } else {
        false
    }
}

private fun soundTunnel(dungeon: Dungeon, middleRow: Int, middleColumn: Int, destinationRow: Int, destinationColumn: Int): Boolean {
    if (destinationRow < 0 || destinationRow > dungeon.nRows || destinationColumn < 0 || destinationColumn > dungeon.nCols) {
        return false
    }
    val topRow = min(middleRow, destinationRow)
    val bottomRow = max(middleRow, destinationRow)
    val leftColumn = min(middleColumn, destinationColumn)
    val rightColumn = max(middleColumn, destinationColumn)
    for (row in topRow..bottomRow) {
        for (column in leftColumn..rightColumn) {
            if (dungeon.cell[row][column] has BLOCK_CORR) {
                return false
            }
        }
    }
    return true
}

private fun delveTunnel(dungeon: Dungeon, startRow: Int, startColumn: Int, destinationRow: Int, destinationColumn: Int): Boolean {
    val topRow = min(startRow, destinationRow)
    val bottomRow = max(startRow, destinationRow)
    val leftColumn = min(startColumn, destinationColumn)
    val rightColumn = max(startColumn, destinationColumn)
    for (row in topRow..bottomRow) {
        for (column in leftColumn..rightColumn) {
            dungeon.cell[row][column] = dungeon.cell[row][column] withoutFlag ENTRANCE withFlag CORRIDOR
        }
    }
    return true
}

private fun emplaceStairs(dungeon: Dungeon): Dungeon {
    val availableStairStarts = stairEnds(dungeon).toMutableList()
    if (dungeon.addStairs <= 0 || availableStairStarts.isEmpty()) {
        return dungeon
    }
    for (stairIndex in 0 until dungeon.addStairs) {
        if (availableStairStarts.isEmpty()) {
            break
        }
        val selectedStair = availableStairStarts.removeAt(dungeon.nextInt(availableStairStarts.size))
        val stairType = if (stairIndex < 2) stairIndex else dungeon.nextInt(2)
        if (stairType == 0) {
            dungeon.cell[selectedStair.row][selectedStair.col] =
                dungeon.cell[selectedStair.row][selectedStair.col] withFlag STAIR_DN withFlag glyph('d')
            selectedStair.key = "down"
        } else {
            dungeon.cell[selectedStair.row][selectedStair.col] =
                dungeon.cell[selectedStair.row][selectedStair.col] withFlag STAIR_UP withFlag glyph('u')
            selectedStair.key = "up"
        }
        dungeon.stairs.add(selectedStair)
    }
    return dungeon
}

private fun stairEnds(dungeon: Dungeon): List<Stair> =
    buildList {
        for (gridRowIndex in 0 until dungeon.nI) {
            val row = (gridRowIndex * 2) + 1
            for (gridColumnIndex in 0 until dungeon.nJ) {
                val column = (gridColumnIndex * 2) + 1
                if (dungeon.cell[row][column] != CORRIDOR || dungeon.cell[row][column] has STAIRS) {
                    continue
                }

                for (direction in Direction.entries) {
                    val stairPattern = stairEnd[direction] ?: continue
                    if (checkTunnel(dungeon, row, column, stairPattern)) {
                        val nextStep = stairPattern.next!!
                        add(
                            Stair(
                                row = row,
                                col = column,
                                nextRow = row + nextStep.dr,
                                nextCol = column + nextStep.dc,
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
    for (gridRowIndex in 0 until dungeon.nI) {
        val row = (gridRowIndex * 2) + 1
        for (gridColumnIndex in 0 until dungeon.nJ) {
            val column = (gridColumnIndex * 2) + 1
            val cell = dungeon.cell[row][column]
            if (cell hasNot OPENSPACE || cell has STAIRS) {
                continue
            }
            if (!collapseAll && dungeon.nextInt(100) >= percent) {
                continue
            }
            collapse(dungeon, row, column, checks)
        }
    }
    return dungeon
}

private fun collapse(dungeon: Dungeon, row: Int, column: Int, checks: Map<Direction, TunnelCheck>): Dungeon {
    if (dungeon.cell[row][column] hasNot OPENSPACE) {
        return dungeon
    }
    for (direction in Direction.entries) {
        val collapsePattern = checks[direction] ?: continue
        if (!checkTunnel(dungeon, row, column, collapsePattern)) {
            continue
        }
        for (offset in collapsePattern.close) {
            dungeon.cell[row + offset.dr][column + offset.dc] = NOTHING
        }
        collapsePattern.open?.let { offset ->
            dungeon.cell[row + offset.dr][column + offset.dc] =
                dungeon.cell[row + offset.dr][column + offset.dc] withFlag CORRIDOR
        }
        collapsePattern.recurse?.let { offset ->
            collapse(dungeon, row + offset.dr, column + offset.dc, checks)
        }
    }
    return dungeon
}

private fun checkTunnel(dungeon: Dungeon, row: Int, column: Int, check: TunnelCheck): Boolean {
    for (offset in check.corridor) {
        if (dungeon.cellAt(row + offset.dr, column + offset.dc) != CORRIDOR) {
            return false
        }
    }
    for (offset in check.walled) {
        if (dungeon.cellAt(row + offset.dr, column + offset.dc) has OPENSPACE) {
            return false
        }
    }
    return true
}

private fun fixDoors(dungeon: Dungeon): Dungeon {
    val processedDoorPositions = mutableSetOf<Pair<Int, Int>>()
    for (room in dungeon.rooms.filterNotNull()) {
        val directions = room.doors.keys.toList().sortedBy { it.ordinal }
        for (direction in directions) {
            val retainedDoors = mutableListOf<Door>()
            val existingDoors = room.doors[direction].orEmpty()
            for (door in existingDoors) {
                val doorCell = dungeon.cell[door.row][door.col]
                if (doorCell hasNot OPENSPACE) {
                    continue
                }

                val doorPosition = door.row to door.col
                if (!processedDoorPositions.add(doorPosition)) {
                    retainedDoors.add(door)
                } else {
                    door.outId?.let { connectedRoomId ->
                        dungeon.rooms.getOrNull(connectedRoomId)?.let { connectedRoom ->
                            connectedRoom.doors.getOrPut(direction.opposite) { mutableListOf() }.add(door)
                        }
                    }
                    retainedDoors.add(door)
                }
            }
            if (retainedDoors.isNotEmpty()) {
                room.doors[direction] = retainedDoors
                dungeon.allDoors.addAll(retainedDoors)
            } else {
                room.doors.remove(direction)
            }
        }
    }
    return dungeon
}

private fun emptyBlocks(dungeon: Dungeon): Dungeon {
    for (row in 0..dungeon.nRows) {
        for (column in 0..dungeon.nCols) {
            if (dungeon.cell[row][column] has BLOCKED) {
                dungeon.cell[row][column] = NOTHING
            }
        }
    }
    return dungeon
}
