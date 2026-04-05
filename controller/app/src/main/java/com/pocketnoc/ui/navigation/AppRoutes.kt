package com.pocketnoc.ui.navigation

/**
 * Sealed class para definir tipicamente todas as rotas da aplicação
 */
sealed class AppRoute(val route: String) {
    data object Splash : AppRoute("splash")
    data object Login : AppRoute("login")
    data object Dashboard : AppRoute("dashboard")
    data object ServerList : AppRoute("server_list")
    data object ServerDetails : AppRoute("server_details/{serverId}") {
        fun createRoute(serverId: Int) = "server_details/$serverId"
    }
    data object ActionCenter : AppRoute("action_center/{serverId}") {
        fun createRoute(serverId: Int) = "action_center/$serverId"
    }
    data object AlertSettings : AppRoute("alert_settings")
    data object ProcessExplorer : AppRoute("process_explorer/{serverId}") {
        fun createRoute(serverId: Int) = "process_explorer/$serverId"
    }
    data object LogViewer : AppRoute("log_viewer/{serverId}?service={service}") {
        fun createRoute(serverId: Int, service: String = "pocket-noc-agent") = "log_viewer/$serverId?service=$service"
    }
    data object AlertHistory : AppRoute("alert_history")
    data object Watchdog : AppRoute("watchdog/{serverId}") {
        fun createRoute(serverId: Int) = "watchdog/$serverId"
    }
    data object Export : AppRoute("export")
    data object AuditLog : AppRoute("audit_log/{serverId}") {
        fun createRoute(serverId: Int) = "audit_log/$serverId"
    }
    data object AgentConfig : AppRoute("agent_config/{serverId}") {
        fun createRoute(serverId: Int) = "agent_config/$serverId"
    }
}
