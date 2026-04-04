package org.litepal.fixture.model

import java.util.Date
import org.litepal.annotation.LitePalSchemaAnchor
import org.litepal.crud.LitePalSupport

class FixtureAuthor : LitePalSupport() {
    var id: Long = 0L
    var name: String? = null
    var active: Boolean = false
    var rank: Int = 0
    var aliases: MutableList<String> = mutableListOf()
    var books: MutableList<FixtureBook> = mutableListOf()
}

class FixtureBook : LitePalSupport() {
    var id: Long = 0L
    var title: String? = null
    var publishedAt: Date? = null
    var author: FixtureAuthor? = null
    var coAuthors: MutableList<FixtureAuthor> = mutableListOf()
}

@LitePalSchemaAnchor(
    version = 1,
    entities = [
        FixtureAuthor::class,
        FixtureBook::class
    ]
)
class FixtureSchemaAnchor
