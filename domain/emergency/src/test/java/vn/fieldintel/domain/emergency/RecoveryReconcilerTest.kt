package vn.fieldintel.domain.emergency

import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryReconcilerTest {
 private fun e(s:Long,r:Long)=IncidentJournalEntry("i",s,r,"OBS",s.toString(),s,s)
 private val x=RecoveryReconciler()
 @Test fun journalAheadRecoversFromJournal(){ assertTrue(x.reconcile(RecoveryCheckpoint("i",1,1,1),listOf(e(1,1),e(2,2))) is ReconcileResult.RecoverFromJournal) }
 @Test fun checkpointAheadBlocksAsConflict(){ assertTrue(x.reconcile(RecoveryCheckpoint("i",3,3,3),listOf(e(1,1),e(2,2))) is ReconcileResult.Conflict) }
 @Test fun equalCheckpointIsConsistent(){ assertTrue(x.reconcile(RecoveryCheckpoint("i",2,2,2),listOf(e(1,1),e(2,2))) is ReconcileResult.Consistent) }
 @Test fun sequenceGapBlocks(){ assertTrue(x.reconcile(null,listOf(e(1,1),e(3,3))) is ReconcileResult.Conflict) }
}