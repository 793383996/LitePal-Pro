package org.litepal.sampletest
import org.litepal.sampletest.model.Book
import org.litepal.sampletest.model.Cellphone
import org.litepal.sampletest.model.Classroom
import org.litepal.sampletest.model.Computer
import org.litepal.sampletest.model.IdCard
import org.litepal.sampletest.model.Message
import org.litepal.sampletest.model.Product
import org.litepal.sampletest.model.Student
import org.litepal.sampletest.model.Teacher
import org.litepal.sampletest.model.WeChatMessage
import org.litepal.sampletest.model.WeiboMessage
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




