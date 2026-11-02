package com.shiftcalendar

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shiftcalendar.data.AppDb

class ShiftApp : Application() {
    val db: AppDb by lazy {
        Room.databaseBuilder(this, AppDb::class.java, "shift-calendar.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shifts ADD COLUMN endTime TEXT")
                db.execSQL("ALTER TABLE shifts ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v3: перерыв смены и профиль-паттерн, проставивший смену.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shifts ADD COLUMN breakMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shifts ADD COLUMN profileId INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
