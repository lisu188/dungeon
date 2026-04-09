package pl.andrz.dungeon

fun main() {
    DungeonGenerator()
        .generate()
        .let(DungeonRenderer()::render)
        .also(::println)
}
