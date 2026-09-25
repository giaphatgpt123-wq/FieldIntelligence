package vn.fieldintel.app

import android.content.Context
import java.io.File
import java.io.FileOutputStream

data class TrackPoint(val latitude:Double,val longitude:Double,val accuracyM:Float,val timeMs:Long)

class TrackStore(context:Context){
 private val file=File(context.filesDir,"field-track.tsv")
 @Synchronized fun append(fix:FieldFix):Boolean = try {
  FileOutputStream(file,true).use { out ->
   val row="${fix.timeMs}\t${fix.latitude}\t${fix.longitude}\t${fix.accuracyM}\n"
   out.write(row.toByteArray(Charsets.UTF_8)); out.flush(); out.fd.sync()
  }; true
 } catch(_:Exception){ false }
 @Synchronized fun load():List<TrackPoint> = try {
  if(!file.exists()) emptyList() else file.readLines().mapNotNull { line ->
   val p=line.split('\t'); if(p.size!=4) null else try { TrackPoint(p[1].toDouble(),p[2].toDouble(),p[3].toFloat(),p[0].toLong()) } catch(_:Exception){null}
  }
 } catch(_:Exception){ emptyList() }
 @Synchronized fun clear():Boolean = !file.exists() || file.delete()
}