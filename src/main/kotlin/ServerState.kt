data class ServerState(
    val port: Int = 4221,
    val hostName: String = "localhost",
    val allowedPaths: List<String> = listOf("echo/*"),
) {

    fun localServerHostNamePort(): String {
        return "$hostName:$port"
    }

    fun localServerUrl(): String {
        return "$hostName:$port/"
    }


}
