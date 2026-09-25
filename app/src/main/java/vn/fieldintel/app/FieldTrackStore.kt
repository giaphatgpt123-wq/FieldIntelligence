package vn.fieldintel.app

import android.content.Context
import java.io.File
import kotlin.math.*

data class TrackPoint(val latitude:Double,val longitude:Double,val accuracyM:Float,val timeMs:Long)
data class TrackSummary(val points:Int,val distanceM:Double,val startedAt:Long?,val endedAt:Long?)

class FieldTrackStore(context:Context){
 private val file=File(context.filesDir,"field-track.tsv")
 fun clear(){ if(file.exists()) file.delete() }
 fun append(fix:FieldFix):Boolean {\n  if(fix.accuracyM<=0f || fix.accuracyM>50f) return false\n  val previous=load().lastOrNull()\n  if(previous!=null){\n   val dt=fix.timeMs-previous.timeMs\n   if(dt<=0L) return false\n   val d=distance(previous,TrackPoint(fix.latitude,fix.longitude,fix.accuracyM,fix.timeMs))\n   val speed=d/(dt/1000.0)\n   if(speed>15.0) return false\n  }\n  return try{ file.appendText(listOf(fix.latitude,fix.longitude,fix.accuracyM,fix.timeMs).joinToString("\t")+"\n");true }catch(_:Exception){false}\n }
 fun load():List<TrackPoint> = if(!file.exists()) emptyList() else file.readLines().mapNotNull{line->
  val p=line.split("\t"); if(p.size!=4) null else try{TrackPoint(p[0].toDouble(),p[1].toDouble(),p[2].toFloat(),p[3].toLong())}catch(_:Exception){null}
 }
 fun summary():TrackSummary { val p=load(); var d=0.0; for(i in 1 until p.size)d+=distance(p[i-1],p[i]); return TrackSummary(p.size,d,p.firstOrNull()?.timeMs,p.lastOrNull()?.timeMs) }
 private fun distance(a:TrackPoint,b:TrackPoint):Double {
  val r=6371000.0; val p1=Math.toRadians(a.latitude); val p2=Math.toRadians(b.latitude); val dp=Math.toRadians(b.latitude-a.latitude); val dl=Math.toRadians(b.longitude-a.longitude)
  val h=sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2); return 2*r*atan2(sqrt(h),sqrt(1-h))
 }
}