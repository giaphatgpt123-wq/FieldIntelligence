package vn.fieldintel.feature.emergency

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ScientificLibraryStatusSnapshot(
    val installed: Boolean,
    val recordCount: Long = 0,
    val acceptedRecordCount: Long = 0,
    val sourceVersion: String = "",
    val sourceDoi: String = "",
    val sourceLicense: String = "",
    val scope: String = "taxonomy-only",
    val fishTaxa: Long = 0,
    val fishWithMedia: Long = 0,
    val fishPendingMedia: Long = 0
)

class ScientificLibraryStatus internal constructor() {
    var installed by mutableStateOf(false); internal set
    var recordCount by mutableStateOf(0L); internal set
    var acceptedRecordCount by mutableStateOf(0L); internal set
    var sourceVersion by mutableStateOf(""); internal set
    var sourceDoi by mutableStateOf(""); internal set
    var sourceLicense by mutableStateOf(""); internal set
    var scope by mutableStateOf("taxonomy-only"); internal set
    var fishTaxa by mutableStateOf(0L); internal set
    var fishWithMedia by mutableStateOf(0L); internal set
    var fishPendingMedia by mutableStateOf(0L); internal set

    internal fun publish(value: ScientificLibraryStatusSnapshot) {
        installed=value.installed; recordCount=value.recordCount; acceptedRecordCount=value.acceptedRecordCount
        sourceVersion=value.sourceVersion; sourceDoi=value.sourceDoi; sourceLicense=value.sourceLicense; scope=value.scope
        fishTaxa=value.fishTaxa; fishWithMedia=value.fishWithMedia; fishPendingMedia=value.fishPendingMedia
    }
}

private class ScientificSearchState {
    var loading by mutableStateOf(false)
    var completed by mutableStateOf(false)
}

/** Vietnamese names checked against domestic sources, keyed by exact binomial. */
private data class DomesticName(val label:String,val source:String)
private val domesticNames=mapOf(
    "Curcuma longa" to DomesticName("Nghệ vàng","https://sokhcn.cantho.gov.vn/default.aspx?nid=17522&pid=57"),
    "Curcuma zedoaria" to DomesticName("Nghệ đen (nga truật)","https://tracuuduoclieu.vn/curcuma-zedoaria-berg-roscoe.html"),
    "Ganoderma lucidum" to DomesticName("Nấm linh chi (Ganoderma lucidum)","https://vafs.gov.vn/vn/gia-tri-duoc-lieu-va-cai-thien-chat-luong-trong-nuoi-trong-nhan-tao-nam-linh-chi-viet-nam/")
)

/** Read-only offline scientific taxonomy/media store. Reference media is not identification evidence. */
class ScientificLibraryStore(context: Context) {
    private val appContext=context.applicationContext
    private val databaseFile=File(File(appContext.filesDir,DIRECTORY_NAME),DATABASE_NAME)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val liveStatus=ScientificLibraryStatus()
    private val searchResults=ConcurrentHashMap<SearchKey,SnapshotStateList<SpeciesRecord>>()
    private val searchStates=ConcurrentHashMap<SearchKey,ScientificSearchState>()
    private val recordCache=ConcurrentHashMap<String,SpeciesRecord>()
    private val pendingIdLoads=ConcurrentHashMap.newKeySet<String>()
    private val searchLock=Any()
    private var activeSearchJob:Job?=null
    @Volatile private var observedDatabaseFingerprint=databaseFingerprint()

    init { refreshStatusAsync(); LibraryCollectionRuntime.bind(appContext,this); startRevisionWatcher() }

    fun databasePath():File=databaseFile
    fun status():ScientificLibraryStatus { refreshStatusAsync(); return liveStatus }

    fun refreshAfterImport(){
        observedDatabaseFingerprint=databaseFingerprint()
        invalidateCaches()
        refreshStatusAsync()
        LibraryCollectionRuntime.bind(appContext,this)
    }

    internal fun isInstalledBlocking():Boolean=readStatusBlocking().installed

    /**
     * A blank query is allowed only for a specific group. This lets Library browse all licensed
     * freshwater-fish records without turning an unfiltered global taxonomy table into a huge UI load.
     */
    fun search(query:String,group:String="Tất cả",limit:Int=80):List<SpeciesRecord>{
        val key=searchKey(query,group,limit)?:return emptyList()
        val observable=searchResults.getOrPut(key){mutableStateListOf()}
        val state=searchStates.getOrPut(key){ScientificSearchState()}
        synchronized(searchLock){
            if(!state.loading&&!state.completed){
                activeSearchJob?.cancel();state.loading=true
                activeSearchJob=scope.launch{
                    try{
                        delay(SEARCH_DEBOUNCE_MS)
                        val loaded=searchBlocking(key.query,key.group,key.limit)
                        loaded.forEach{recordCache[it.id]=it}
                        Snapshot.withMutableSnapshot{observable.clear();observable.addAll(loaded);state.completed=true}
                    }finally{Snapshot.withMutableSnapshot{state.loading=false}}
                }
            }
        }
        return observable
    }

    fun isSearching(query:String,group:String="Tất cả",limit:Int=80):Boolean=
        searchKey(query,group,limit)?.let{searchStates[it]?.loading}==true
    fun isSearchCompleted(query:String,group:String="Tất cả",limit:Int=80):Boolean=
        searchKey(query,group,limit)?.let{searchStates[it]?.completed}==true

    fun findById(id:String):SpeciesRecord?{
        recordCache[id]?.let{return it}
        if(!databaseFile.isFile||!id.startsWith(ID_PREFIX))return null
        if(pendingIdLoads.add(id)) scope.launch{
            try{findByIdBlocking(id)?.let{recordCache[id]=it}}finally{pendingIdLoads.remove(id)}
        }
        return null
    }

    fun findByScientificNames(scientificNames:Collection<String>,limit:Int=500):List<SpeciesRecord>{
        if(!databaseFile.isFile||scientificNames.isEmpty())return emptyList()
        val normalized=scientificNames.asSequence().map{it.trim().lowercase()}.filter{it.isNotBlank()}.distinct()
            .take(limit.coerceIn(1,1000)).toList()
        if(normalized.isEmpty())return emptyList()
        val loaded=runCatching{
            openReadOnly().use{db->
                val v2=requireSchema(db)>=2
                val references=v2&&hasSourceReferences(db)
                val placeholders=normalized.joinToString(","){"?"}
                val sql=buildString{
                    append("SELECT ").append(recordProjection(v2,references)).append(" FROM taxon t WHERE t.scientific_name_search IN (")
                    append(placeholders).append(")")
                    if(v2)append(FISH_MEDIA_PUBLISH_SQL)
                    append(" ORDER BY t.scientific_name_search")
                }
                val args=normalized.toMutableList();if(v2)args+=FISH_GROUP
                db.rawQuery(sql,args.toTypedArray()).use{cursor->buildList{while(cursor.moveToNext())add(cursor.toSpeciesRecord(v2,references))}}
            }
        }.getOrElse{emptyList()}
        loaded.forEach{recordCache[it.id]=it};return loaded
    }

    /** Exact, reviewer-linked category rows packaged with the validated taxonomy database. */
    fun reviewedCollectionRecords(collectionId:String):List<SpeciesRecord>{
        if(collectionId !in setOf("flowers","timber-trees","fruit-crops")||!databaseFile.isFile)return emptyList()
        return runCatching { openReadOnly().use { db ->
            val exists=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='reviewed_collection'",null)
                .use { it.moveToFirst() }
            if(!exists)return@use emptyList()
            val v2=requireSchema(db)>=2
            val references=v2&&hasSourceReferences(db)
            val sql="SELECT ${recordProjection(v2,references)}, c.vietnamese_name FROM taxon t " +
                "JOIN reviewed_collection c ON c.source_id=t.source_id AND c.source_record_id=t.source_record_id " +
                "WHERE c.collection_id=? ORDER BY c.vietnamese_name LIMIT 2000"
            db.rawQuery(sql,arrayOf(collectionId)).use { cursor -> buildList {
                while(cursor.moveToNext())add(cursor.toSpeciesRecord(v2,references).copy(
                    vietnameseName=cursor.getString(cursor.columnCount-1)))
            } }
        } }.getOrElse { emptyList() }.also { records -> records.forEach { recordCache[it.id]=it } }
    }

    private fun startRevisionWatcher(){
        scope.launch{
            while(isActive){
                delay(DB_REVISION_POLL_MS)
                val fingerprint=databaseFingerprint()
                if(fingerprint==observedDatabaseFingerprint)continue
                delay(DB_REVISION_SETTLE_MS)
                val settledFingerprint=databaseFingerprint()
                if(settledFingerprint==observedDatabaseFingerprint)continue
                observedDatabaseFingerprint=settledFingerprint
                invalidateCaches()
                val value=readStatusBlocking()
                Snapshot.withMutableSnapshot{liveStatus.publish(value)}
                LibraryCollectionRuntime.bind(appContext,this@ScientificLibraryStore)
            }
        }
    }

    private fun invalidateCaches(){
        synchronized(searchLock){
            activeSearchJob?.cancel();activeSearchJob=null
            Snapshot.withMutableSnapshot{
                searchResults.values.forEach{it.clear()}
                searchStates.values.forEach{state->state.loading=false;state.completed=false}
            }
        }
        recordCache.clear();pendingIdLoads.clear()
    }

    private fun databaseFingerprint():String{
        if(!databaseFile.isFile||databaseFile.length()<=0L)return MISSING_DB_FINGERPRINT
        return "${databaseFile.length()}:${databaseFile.lastModified()}"
    }

    private fun refreshStatusAsync(){scope.launch{val value=readStatusBlocking();Snapshot.withMutableSnapshot{liveStatus.publish(value)}}}

    private fun readStatusBlocking():ScientificLibraryStatusSnapshot{
        if(!databaseFile.isFile||databaseFile.length()<=0L)return ScientificLibraryStatusSnapshot(false)
        return runCatching{
            openReadOnly().use{db->
                requireSchema(db);val meta=readMeta(db)
                ScientificLibraryStatusSnapshot(
                    installed=true,recordCount=meta.longValue("recordCount"),acceptedRecordCount=meta.longValue("acceptedRecordCount"),
                    sourceVersion=meta.stringValue("sourceVersion"),sourceDoi=meta.stringValue("sourceDoi"),
                    sourceLicense=meta.stringValue("sourceLicense"),scope=meta.stringValue("scope").ifBlank{"taxonomy-only"},
                    fishTaxa=meta.longValue("fishTaxa"),fishWithMedia=meta.longValue("fishWithMedia"),fishPendingMedia=meta.longValue("fishPendingMedia")
                )
            }
        }.getOrElse{ScientificLibraryStatusSnapshot(false)}
    }

    private fun searchBlocking(needle:String,group:String,safeLimit:Int):List<SpeciesRecord>{
        if(!databaseFile.isFile)return emptyList()
        return runCatching{
            openReadOnly().use{db->
                val v2=requireSchema(db)>=2
                val references=v2&&hasSourceReferences(db)
                val where=StringBuilder()
                val args=mutableListOf<String>()
                if(needle.isBlank()) {
                    where.append("1=1")
                } else if(v2) {
                    val scientificPrefix="${escapeLike(needle)}%"
                    val vernacularContains="%${escapeLike(needle)}%"
                    where.append("(t.scientific_name_search LIKE ? ESCAPE '\\' OR EXISTS (")
                    where.append("SELECT 1 FROM vernacular_name sv WHERE sv.source_id=t.source_id AND sv.source_record_id=t.source_record_id ")
                    where.append("AND sv.vernacular_name COLLATE NOCASE LIKE ? ESCAPE '\\')")
                    args+=scientificPrefix;args+=vernacularContains
                    val domesticMatches=domesticNames.filter { (binomial, entry) ->
                        SpeciesCatalog.search(needle).any { it.scientificName.startsWith("$binomial ") && it.vietnameseName==entry.label }
                    }.keys
                    domesticMatches.forEach { binomial ->
                        where.append(" OR t.scientific_name_search LIKE ? ESCAPE '\\'")
                        args+="${escapeLike(binomial.lowercase(Locale.ROOT))}%"
                    }
                    where.append(")")
                } else {
                    where.append("t.scientific_name_search LIKE ? ESCAPE '\\'")
                    args+="${escapeLike(needle)}%"
                }
                if(group!="Tất cả"){where.append(" AND t.library_group = ?");args+=group}
                if(v2){where.append(FISH_MEDIA_PUBLISH_SQL);args+=FISH_GROUP}
                val sql="""
                    SELECT ${recordProjection(v2,references)}
                    FROM taxon t
                    WHERE $where
                    ORDER BY t.scientific_name_search
                    LIMIT $safeLimit
                """.trimIndent()
                db.rawQuery(sql,args.toTypedArray()).use{cursor->buildList{while(cursor.moveToNext())add(cursor.toSpeciesRecord(v2,references))}}
            }
        }.getOrElse{emptyList()}
    }

    private fun findByIdBlocking(id:String):SpeciesRecord?{
        if(!databaseFile.isFile||!id.startsWith(ID_PREFIX))return null
        val body=id.removePrefix(ID_PREFIX);val separator=body.indexOf('|')
        if(separator<=0||separator>=body.lastIndex)return null
        val sourceId=body.substring(0,separator);val sourceRecordId=body.substring(separator+1)
        return runCatching{
            openReadOnly().use{db->
                val v2=requireSchema(db)>=2
                val references=v2&&hasSourceReferences(db)
                val sql=buildString{
                    append("SELECT ").append(recordProjection(v2,references)).append(" FROM taxon t WHERE t.source_id=? AND t.source_record_id=?")
                    if(v2)append(FISH_MEDIA_PUBLISH_SQL);append(" LIMIT 1")
                }
                val args=mutableListOf(sourceId,sourceRecordId);if(v2)args+=FISH_GROUP
                db.rawQuery(sql,args.toTypedArray()).use{cursor->if(cursor.moveToFirst())cursor.toSpeciesRecord(v2,references) else null}
            }
        }.getOrNull()
    }

    private fun hasSourceReferences(db:SQLiteDatabase):Boolean=db.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='source_reference' LIMIT 1",null
    ).use{it.moveToFirst()}

    private fun recordProjection(v2:Boolean,references:Boolean):String{
        val base="t.source_id,t.source_record_id,t.scientific_name,t.library_group,t.authority,t.license,t.source_scope,t.source_version,t.source_doi"
        if(!v2)return base
        return base+""",
            COALESCE((SELECT v.vernacular_name FROM vernacular_name v WHERE v.source_id=t.source_id AND v.source_record_id=t.source_record_id ORDER BY v.vernacular_name LIMIT 1),''),
            (SELECT COUNT(*) FROM species_media m WHERE m.source_id=t.source_id AND m.source_record_id=t.source_record_id),
            COALESCE((SELECT m.creator FROM species_media m WHERE m.source_id=t.source_id AND m.source_record_id=t.source_record_id ORDER BY m.media_identifier LIMIT 1),''),
            COALESCE((SELECT m.rights_holder FROM species_media m WHERE m.source_id=t.source_id AND m.source_record_id=t.source_record_id ORDER BY m.media_identifier LIMIT 1),''),
            COALESCE((SELECT m.media_license FROM species_media m WHERE m.source_id=t.source_id AND m.source_record_id=t.source_record_id ORDER BY m.media_identifier LIMIT 1),''),
            COALESCE((SELECT m.references_url FROM species_media m WHERE m.source_id=t.source_id AND m.source_record_id=t.source_record_id ORDER BY m.media_identifier LIMIT 1),''),
            COALESCE((SELECT o.country_code FROM occurrence_summary o WHERE o.source_id=t.source_id AND o.source_record_id=t.source_record_id LIMIT 1),''),
            COALESCE((SELECT o.state_province FROM occurrence_summary o WHERE o.source_id=t.source_id AND o.source_record_id=t.source_record_id LIMIT 1),''),
            COALESCE((SELECT o.locality FROM occurrence_summary o WHERE o.source_id=t.source_id AND o.source_record_id=t.source_record_id LIMIT 1),''),
            COALESCE((SELECT o.event_date FROM occurrence_summary o WHERE o.source_id=t.source_id AND o.source_record_id=t.source_record_id LIMIT 1),'')
        """.trimIndent().replace("\n"," ")+(if(references)""",
            COALESCE((SELECT r.source_url FROM source_reference r WHERE r.source_id=t.source_id AND r.source_record_id=t.source_record_id ORDER BY r.source_url LIMIT 1),'')
        """.trimIndent().replace("\n"," ") else "")
    }

    private fun Cursor.toSpeciesRecord(v2:Boolean,references:Boolean):SpeciesRecord{
        val sourceId=getString(0);val sourceRecordId=getString(1);val scientificName=getString(2)
        val group=getString(3).ifBlank{"Thực vật"};val authority=getString(4).ifBlank{sourceId};val license=getString(5)
        val sourceScope=getString(6).ifBlank{"taxonomy-only"};val version=getString(7);val doi=getString(8)
        val vernacular=if(v2)getString(9).orEmpty() else "";val mediaCount=if(v2)getLong(10) else 0L
        val mediaCreator=if(v2)getString(11).orEmpty() else "";val mediaRights=if(v2)getString(12).orEmpty() else ""
        val mediaLicense=if(v2)getString(13).orEmpty() else "";val mediaReference=if(v2)getString(14).orEmpty() else ""
        val country=if(v2)getString(15).orEmpty() else "";val province=if(v2)getString(16).orEmpty() else ""
        val locality=if(v2)getString(17).orEmpty() else "";val eventDate=if(v2)getString(18).orEmpty() else ""
        val provenance=buildString{
            append("Phạm vi nguồn: ").append(sourceScope)
            if(license.isNotBlank())append(" • license dữ liệu ").append(license)
            if(version.isNotBlank())append(" • phiên bản ").append(version)
            if(v2){
                append(". Ảnh tham chiếu có license: ").append(mediaCount)
                if(mediaCreator.isNotBlank())append(" • tác giả ảnh: ").append(mediaCreator)
                if(mediaRights.isNotBlank())append(" • chủ quyền: ").append(mediaRights)
                if(mediaLicense.isNotBlank())append(" • license ảnh: ").append(mediaLicense)
                val occurrence=listOf(country,province,locality,eventDate).filter{it.isNotBlank()}.joinToString(" • ")
                if(occurrence.isNotBlank())append(". Ghi nhận nguồn: ").append(occurrence)
            }
            append(". Ảnh tham chiếu/taxonomy không xác minh mẫu vật người dùng, tính ăn được, độc tính hoặc hướng dẫn điều trị.")
        }
        val recordReference=if(references)getString(19).orEmpty() else ""
        val sourceUrl=when{recordReference.startsWith("https://")->recordReference;doi.isNotBlank()->"https://doi.org/$doi";mediaReference.startsWith("https://")->mediaReference;else->""}
        val binomial=scientificName.trim().split(Regex("\\s+")).take(2).joinToString(" ")
        val domestic=domesticNames[binomial]
        val starterFishName=if(group=="Cá nước ngọt"&&vernacular.isBlank()){
            FreshwaterFishCatalog.records.firstOrNull{
                it.scientificName.trim().split(Regex("\\s+")).take(2).joinToString(" ").equals(binomial,ignoreCase=true)
            }?.vietnameseName.orEmpty()
        }else ""
        return SpeciesRecord("$ID_PREFIX$sourceId|$sourceRecordId",domestic?.label?:vernacular.ifBlank{starterFishName.ifBlank{scientificName}},scientificName,group,authority,sourceUrl,provenance+(domestic?.let{" • Tên Việt đối chiếu: ${it.source}"}.orEmpty()))
    }

    private fun openReadOnly():SQLiteDatabase=SQLiteDatabase.openDatabase(databaseFile.absolutePath,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)

    private fun requireSchema(db:SQLiteDatabase):Int{
        val core=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('meta','taxon') ORDER BY name",null)
            .use{cursor->buildSet{while(cursor.moveToNext())add(cursor.getString(0))}}
        require(core==setOf("meta","taxon")){"Scientific library schema is incomplete"}
        val meta=readMeta(db);val schemaVersion=meta["schemaVersion"]?.trimJsonString()?.toIntOrNull()
        require(schemaVersion in SUPPORTED_SCHEMA_VERSIONS){"Unsupported scientific library schema: $schemaVersion"}
        val dataScope=meta["scope"]?.trimJsonString();require(dataScope in SUPPORTED_SCOPES){"Unexpected scientific library scope: $dataScope"}
        if(schemaVersion==2){
            val required=setOf("species_media","vernacular_name","occurrence_summary")
            val actual=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('species_media','vernacular_name','occurrence_summary')",null)
                .use{cursor->buildSet{while(cursor.moveToNext())add(cursor.getString(0))}}
            require(actual==required){"Scientific library schema v2 detail tables are incomplete"}
        }
        return schemaVersion?:error("Missing scientific schema version")
    }

    private fun readMeta(db:SQLiteDatabase):Map<String,String> = db.rawQuery("SELECT key,value FROM meta",null)
        .use{cursor->buildMap{while(cursor.moveToNext())put(cursor.getString(0),cursor.getString(1))}}
    private fun Map<String,String>.stringValue(key:String):String=this[key]?.trimJsonString().orEmpty()
    private fun Map<String,String>.longValue(key:String):Long=stringValue(key).toLongOrNull()?:0L
    private fun String.trimJsonString():String{
        val value=trim();if(value.length>=2&&value.first()=='"'&&value.last()=='"')return value.substring(1,value.length-1).replace("\\\"","\"").replace("\\\\","\\")
        return value
    }
    private fun escapeLike(value:String):String=value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")
    private fun searchKey(query:String,group:String,limit:Int):SearchKey?{
        val needle=query.trim().lowercase()
        if(needle.isBlank()&&group=="Tất cả")return null
        return SearchKey(needle,group,limit.coerceIn(1,1000))
    }
    private data class SearchKey(val query:String,val group:String,val limit:Int)

    companion object{
        const val DIRECTORY_NAME="scientific-library";const val DATABASE_NAME="wfo-taxonomy.sqlite"
        private val SUPPORTED_SCHEMA_VERSIONS=setOf(1,2);private val SUPPORTED_SCOPES=setOf("taxonomy-only","taxonomy-media-occurrence")
        private const val ID_PREFIX="scientific-db:";private const val SEARCH_DEBOUNCE_MS=250L;private const val FISH_GROUP="Cá nước ngọt"
        private const val DB_REVISION_POLL_MS=1_500L;private const val DB_REVISION_SETTLE_MS=150L;private const val MISSING_DB_FINGERPRINT="missing"
        private const val FISH_MEDIA_PUBLISH_SQL=" AND (t.library_group != ? OR EXISTS (SELECT 1 FROM species_media pm WHERE pm.source_id=t.source_id AND pm.source_record_id=t.source_record_id))"
    }
}
