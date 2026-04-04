package fixture.negative

import org.litepal.annotation.LitePalSchemaAnchor
import org.litepal.crud.LitePalSupport

class BrokenEntity : LitePalSupport() {
    var name: String = ""
}

@LitePalSchemaAnchor(entities = [BrokenEntity::class])
class AnchorA

@LitePalSchemaAnchor(entities = [BrokenEntity::class])
class AnchorB
