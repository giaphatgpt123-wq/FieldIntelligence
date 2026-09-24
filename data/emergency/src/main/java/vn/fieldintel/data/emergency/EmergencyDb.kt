package vn.fieldintel.data.emergency
import androidx.room.*

@Entity(tableName="incident") data class IncidentEntity(@PrimaryKey val incidentId:String, val state:String, val protocolPackVersion:String, val revision:Long, val startedWallTime:Long, val startedMonotonicTime:Long)
@Entity(tableName="incident_event", indices=[Index(value=["incidentId","sequence"], unique=true)]) data class IncidentEventEntity(@PrimaryKey val eventId:String, val incidentId:String, val sequence:Long, val revision:Long, val eventType:String, val payloadHash:String, val wallTime:Long, val monotonicTime:Long)
@Entity(tableName="location_snapshot") data class LocationSnapshotEntity(@PrimaryKey val id:String, val incidentId:String, val latitude:Double, val longitude:Double, val horizontalAccuracyM:Double?, val fixAgeMs:Long, val positionHealth:String, val wallTime:Long, val monotonicTime:Long)

@Dao interface EmergencyDao {
 @Insert suspend fun insertIncident(v:IncidentEntity)
 @Insert suspend fun insertEvent(v:IncidentEventEntity)
 @Insert suspend fun insertLocation(v:LocationSnapshotEntity)
 @Query("SELECT * FROM incident WHERE incidentId=:id") suspend fun incident(id:String):IncidentEntity?
 @Query("UPDATE incident SET revision=:revision WHERE incidentId=:id AND revision<=:revision") suspend fun advanceRevision(id:String,revision:Long):Int
 @Query("SELECT * FROM incident WHERE state != 'CLOSED' ORDER BY startedMonotonicTime DESC LIMIT 1") suspend fun activeIncident():IncidentEntity?
 @Query("SELECT * FROM incident_event WHERE incidentId=:id ORDER BY sequence ASC") suspend fun events(id:String):List<IncidentEventEntity>
 @Query("SELECT * FROM incident_event WHERE incidentId=:id ORDER BY sequence DESC LIMIT 1") suspend fun lastEvent(id:String):IncidentEventEntity?
 @Query("SELECT * FROM location_snapshot WHERE incidentId=:id ORDER BY monotonicTime DESC LIMIT 1") suspend fun latestLocation(id:String):LocationSnapshotEntity?
}
@Database(entities=[IncidentEntity::class,IncidentEventEntity::class,LocationSnapshotEntity::class], version=1, exportSchema=true) abstract class EmergencyDatabase:RoomDatabase(){ abstract fun emergencyDao():EmergencyDao }