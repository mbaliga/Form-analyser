package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AthleteEntity::class, SessionEntity::class, RigEntity::class, ShotEntity::class,
        // Phase 2 — wellness + life layer
        CheckinEntity::class, SorenessEntity::class, RestDayEntity::class, HiatusEntity::class,
        MoodEntity::class, LifeEventEntity::class, CycleEntity::class, MedicationEntity::class,
        EventEntity::class,
        // Phase 3 — body layer
        PainLogEntity::class, InjuryEntity::class, PhysioPlanEntity::class,
        PhysioExerciseEntity::class, PhysioSessionEntity::class, DocumentEntity::class,
    ],
    version = 4,
    exportSchema = false, // TODO(verified pass): enable + commit schemas/ with ksp room.schemaLocation
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun athleteDao(): AthleteDao
    abstract fun sessionDao(): SessionDao
    abstract fun rigDao(): RigDao
    abstract fun shotDao(): ShotDao
    abstract fun wellnessDao(): WellnessDao
    abstract fun bodyDao(): BodyDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "form-analyser.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
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

        /** V2 → V3 (Phase 2): wellness + life-layer tables and session check-in/duration columns. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checkin` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, " +
                        "`ts` INTEGER NOT NULL, `kind` TEXT NOT NULL, `skipped` INTEGER NOT NULL, " +
                        "`energy` INTEGER, `sleep` INTEGER, `motivation` INTEGER, `rpe` REAL, `feel` INTEGER, " +
                        "`note` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_checkin_athleteId` ON `checkin` (`athleteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_checkin_ts` ON `checkin` (`ts`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `soreness` (`checkinId` TEXT NOT NULL, `regionId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`checkinId`, `regionId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rest_day` (`date` TEXT NOT NULL, `planned` INTEGER NOT NULL, " +
                        "`note` TEXT, PRIMARY KEY(`date`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hiatus` (`id` TEXT NOT NULL, `startDate` TEXT NOT NULL, " +
                        "`endDate` TEXT, `lifeEventId` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mood_entry` (`id` TEXT NOT NULL, `ts` INTEGER NOT NULL, " +
                        "`mood` INTEGER NOT NULL, `tagsJson` TEXT NOT NULL, `note` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mood_entry_ts` ON `mood_entry` (`ts`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `life_event` (`id` TEXT NOT NULL, `startDate` TEXT NOT NULL, " +
                        "`endDate` TEXT, `category` TEXT NOT NULL, `impact` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cycle_entry` (`id` TEXT NOT NULL, `startDate` TEXT NOT NULL, " +
                        "`endDate` TEXT, `flow` INTEGER, `symptomsJson` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medication_entry` (`id` TEXT NOT NULL, `ts` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `dose` TEXT, `schedule` TEXT, `taken` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_entry_ts` ON `medication_entry` (`ts`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `event` (`id` TEXT NOT NULL, `ts` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, `icon` TEXT, `tagsJson` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_ts` ON `event` (`ts`)")

                db.execSQL("ALTER TABLE sessions ADD COLUMN preCheckinId TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN postCheckinId TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN durationAutoS INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN durationS INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN arrowsActual INTEGER")
            }
        }

        /** V3 → V4 (Phase 3): body layer — pain, injuries, physio, encrypted documents. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pain_log` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, " +
                        "`ts` INTEGER NOT NULL, `regionId` TEXT NOT NULL, `intensity` INTEGER NOT NULL, " +
                        "`tagsJson` TEXT NOT NULL, `injuryId` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pain_log_athleteId` ON `pain_log` (`athleteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pain_log_regionId` ON `pain_log` (`regionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pain_log_ts` ON `pain_log` (`ts`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `injury` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, " +
                        "`onset` TEXT NOT NULL, `regionsJson` TEXT NOT NULL, `severity` INTEGER NOT NULL, " +
                        "`mechanism` TEXT NOT NULL, `status` TEXT NOT NULL, `resolvedDate` TEXT, `notes` TEXT, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_injury_athleteId` ON `injury` (`athleteId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `physio_plan` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `targetRegionsJson` TEXT NOT NULL, `scheduleJson` TEXT NOT NULL, " +
                        "`startDate` TEXT NOT NULL, `endDate` TEXT, `source` TEXT NOT NULL, `notes` TEXT, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_physio_plan_athleteId` ON `physio_plan` (`athleteId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `physio_exercise` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `sets` INTEGER NOT NULL, `reps` INTEGER, `holdS` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_physio_exercise_planId` ON `physio_exercise` (`planId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `physio_session` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, " +
                        "`ts` INTEGER NOT NULL, `completedJson` TEXT NOT NULL, `note` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_physio_session_planId` ON `physio_session` (`planId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_physio_session_ts` ON `physio_session` (`ts`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `document` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, " +
                        "`ts` INTEGER NOT NULL, `title` TEXT NOT NULL, `mime` TEXT NOT NULL, `encPath` TEXT NOT NULL, " +
                        "`sha256` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `injuryId` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_athleteId` ON `document` (`athleteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_injuryId` ON `document` (`injuryId`)")
            }
        }
    }
}
