package vn.local.queryrouter.core

data class LibraryHealth(
    val fieldCount: Int,
    val functionCount: Int,
    val sourceCount: Int,
    val knowledgeCount: Int,
    val currentFunctionCount: Int,
    val unknownFunctionCount: Int,
    val verifiedRouteCount: Int,
    val unknownSourceCount: Int
)

object LibraryInspector {
    fun health(extraFunctions: List<FunctionNode> = emptyList()): LibraryHealth {
        val functions = LocalKnowledge.functions + extraFunctions
        return LibraryHealth(
            fieldCount = LocalKnowledge.fields.size,
            functionCount = functions.size,
            sourceCount = LocalKnowledge.sources.size,
            knowledgeCount = LocalKnowledge.records.size,
            currentFunctionCount = functions.count { it.dataStatus == DataStatus.CURRENT },
            unknownFunctionCount = functions.count { it.dataStatus == DataStatus.UNKNOWN },
            verifiedRouteCount = functions.flatMap { it.routes }.count { it.verification == VerificationStatus.VERIFIED },
            unknownSourceCount = LocalKnowledge.sources.count { it.status == DataStatus.UNKNOWN }
        )
    }
}
