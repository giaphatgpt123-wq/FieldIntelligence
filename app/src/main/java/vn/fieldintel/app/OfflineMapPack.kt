package vn.fieldintel.app

import android.content.Context
import java.io.File

data class OfflineMapPackState(val available:Boolean,val fileCount:Int,val bytes:Long)

class OfflineMapPack(context:Context){
 private val root=File(context.filesDir,"offline-map").apply{mkdirs()}
 fun state():OfflineMapPackState{
  val files=root.walkTopDown().filter{it.isFile}.toList()
  return OfflineMapPackState(files.isNotEmpty(),files.size,files.sumOf{it.length()})
 }
 fun rootPath():String=root.absolutePath
}