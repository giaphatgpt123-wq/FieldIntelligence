package vn.fieldintel.app

import android.content.Context
import java.io.File
import java.io.FileOutputStream

data class TrackPoint(val latitude:Double,val longitude:Double,val accuracyM:Float,val timeMs:Long)

class TrackRecorder(context:Context){
 private val dir=File(context.filesDir,"tracks").apply{mkdirs()}
 private var file:File?=null
 var recording:Boolean=false; private set
 var pointCount:Int=0; private set
 fun start():Boolean {
  if(recording) return true
  file=File(dir,"track_${System.currentTimeMillis()}.csv")
  return try { file!!.writeText("lat,lon,accuracy,time\n"); recording=true; pointCount=0; true } catch(_:Exception){false}
 }
 fun append(fix:FieldFix):Boolean {
  if(!recording) return false
  val line="${fix.latitude},${fix.longitude},${fix.accuracyM},${fix.timeMs}\n"
  return try { FileOutputStream(file,true).use{out->out.write(line.toByteArray());out.flush();out.fd.sync()};pointCount++;true } catch(_:Exception){false}
 }
 fun stop(){recording=false;file=null}
}