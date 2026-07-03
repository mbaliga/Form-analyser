package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AthleteEntity::class, SessionEntity::class, RigEntity::class, ShotEntity::class],
    version = 2,
    exportSchema = false, // TODO(phase-1 verified pass): enable + commit schemas/ with ksp room.schemaLocation
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun athleteDao(): AthleteDao
    abstract fun sessionDao(): SessionDao
    abstract fun rigDao(): RigDao
    abstract fun shotDao(): ShotDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "form-analyser.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        /**
         * V1 → V2 (Phase 1): athlete profile columns, the rig table, session rig/handedness columns,
         * and a backfill giving every existing athlete a default active rig (tuning seeded from their
         * most recent session's draw weight) with sessions repointed to it. Column names are camelCase
         * to match Room's default derivation for the incumbent entities.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE athletes ADD COLUMN handedness TEXT NOT NULL DEFAULT 'RH'")
                db.execSQL("ALTER TABLE athletes ADD COLUMN drawLengthMm INTEGER")
                db.execSQL("ALTER TABLE athletes ADD COLUMN avatarSeed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE athletes ADD COLUMN club TEXT")
                db.execSQL("ALTER TABLE athletes ADD COLUMN pubkey TEXT")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rig` (" +
                        "`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`bowType` TEXT NOT NULL, `tuningJson` TEXT, `active` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rig_athleteId` ON `rig` (`athleteId`)")

                db.execSQL("ALTER TABLE sessions ADD COLUMN rigId TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN handednessOverride TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_rigId` ON `sessions` (`rigId`)")

                // Backfill non-zero avatar seeds.
                db.execSQL("UPDATE athletes SET avatarSeed = (abs(random()) % 1000000000) + 1 WHERE avatarSeed = 0")

                // Backfill one default active rig per athlete; repoint their sessions to it.
                val athleteIds = mutableListOf<String>()
                db.query("SELECT id FROM athletes").use { c ->
                    while (c.moveToNext()) athleteIds.add(c.getString(0))
                }
                val now = System.currentTimeMillis()
                for (aid in athleteIds) {
                    var marked: Double? = null
                    db.query(
                        "SELECT drawWeightLbs FROM sessions WHERE athleteId = ? ORDER BY startedAtEpochMs DESC LIMIT 1",
                        arrayOf<Any?>(aid),
                    ).use { c -> if (c.moveToFirst() && !c.isNull(0)) marked = c.getDouble(0) }

                    val tuning = marked?.let { "{\"v\":0,\"markedLbs\":$it}" }
                    val rigId = "rig_$aid"
                    db.execSQL(
                        "INSERT INTO rig (id, athleteId, name, bowType, tuningJson, active, createdAt) VALUES (?,?,?,?,?,1,?)",
                        arrayOf<Any?>(rigId, aid, "My bow", "RECURVE", tuning, now),
                    )
                    db.execSQL("UPDATE sessions SET rigId = ? WHERE athleteId = ?", arrayOf<Any?>(rigId, aid))
                }
            }
        }
    }
}
