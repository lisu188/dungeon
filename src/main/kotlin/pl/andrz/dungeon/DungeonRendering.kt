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

private fun getPalette(image: ImageInfo): Palette =
    when (image.mapStyle) {
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

private fun getColor(palette: Palette, key: String): Color? =
    generateSequence(key) { colorChain[it] }
        .mapNotNull { current ->
            when (current) {
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

private fun withGraphics(image: BufferedImage, block: (Graphics2D) -> Unit) {
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        block(graphics)
    } finally {
        graphics.dispose()
    }
}

private fun baseLayer(dungeon: Dungeon, image: ImageInfo, palette: Palette): BufferedImage {
    val layer = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    withGraphics(layer) { g ->
        g.color = palette.open
        g.fillRect(0, 0, image.width, image.height)
        palette.openGrid?.let { imageGrid(dungeon, image, it, g) }
    }
    return layer
}

private fun fillImage(dungeon: Dungeon, image: ImageInfo, palette: Palette): BufferedImage {
    val canvas = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    withGraphics(canvas) { g ->
        g.color = palette.fill
        g.fillRect(0, 0, image.width, image.height)
    }
    return canvas
}

private fun imageGrid(dungeon: Dungeon, image: ImageInfo, color: Color, graphics: Graphics2D) {
    when (dungeon.grid) {
        "None" -> Unit
        "Hex" -> hexGrid(image, color, graphics)
        else -> squareGrid(image, color, graphics)
    }
}

private fun squareGrid(image: ImageInfo, color: Color, graphics: Graphics2D) {
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

private fun hexGrid(image: ImageInfo, color: Color, graphics: Graphics2D) {
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

private fun openCells(dungeon: Dungeon, image: ImageInfo, baseLayer: BufferedImage, canvas: BufferedImage) {
    withGraphics(canvas) { g ->
        val dim = image.cellSize
        for (r in 0..dungeon.nRows) {
            val y1 = r * dim
            val y2 = y1 + dim + 1
            for (c in 0..dungeon.nCols) {
                if (dungeon.cell[r][c] hasNot OPENSPACE) {
                    continue
                }
                val x1 = c * dim
                val x2 = x1 + dim + 1
                g.drawImage(baseLayer, x1, y1, x2, y2, x1, y1, x2, y2, null)
            }
        }
    }
}

private fun imageWalls(dungeon: Dungeon, image: ImageInfo, palette: Palette, canvas: BufferedImage) {
    val wallColor = palette.wall
    withGraphics(canvas) { g ->
        g.color = wallColor
        val dim = image.cellSize
        for (r in 0..dungeon.nRows) {
            val y1 = r * dim
            val y2 = y1 + dim
            for (c in 0..dungeon.nCols) {
                if (dungeon.cell[r][c] hasNot OPENSPACE) {
                    continue
                }
                val x1 = c * dim
                val x2 = x1 + dim

                if (dungeon.cellAt(r - 1, c - 1) hasNot OPENSPACE) {
                    canvas.setRGB(x1, y1, wallColor.rgb)
                }
                if (dungeon.cellAt(r - 1, c) hasNot OPENSPACE) {
                    g.drawLine(x1, y1, x2, y1)
                }
                if (dungeon.cellAt(r, c - 1) hasNot OPENSPACE) {
                    g.drawLine(x1, y1, x1, y2)
                }
                if (dungeon.cellAt(r, c + 1) hasNot OPENSPACE) {
                    g.drawLine(x2, y1, x2, y2)
                }
                if (dungeon.cellAt(r + 1, c) hasNot OPENSPACE) {
                    g.drawLine(x1, y2, x2, y2)
                }
            }
        }
    }
}

private fun imageDoors(dungeon: Dungeon, image: ImageInfo, palette: Palette, canvas: BufferedImage) {
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

            val vertical = dungeon.cellAt(r, c - 1) has OPENSPACE
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

private fun doorAttr(door: Door): Map<String, Boolean> {
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

private fun imageLabels(dungeon: Dungeon, image: ImageInfo, palette: Palette, canvas: BufferedImage) {
    val color = getColor(palette, "label") ?: palette.label
    withGraphics(canvas) { g ->
        g.color = color
        g.font = image.font
        val dim = image.cellSize
        for (r in 0..dungeon.nRows) {
            for (c in 0..dungeon.nCols) {
                if (dungeon.cell[r][c] hasNot OPENSPACE) {
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

private fun cellLabel(cell: Int): Char? =
    ((cell ushr 24) and 0xFF)
        .takeIf { it != 0 }
        ?.toChar()
        ?.takeIf(Char::isDigit)

private fun imageStairs(dungeon: Dungeon, image: ImageInfo, palette: Palette, canvas: BufferedImage) {
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

private fun imageDungeon(dungeon: Dungeon): String {
    val image = scaleDungeon(dungeon)
    val palette = getPalette(image)
    val baseLayer = baseLayer(dungeon, image, palette)
    val canvas = fillImage(dungeon, image, palette)

    openCells(dungeon, image, baseLayer, canvas)
    imageWalls(dungeon, image, palette, canvas)
    imageDoors(dungeon, image, palette, canvas)
    imageLabels(dungeon, image, palette, canvas)
    imageStairs(dungeon, image, palette, canvas)

    return File("${dungeon.seed}.gif")
        .takeIf { ImageIO.write(canvas, "gif", it) }
        ?.name
        ?: File("${dungeon.seed}.png").also { ImageIO.write(canvas, "png", it) }.name
}
