package vn.fieldintel.data.emergency

import java.io.File
import java.io.FileOutputStream
import vn.fieldintel.domain.emergency.RecoveryCheckpoint

class FileRecoveryCheckpointStore(private val directory:File){
 private val file get()=File(directory,"emergency.checkpoint")
 fun write(v:RecoveryCheckpoint):Boolean {
  if(!directory.exists() && !directory.mkdirs()) return false
  val tmp=File(directory,"emergency.checkpoint.tmp")
  val payload=listOf("1",v.incidentId,v.lastSequence.toString(),v.lastRevision.toString(),v.monotonicTimeMs.toString()).joinToString("\t")
  return try {
   FileOutputStream(tmp,false).use { out -> out.write(payload.toByteArray(Charsets.UTF_8)); out.flush(); out.fd.sync() }
   if(file.exists() && !file.delete()) return false
   tmp.renameTo(file)
  } catch(_:Exception){ tmp.delete(); false }
 }
 fun read():RecoveryCheckpoint? = try {
  if(!file.exists()) return null
  val p=file.readText(Charsets.UTF_8).split("\t")
  if(p.size!=5 || p[0]!="1") return null
  RecoveryCheckpoint(p[1],p[2].toLong(),p[3].toLong(),p[4].toLong())
 } catch(_:Exception){ null }
}