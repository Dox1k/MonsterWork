package com.shiftcalendar

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shiftcalendar.data.AppDb

class ShiftApp : Application() {
    val db: AppDb by lazy {
        Room.databaseBuilder(this, AppDb::class.java, "shift-calendar.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

        // v4: второй профиль дня (решение 12-Б). Поле появилось в 1.6 без подъёма
        // версии БД — из-за этого Room падал на проверке identity hash, поэтому
        // здесь же лечим уже затронутые установки: колонки может не быть, тогда
        // добавляем её; если она уже есть (БД, созданная сборкой 1.6 с version=3),
        // ничего не делаем — Room перезапишет identity hash после открытия.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("shifts", "profileId2")) {
                    db.execSQL("ALTER TABLE shifts ADD COLUMN profileId2 INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
            query("PRAGMA table_info($table)").use { c ->
                val nameIdx = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    if (nameIdx >= 0 && c.getString(nameIdx) == column) return true
                }
            }
            return false
        }
    }
}
