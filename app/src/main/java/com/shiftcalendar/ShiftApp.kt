package com.shiftcalendar

import android.app.Application
import androidx.room.Room
import com.shiftcalendar.data.AppDb

class ShiftApp : Application() {
    val db: AppDb by lazy {
        Room.databaseBuilder(this, AppDb::class.java, "shift-calendar.db")
            .fallbackToDestructiveMigration()
            .build()
    }
}
