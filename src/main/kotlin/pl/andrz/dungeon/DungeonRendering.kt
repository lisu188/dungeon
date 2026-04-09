package pl.andrz.dungeon

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private data class Palette(
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

private data class ImageInfo(
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

class DungeonRenderer {
    fun render(dungeon: Dungeon): String = imageDungeon(dungeon)
}

private fun scaleDungeon(dungeon: Dungeon): ImageInfo {
    val cellSize = dungeon.cellSize
    val imageWidth = ((dungeon.nCols + 1) * cellSize) + 1
    val imageHeight = ((dungeon.nRows + 1) * cellSize) + 1
    val labelFont = when {
        cellSize > 16 -> Font(Font.MONOSPACED, Font.PLAIN, 14)
        cellSize > 12 -> Font(Font.MONOSPACED, Font.PLAIN, 10)
        else -> Font(Font.MONOSPACED, Font.PLAIN, 8)
    }

    val fontMetricsImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
    val fontGraphics = fontMetricsImage.createGraphics()
    fontGraphics.font = labelFont
    val fontMetrics = fontGraphics.fontMetrics
    val characterWidth = fontMetrics.charWidth('0')
    val characterHeight = fontMetrics.height
    val characterOffsetX = ((cellSize - characterWidth) / 2) + 1
    val characterBaselineY = ((cellSize - characterHeight) / 2) + fontMetrics.ascent
    fontGraphics.dispose()

    return ImageInfo(
        cellSize = cellSize,
        mapStyle = dungeon.mapStyle,
        width = imageWidth,
        height = imageHeight,
        maxX = imageWidth - 1,
        maxY = imageHeight - 1,
        font = labelFont,
        charW = characterWidth,
        charH = characterHeight,
        charX = characterOffsetX,
        charY = characterBaselineY,
    )
}

private fun getPalette(imageInfo: ImageInfo): Palette =
    when (imageInfo.mapStyle) {
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

private fun getColor(palette: Palette, colorKey: String): Color? =
    generateSequence(colorKey) { colorChain[it] }
        .mapNotNull { chainKey ->
            when (chainKey) {
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
        }
        .firstOrNull()

private fun withGraphics(bufferedImage: BufferedImage, block: (Graphics2D) -> Unit) {
    val graphics = bufferedImage.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        block(graphics)
    } finally {
        graphics.dispose()
    }
}

private fun baseLayer(dungeon: Dungeon, imageInfo: ImageInfo, palette: Palette): BufferedImage {
    val baseImage = BufferedImage(imageInfo.width, imageInfo.height, BufferedImage.TYPE_INT_RGB)
    withGraphics(baseImage) { graphics ->
        graphics.color = palette.open
        graphics.fillRect(0, 0, imageInfo.width, imageInfo.height)
        palette.openGrid?.let { imageGrid(dungeon, imageInfo, it, graphics) }
    }
    return baseImage
}

private fun fillImage(dungeon: Dungeon, imageInfo: ImageInfo, palette: Palette): BufferedImage {
    val filledImage = BufferedImage(imageInfo.width, imageInfo.height, BufferedImage.TYPE_INT_RGB)
    withGraphics(filledImage) { graphics ->
        graphics.color = palette.fill
        graphics.fillRect(0, 0, imageInfo.width, imageInfo.height)
    }
    return filledImage
}

private fun imageGrid(dungeon: Dungeon, imageInfo: ImageInfo, color: Color, graphics: Graphics2D) {
    when (dungeon.grid) {
        "None" -> Unit
        "Hex" -> hexGrid(imageInfo, color, graphics)
        else -> squareGrid(imageInfo, color, graphics)
    }
}

private fun squareGrid(imageInfo: ImageInfo, color: Color, graphics: Graphics2D) {
    graphics.color = color
    val cellSize = imageInfo.cellSize
    var currentX = 0
    while (currentX <= imageInfo.maxX) {
        graphics.drawLine(currentX, 0, currentX, imageInfo.maxY)
        currentX += cellSize
    }
    var currentY = 0
    while (currentY <= imageInfo.maxY) {
        graphics.drawLine(0, currentY, imageInfo.maxX, currentY)
        currentY += cellSize
    }
}

private fun hexGrid(imageInfo: ImageInfo, color: Color, graphics: Graphics2D) {
    val cellSize = imageInfo.cellSize.toDouble()
    val rowStep = cellSize / 2.0
    val columnOffset = cellSize / 3.4641016151
    val columnCount = (imageInfo.width / (3 * columnOffset)).toInt()
    val rowCount = (imageInfo.height / rowStep).toInt()
    val previousHint = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.color = color

    for (columnIndex in 0 until columnCount) {
        val leftX = (columnIndex * (3 * columnOffset)).toInt()
        val centerX = (leftX + columnOffset).toInt()
        val rightX = (leftX + (3 * columnOffset)).toInt()
        for (rowIndex in 0 until rowCount) {
            val topY = (rowIndex * rowStep).toInt()
            val bottomY = (topY + rowStep).toInt()
            if ((columnIndex + rowIndex) % 2 == 1) {
                graphics.drawLine(leftX, topY, centerX, bottomY)
                graphics.drawLine(centerX, bottomY, rightX, bottomY)
            } else {
                graphics.drawLine(centerX, topY, leftX, bottomY)
            }
        }
    }

    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousHint)
}

private fun openCells(dungeon: Dungeon, imageInfo: ImageInfo, openAreaBaseImage: BufferedImage, canvas: BufferedImage) {
    withGraphics(canvas) { graphics ->
        val cellSize = imageInfo.cellSize
        for (row in 0..dungeon.nRows) {
            val topY = row * cellSize
            val bottomY = topY + cellSize + 1
            for (column in 0..dungeon.nCols) {
                if (dungeon.cell[row][column] hasNot OPENSPACE) {
                    continue
                }
                val leftX = column * cellSize
                val rightX = leftX + cellSize + 1
                graphics.drawImage(openAreaBaseImage, leftX, topY, rightX, bottomY, leftX, topY, rightX, bottomY, null)
            }
        }
    }
}

private fun imageWalls(dungeon: Dungeon, imageInfo: ImageInfo, palette: Palette, canvas: BufferedImage) {
    val wallColor = palette.wall
    withGraphics(canvas) { graphics ->
        graphics.color = wallColor
        val cellSize = imageInfo.cellSize
        for (row in 0..dungeon.nRows) {
            val topY = row * cellSize
            val bottomY = topY + cellSize
            for (column in 0..dungeon.nCols) {
                if (dungeon.cell[row][column] hasNot OPENSPACE) {
                    continue
                }
                val leftX = column * cellSize
                val rightX = leftX + cellSize

                if (dungeon.cellAt(row - 1, column - 1) hasNot OPENSPACE) {
                    canvas.setRGB(leftX, topY, wallColor.rgb)
                }
                if (dungeon.cellAt(row - 1, column) hasNot OPENSPACE) {
                    graphics.drawLine(leftX, topY, rightX, topY)
                }
                if (dungeon.cellAt(row, column - 1) hasNot OPENSPACE) {
                    graphics.drawLine(leftX, topY, leftX, bottomY)
                }
                if (dungeon.cellAt(row, column + 1) hasNot OPENSPACE) {
                    graphics.drawLine(rightX, topY, rightX, bottomY)
                }
                if (dungeon.cellAt(row + 1, column) hasNot OPENSPACE) {
                    graphics.drawLine(leftX, bottomY, rightX, bottomY)
                }
            }
        }
    }
}

private fun imageDoors(dungeon: Dungeon, imageInfo: ImageInfo, palette: Palette, canvas: BufferedImage) {
    if (dungeon.allDoors.isEmpty()) {
        return
    }

    val archColor = getColor(palette, "wall") ?: palette.wall
    val doorColor = getColor(palette, "door") ?: palette.door

    withGraphics(canvas) { graphics ->
        val cellSize = imageInfo.cellSize
        val archInset = cellSize / 6
        val doorHalfSpan = cellSize / 4
        val trapHalfSpan = cellSize / 3

        for (door in dungeon.allDoors) {
            val row = door.row
            val column = door.col
            val topY = row * cellSize
            val bottomY = topY + cellSize
            val leftX = column * cellSize
            val rightX = leftX + cellSize

            val isVerticalDoor = dungeon.cellAt(row, column - 1) has OPENSPACE
            val centerX = if (isVerticalDoor) (leftX + rightX) / 2 else null
            val centerY = if (!isVerticalDoor) (topY + bottomY) / 2 else null
            val doorAttributes = doorAttr(door)

            if (doorAttributes["wall"] == true) {
                graphics.color = archColor
                if (centerX != null) {
                    graphics.drawLine(centerX, topY, centerX, bottomY)
                } else if (centerY != null) {
                    graphics.drawLine(leftX, centerY, rightX, centerY)
                }
            }

            if (doorAttributes["secret"] == true) {
                graphics.color = doorColor
                if (centerX != null) {
                    val middleY = (topY + bottomY) / 2
                    graphics.drawLine(centerX - 1, middleY - doorHalfSpan, centerX + 2, middleY - doorHalfSpan)
                    graphics.drawLine(centerX - 2, middleY - doorHalfSpan + 1, centerX - 2, middleY - 1)
                    graphics.drawLine(centerX - 1, middleY, centerX + 1, middleY)
                    graphics.drawLine(centerX + 2, middleY + 1, centerX + 2, middleY + doorHalfSpan - 1)
                    graphics.drawLine(centerX - 2, middleY + doorHalfSpan, centerX + 1, middleY + doorHalfSpan)
                } else if (centerY != null) {
                    val middleX = (leftX + rightX) / 2
                    graphics.drawLine(middleX - doorHalfSpan, centerY - 2, middleX - doorHalfSpan, centerY + 1)
                    graphics.drawLine(middleX - doorHalfSpan + 1, centerY + 2, middleX - 1, centerY + 2)
                    graphics.drawLine(middleX, centerY - 1, middleX, centerY + 1)
                    graphics.drawLine(middleX + 1, centerY - 2, middleX + doorHalfSpan - 1, centerY - 2)
                    graphics.drawLine(middleX + doorHalfSpan, centerY - 1, middleX + doorHalfSpan, centerY + 2)
                }
            }

            if (doorAttributes["arch"] == true) {
                graphics.color = archColor
                if (centerX != null) {
                    graphics.fillRect(centerX - 1, topY, 3, archInset + 1)
                    graphics.fillRect(centerX - 1, bottomY - archInset, 3, archInset + 1)
                } else if (centerY != null) {
                    graphics.fillRect(leftX, centerY - 1, archInset + 1, 3)
                    graphics.fillRect(rightX - archInset, centerY - 1, archInset + 1, 3)
                }
            }

            if (doorAttributes["door"] == true) {
                graphics.color = doorColor
                if (centerX != null) {
                    graphics.drawRect(
                        centerX - doorHalfSpan,
                        topY + archInset + 1,
                        doorHalfSpan * 2,
                        (bottomY - archInset - 1) - (topY + archInset + 1),
                    )
                } else if (centerY != null) {
                    graphics.drawRect(
                        leftX + archInset + 1,
                        centerY - doorHalfSpan,
                        (rightX - archInset - 1) - (leftX + archInset + 1),
                        doorHalfSpan * 2,
                    )
                }
            }

            if (doorAttributes["lock"] == true) {
                graphics.color = doorColor
                if (centerX != null) {
                    graphics.drawLine(centerX, topY + archInset + 1, centerX, bottomY - archInset - 1)
                } else if (centerY != null) {
                    graphics.drawLine(leftX + archInset + 1, centerY, rightX - archInset - 1, centerY)
                }
            }

            if (doorAttributes["trap"] == true) {
                graphics.color = doorColor
                if (centerX != null) {
                    val middleY = (topY + bottomY) / 2
                    graphics.drawLine(centerX - trapHalfSpan, middleY, centerX + trapHalfSpan, middleY)
                } else if (centerY != null) {
                    val middleX = (leftX + rightX) / 2
                    graphics.drawLine(middleX, centerY - trapHalfSpan, middleX, centerY + trapHalfSpan)
                }
            }

            if (doorAttributes["portc"] == true) {
                graphics.color = doorColor
                if (centerX != null) {
                    var currentY = topY + archInset + 2
                    while (currentY < bottomY - archInset) {
                        canvas.setRGB(centerX, currentY, doorColor.rgb)
                        currentY += 2
                    }
                } else if (centerY != null) {
                    var currentX = leftX + archInset + 2
                    while (currentX < rightX - archInset) {
                        canvas.setRGB(currentX, centerY, doorColor.rgb)
                        currentX += 2
                    }
                }
            }
        }
    }
}

private fun doorAttr(door: Door): Map<String, Boolean> {
    val doorAttributes = mutableMapOf<String, Boolean>()
    when (door.key) {
        "arch" -> doorAttributes["arch"] = true
        "open" -> {
            doorAttributes["arch"] = true
            doorAttributes["door"] = true
        }
        "lock" -> {
            doorAttributes["arch"] = true
            doorAttributes["door"] = true
            doorAttributes["lock"] = true
        }
        "trap" -> {
            doorAttributes["arch"] = true
            doorAttributes["door"] = true
            doorAttributes["trap"] = true
            if (door.desc?.contains("Lock") == true) {
                doorAttributes["lock"] = true
            }
        }
        "secret" -> {
            doorAttributes["wall"] = true
            doorAttributes["arch"] = true
            doorAttributes["secret"] = true
        }
        "portc" -> {
            doorAttributes["arch"] = true
            doorAttributes["portc"] = true
        }
    }
    return doorAttributes
}

private fun imageLabels(dungeon: Dungeon, imageInfo: ImageInfo, palette: Palette, canvas: BufferedImage) {
    val labelColor = getColor(palette, "label") ?: palette.label
    withGraphics(canvas) { graphics ->
        graphics.color = labelColor
        graphics.font = imageInfo.font
        val cellSize = imageInfo.cellSize
        for (row in 0..dungeon.nRows) {
            for (column in 0..dungeon.nCols) {
                if (dungeon.cell[row][column] hasNot OPENSPACE) {
                    continue
                }
                val labelCharacter = cellLabel(dungeon.cell[row][column]) ?: continue
                val drawX = (column * cellSize) + imageInfo.charX
                val drawY = (row * cellSize) + imageInfo.charY
                graphics.drawString(labelCharacter.toString(), drawX, drawY)
            }
        }
    }
}

private fun cellLabel(cell: Int): Char? =
    ((cell ushr 24) and 0xFF)
        .takeIf { it != 0 }
        ?.toChar()
        ?.takeIf(Char::isDigit)

private fun imageStairs(dungeon: Dungeon, imageInfo: ImageInfo, palette: Palette, canvas: BufferedImage) {
    if (dungeon.stairs.isEmpty()) {
        return
    }

    val stairColor = getColor(palette, "stair") ?: palette.stair
    withGraphics(canvas) { graphics ->
        graphics.color = stairColor
        val cellSize = imageInfo.cellSize
        val stairHalfWidth = cellSize / 2
        val stairLineStep = (cellSize / 20) + 2

        for (stair in dungeon.stairs) {
            when {
                stair.nextRow > stair.row -> {
                    val centerX = ((stair.col + 0.5) * cellSize).toInt()
                    val startY = stair.row * cellSize
                    val endY = (stair.nextRow + 1) * cellSize
                    var currentY = startY
                    while (currentY < endY) {
                        val stairWidth = if (stair.key == "down") {
                            (((currentY - startY).toDouble() / (endY - startY)) * stairHalfWidth).toInt()
                        } else {
                            stairHalfWidth
                        }
                        graphics.drawLine(centerX - stairWidth, currentY, centerX + stairWidth, currentY)
                        currentY += stairLineStep
                    }
                }
                stair.nextRow < stair.row -> {
                    val centerX = ((stair.col + 0.5) * cellSize).toInt()
                    val startY = (stair.row + 1) * cellSize
                    val endY = stair.nextRow * cellSize
                    var currentY = startY
                    while (currentY > endY) {
                        val stairWidth = if (stair.key == "down") {
                            (((currentY - startY).toDouble() / (endY - startY)) * stairHalfWidth).toInt()
                        } else {
                            stairHalfWidth
                        }
                        graphics.drawLine(centerX - stairWidth, currentY, centerX + stairWidth, currentY)
                        currentY -= stairLineStep
                    }
                }
                stair.nextCol > stair.col -> {
                    val startX = stair.col * cellSize
                    val endX = (stair.nextCol + 1) * cellSize
                    val centerY = ((stair.row + 0.5) * cellSize).toInt()
                    var currentX = startX
                    while (currentX < endX) {
                        val stairHeight = if (stair.key == "down") {
                            (((currentX - startX).toDouble() / (endX - startX)) * stairHalfWidth).toInt()
                        } else {
                            stairHalfWidth
                        }
                        graphics.drawLine(currentX, centerY - stairHeight, currentX, centerY + stairHeight)
                        currentX += stairLineStep
                    }
                }
                stair.nextCol < stair.col -> {
                    val startX = (stair.col + 1) * cellSize
                    val endX = stair.nextCol * cellSize
                    val centerY = ((stair.row + 0.5) * cellSize).toInt()
                    var currentX = startX
                    while (currentX > endX) {
                        val stairHeight = if (stair.key == "down") {
                            (((currentX - startX).toDouble() / (endX - startX)) * stairHalfWidth).toInt()
                        } else {
                            stairHalfWidth
                        }
                        graphics.drawLine(currentX, centerY - stairHeight, currentX, centerY + stairHeight)
                        currentX -= stairLineStep
                    }
                }
            }
        }
    }
}

private fun imageDungeon(dungeon: Dungeon): String {
    val imageInfo = scaleDungeon(dungeon)
    val palette = getPalette(imageInfo)
    val openAreaBaseImage = baseLayer(dungeon, imageInfo, palette)
    val canvas = fillImage(dungeon, imageInfo, palette)

    openCells(dungeon, imageInfo, openAreaBaseImage, canvas)
    imageWalls(dungeon, imageInfo, palette, canvas)
    imageDoors(dungeon, imageInfo, palette, canvas)
    imageLabels(dungeon, imageInfo, palette, canvas)
    imageStairs(dungeon, imageInfo, palette, canvas)

    return File("${dungeon.seed}.gif")
        .takeIf { ImageIO.write(canvas, "gif", it) }
        ?.name
        ?: File("${dungeon.seed}.png").also { ImageIO.write(canvas, "png", it) }.name
}
