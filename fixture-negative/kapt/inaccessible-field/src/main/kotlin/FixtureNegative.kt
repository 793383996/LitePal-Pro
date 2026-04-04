package fixture.negative

import org.litepal.annotation.LitePalSchemaAnchor
import org.litepal.crud.LitePalSupport

class BrokenEntity : LitePalSupport() {
    var name: String = ""
        private set
}

@LitePalSchemaAnchor(entities = [BrokenEntity::class])
class Anchor
