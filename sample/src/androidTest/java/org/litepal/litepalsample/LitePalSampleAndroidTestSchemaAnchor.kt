package org.litepal.litepalsample

import com.litepaltest.model.Book
import com.litepaltest.model.Cellphone
import com.litepaltest.model.Classroom
import com.litepaltest.model.Computer
import com.litepaltest.model.IdCard
import com.litepaltest.model.Message
import com.litepaltest.model.Product
import com.litepaltest.model.Student
import com.litepaltest.model.Teacher
import com.litepaltest.model.WeChatMessage
import com.litepaltest.model.WeiboMessage
import org.litepal.annotation.LitePalSchemaAnchor
import org.litepal.litepalsample.model.Album
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.model.Song

@LitePalSchemaAnchor(
    version = 1,
    entities = [
        Singer::class,
        Album::class,
        Song::class,
        Classroom::class,
        Teacher::class,
        IdCard::class,
        Student::class,
        Cellphone::class,
        Computer::class,
        Book::class,
        Product::class,
        Message::class,
        WeChatMessage::class,
        WeiboMessage::class
    ]
)
class LitePalSampleAndroidTestSchemaAnchor
