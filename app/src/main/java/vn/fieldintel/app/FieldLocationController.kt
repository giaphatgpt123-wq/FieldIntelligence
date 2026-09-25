package vn.fieldintel.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

data class FieldFix(val latitude:Double,val longitude:Double,val accuracyM:Float,val timeMs:Long)

class FieldLocationController(context:Context){
 private val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
 @SuppressLint("MissingPermission")
 fun start(onFix:(FieldFix)->Unit):LocationListener {
  val listener=object:LocationListener{
   override fun onLocationChanged(location:Location){ onFix(FieldFix(location.latitude,location.longitude,location.accuracy,location.time)) }
   @Deprecated("Deprecated in Java") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?){}
  }
  manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000L,2f,listener)
  return listener
 }
 fun stop(listener:LocationListener){ manager.removeUpdates(listener) }
}