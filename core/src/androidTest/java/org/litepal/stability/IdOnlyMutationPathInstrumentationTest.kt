package org.litepal.stability

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.litepal.LitePal
import org.litepal.LitePalDB
import org.litepal.LitePalErrorPolicy
import org.litepal.stability.model.RuntimeAlbum
import org.litepal.stability.model.RuntimeArtist
import org.litepal.util.DBUtility

@RunWith(AndroidJUnit4::class)
class IdOnlyMutationPathInstrumentationTest {

    private val createdDbNames = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LitePal.initialize(context)
        LitePal.setErrorPolicy(LitePalErrorPolicy.COMPAT)
    }

    @After
    fun tearDown() {
        LitePal.unregisterDatabaseListener()
        LitePal.useDefault()
        for (dbName in createdDbNames) {
            LitePal.deleteDatabase(dbName)
        }
        createdDbNames.clear()
    }

    @Test
    fun updateAllAndDeleteAll_shouldRemainCorrectForGenericFieldModels() {
        val dbName = "id_only_mutation_${System.currentTimeMillis()}"
        createdDbNames.add(dbName)
        LitePal.use(newDb(dbName))
        LitePal.getDatabase()

        LitePal.deleteAll(RuntimeAlbum::class.java)
        LitePal.deleteAll(RuntimeArtist::class.java)

        val artist = RuntimeArtist().apply { name = "artist_id_only" }
        assertTrue(artist.save())
        repeat(24) { index ->
            val album = RuntimeAlbum().apply {
                name = "album_$index"
                this.artist = artist
                tags = mutableListOf("old_a", "old_b")
            }
            assertTrue(album.save())
        }

        val updater = RuntimeAlbum().apply { name = "album_renamed" }
        val updatedRows = updater.updateAll("name like ?", "album_%")
        assertTrue(updatedRows >= 24)

        val afterUpdate = LitePal.where("name = ?", "album_renamed").find(RuntimeAlbum::class.java)
        assertEquals(24, afterUpdate.size)

        val deletedRows = LitePal.deleteAll(RuntimeAlbum::class.java, "name = ?", "album_renamed")
        assertTrue(deletedRows >= 24)

        val genericTableName = DBUtility.getGenericTableName(RuntimeAlbum::class.java.name, "tags")
        var cursor: android.database.Cursor? = null
        val remainingGenericRows = try {
            cursor = LitePal.getDatabase().rawQuery("select count(1) from $genericTableName", null)
            if (cursor.moveToFirst()) {
                cursor.getInt(0)
            } else {
                0
            }
        } finally {
            cursor?.close()
        }
        assertEquals(0, remainingGenericRows)
    }

    private fun newDb(name: String): LitePalDB {
        val db = LitePalDB(name, 1)
        db.addClassName(RuntimeArtist::class.java.name)
        db.addClassName(RuntimeAlbum::class.java.name)
        return db
    }
}
