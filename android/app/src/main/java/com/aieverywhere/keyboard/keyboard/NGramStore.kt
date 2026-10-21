package com.aieverywhere.keyboard.keyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NGramStore(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ngram_db.sqlite"
        private const val DATABASE_VERSION = 1
        private const val TABLE_BIGRAMS = "bigrams"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_BIGRAMS (word1 TEXT, word2 TEXT, count INTEGER, PRIMARY KEY (word1, word2))")
        
        // Let's seed it with some extremely common english bigrams just so it works instantly!
        val seed = listOf(
            "how are", "are you", "you doing", "thank you", "you so", "so much", 
            "what is", "is this", "i am", "am i", "let me", "me know", "on the",
            "in the", "at the", "to the", "for the", "of the", "with the", "and the",
            "I will", "will be", "be there", "there is", "is a", "a good", "good morning",
            "good night", "night and", "see you", "you later", "have a", "a great", "great day",
            "where are", "are we", "we going", "going to", "to do", "do you", "you want",
            "want to", "to go", "please let", "can you", "could you", "would you", "you like"
        )
        for (pair in seed) {
            val parts = pair.split(" ")
            db.execSQL(
                "INSERT INTO $TABLE_BIGRAMS (word1, word2, count) VALUES (?, ?, 10)",
                arrayOf(parts[0].lowercase(), parts[1].lowercase())
            )
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BIGRAMS")
        onCreate(db)
    }

    fun addBigram(w1: String, w2: String) {
        val word1 = w1.lowercase().trim()
        val word2 = w2.lowercase().trim()
        if (word1.isEmpty() || word2.isEmpty()) return

        try {
            val db = writableDatabase
            db.execSQL(
                "INSERT INTO $TABLE_BIGRAMS (word1, word2, count) VALUES (?, ?, 1) " +
                "ON CONFLICT(word1, word2) DO UPDATE SET count = count + 1",
                arrayOf(word1, word2)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getNextWordSuggestions(word1: String, limit: Int = 3): List<String> {
        val w1 = word1.lowercase().trim()
        val results = mutableListOf<String>()
        try {
            val db = readableDatabase
            db.rawQuery("SELECT word2 FROM $TABLE_BIGRAMS WHERE word1 = ? ORDER BY count DESC LIMIT ?", arrayOf(w1, limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(cursor.getString(0))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }
}
