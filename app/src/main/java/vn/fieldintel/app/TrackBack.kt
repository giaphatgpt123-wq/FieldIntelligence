package vn.fieldintel.app

import kotlin.math.*

data class TrackBackState(val target:TrackPoint?,val remainingM:Double,val bearingDeg:Double?,val offTrackM:Double=0.0,val breadcrumb:List<TrackPoint> = emptyList())

object TrackBack {
 fun state(points:List<TrackPoint>,current:FieldFix?):TrackBackState {
  if(points.isEmpty()||current==null)return TrackBackState(null,0.0,null,0.0,emptyList())
  val here=TrackPoint(current.latitude,current.longitude,current.accuracyM,current.timeMs)
  var nearest=0;var best=Double.MAX_VALUE
  for(i in points.indices){val d=distance(here,points[i]);if(d<best){best=d;nearest=i}}
  val targetIndex=(nearest-1).coerceAtLeast(0)
  val target=points[targetIndex]
  var remaining=distance(here,points[nearest])
  for(i in 1..nearest)remaining+=distance(points[i],points[i-1])
  return TrackBackState(target,remaining,bearing(here,target),best,points.subList(0,nearest+1).asReversed())
 }
 private fun distance(a:TrackPoint,b:TrackPoint):Double{
  val r=6371000.0;val p1=Math.toRadians(a.latitude);val p2=Math.toRadians(b.latitude);val dp=Math.toRadians(b.latitude-a.latitude);val dl=Math.toRadians(b.longitude-a.longitude)
  val h=sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2);return 2*r*atan2(sqrt(h),sqrt(1-h))
 }
 private fun bearing(a:TrackPoint,b:TrackPoint):Double{
  val p1=Math.toRadians(a.latitude);val p2=Math.toRadians(b.latitude);val dl=Math.toRadians(b.longitude-a.longitude)
  val y=sin(dl)*cos(p2);val x=cos(p1)*sin(p2)-sin(p1)*cos(p2)*cos(dl);return (Math.toDegrees(atan2(y,x))+360.0)%360.0
 }
}