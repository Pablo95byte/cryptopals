package androidx.sqlite.db

interface SupportSQLiteDatabase
interface SupportSQLiteOpenHelper {
    interface Factory
    abstract class Callback(@JvmField val version: Int) {
        abstract fun onCreate(db: SupportSQLiteDatabase)
        abstract fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int)
    }
}
