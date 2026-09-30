package vn.local.queryrouter.core

/** Pure route policy. Device installation checks are applied by the Android layer. */
object RouteResolver {
    fun ranked(function: FunctionNode): List<RouteSpec> = function.routes
        .asSequence()
        .filter { it.verification == VerificationStatus.VERIFIED }
        .sortedWith(
            compareByDescending<RouteSpec> { it.priority }
                .thenBy { routeTypeRank(it.type) }
        )
        .toList()

    private fun routeTypeRank(type: RouteType): Int = when (type) {
        RouteType.DEEP_LINK, RouteType.APP_LINK -> 0
        RouteType.WEB_DIRECT -> 1
        RouteType.PACKAGE -> 2
        RouteType.WEB_HOME -> 3
        RouteType.STORE -> 4
    }
}
