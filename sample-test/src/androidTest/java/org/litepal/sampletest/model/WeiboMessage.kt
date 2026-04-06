package org.litepal.sampletest.model

import org.litepal.annotation.Column

class WeiboMessage : Message() {
    var follower: String? = null

    @Column(ignore = true)
    var number: Int = 0

    var cellphone: Cellphone? = null
}




