package vn.local.queryrouter.core

enum class ConfidenceState { CONFIRMED, NEED_MORE_INFO, AMBIGUOUS, NO_MATCH }
enum class AnswerMode { ANSWER_ONLY, ANSWER_AND_ACTION, ACTION_ONLY, NEED_MORE_INFO }
enum class RouteType { PACKAGE, DEEP_LINK, APP_LINK, WEB_DIRECT, WEB_HOME, STORE }
enum class VerificationStatus { VERIFIED, BROKEN, DEPRECATED, UNKNOWN }
enum class DataStatus { CURRENT, EXPIRED, SUPERSEDED, CONFLICT, UNKNOWN }
enum class SourceLevel { OFFICIAL_ISSUER, OFFICIAL_PORTAL, VERIFIED_REFERENCE, USER_DEFINED }

data class SourceRef(val id:String,val title:String,val issuer:String,val reference:String,val sourceLevel:SourceLevel=SourceLevel.OFFICIAL_ISSUER,val effectiveFrom:String?=null,val effectiveTo:String?=null,val verifiedAt:String?=null,val status:DataStatus=DataStatus.CURRENT,val jurisdiction:String?=null)
data class KnowledgeRecord(val id:String,val topic:String,val canonicalEntity:String,val factKey:String,val factValue:String,val keywords:List<String>,val sourceId:String,val status:DataStatus,val jurisdiction:String?=null,val audience:String?=null,val effectiveFrom:String?=null,val effectiveTo:String?=null)
data class RouteSpec(val id:String,val type:RouteType,val value:String,val label:String,val priority:Int,val verification:VerificationStatus,val requiresInstalledPackage:String?=null,val sourceId:String?=null)
data class FunctionNode(val id:String,val field:String,val service:String,val group:String,val title:String,val description:String,val keywords:List<String>,val answerMode:AnswerMode,val routes:List<RouteSpec> = emptyList(),val sourceIds:List<String> = emptyList(),val requiredEntities:Set<String> = emptySet(),val dataStatus:DataStatus=DataStatus.CURRENT)
data class AppProfile(val id:String,val name:String,val field:String,val description:String,val packageName:String?,val functions:List<String>,val officialSource:String?=null,val userDefined:Boolean=false)
data class QueryEntities(val money:String?=null,val phone:String?=null,val customerCode:String?=null,val taxStatus:String?=null,val location:String?=null,val specialty:String?=null,val organizationType:String?=null)
data class QueryResult(val confidence:ConfidenceState,val function:FunctionNode?=null,val entities:QueryEntities=QueryEntities(),val shortAnswer:String,val clarification:String?=null,val alternativeFunctions:List<FunctionNode> = emptyList(),val sourceRefs:List<SourceRef> = emptyList())
