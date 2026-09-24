package vn.fieldintel.app

import android.content.Context
import androidx.room.Room
import vn.fieldintel.data.emergency.*

object EmergencyBootstrap {
 fun database(context:Context):EmergencyDatabase = Room.databaseBuilder(
  context.applicationContext, EmergencyDatabase::class.java, "emergency.db"
 ).build()
 fun recovery(context:Context, db:EmergencyDatabase):CrashRecoveryCoordinator {
  val startup=StartupRecovery(db.emergencyDao())
  val checkpoint=FileRecoveryCheckpointStore(context.filesDir)
  return CrashRecoveryCoordinator(startup,checkpoint)
 }
}