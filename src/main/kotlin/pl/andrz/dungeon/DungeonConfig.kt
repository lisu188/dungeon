package pl.andrz.dungeon

internal val dungeonLayouts = mapOf(
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

internal val corridorLayouts = mapOf(
    "Labyrinth" to 0,
    "Bent" to 50,
    "Straight" to 100,
)

internal val stairEnd = mapOf(
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

internal val closeEnd = mapOf(
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

internal val colorChain = mapOf(
    "door" to "fill",
    "label" to "fill",
    "stair" to "wall",
    "wall" to "fill",
    "fill" to "black",
)
