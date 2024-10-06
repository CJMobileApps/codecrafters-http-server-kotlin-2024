import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket;

lateinit var serverState: ServerState

fun main() {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")

    serverState = ServerState()
    // Uncomment this block to pass the first stage
    val serverSocket = ServerSocket(serverState.port)

    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true

    // To try this locally on macOS, you could run ./your_server.sh in one terminal session,
    // and nc -vz 127.0.0.1 4221 in another. (-v gives more verbose output, -z just scan for listening daemons, without sending any data to them.)
    // curl -v http://localhost:4221
    // curl -v http://localhost:4221/echo/pineapple

    while (true) {
        val clientSocket = serverSocket.accept() // Wait for connection from client.
        println("accepted new connection")
        val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
        val output = PrintWriter(clientSocket.getOutputStream(), true)

        val serverRequest = buildServerRequest(input = input)

        val httpResponse = buildResponse(
            serverRequest = serverRequest
        )
        println()
        println("httpResponse $httpResponse")

        output.print(httpResponse)
        output.close()
        println("Ready for new connection...")
    }
}

fun buildResponse(serverRequest: ServerRequest): String {

    // Status
    var serverResponse = ServerResponse()
    serverResponse = serverResponse.buildResponseStatusLine(serverRequest = serverRequest)

    return serverResponse.getResponse()
}

fun ServerResponse.buildResponseStatusLine(
    serverRequest: ServerRequest,
): ServerResponse {
    val requestStatusLine = serverRequest.requestStatusLine
    val requestHostPort = serverRequest.requestHostPort

    // GET /index.html HTTP/1.1
    val requestStatusLineArray = requestStatusLine.split(" ")

    // Host: localhost:4221\r\n
    val requestUrlArray = requestHostPort.split(" ")

    // localhost:4221  /index.html
    val requestHostNamePort = if (requestUrlArray.size > 2) requestUrlArray[1] else serverState.localServerHostNamePort()
    val requestUrl = requestHostNamePort + requestStatusLineArray[1]
    val localServerUrl = serverState.localServerUrl()

    return if (requestUrl == localServerUrl) {
        this.setFoundOk()
    } else {
        if(requestHostNamePort != serverState.localServerHostNamePort()) {
            return this.setNotFound()
        }

        if(requestStatusLineArray[1] == "/user-agent") {
            this.content = serverRequest.getUserAgent()
            return this.setFoundOk()
        }

        val requestPaths = requestStatusLineArray[1].split("/")


        var contentFromPath = ""
        serverState.allowedPaths.forEach { entirePath ->
            val paths = "/$entirePath".split("/")

            if(requestPaths.size != paths.size) return@forEach

            paths.forEachIndexed paths@ { i, path ->
                if (path == "*") {
                    contentFromPath = requestPaths[i]
                    return@paths
                }

                if(path != requestPaths[i]) return@forEach
            }

            this.content = contentFromPath
            return this.setFoundOk()
        }

        this.setNotFound()
    }
}

fun ServerResponse.setFoundOk(): ServerResponse {
    this.statusCode = "200"
    this.optionalReasonPhrase = "OK"
    return this
}

fun ServerResponse.setNotFound(): ServerResponse {
    this.statusCode = "404"
    this.optionalReasonPhrase = "Not Found"
    return this
}

data class ServerResponse(
    val httpVersion: String = "HTTP/1.1",
    val crlfStatusLine: String = "\r\n",
    var statusCode: String = "",
    var optionalReasonPhrase: String = "",
    var content: String = "",
) {

    private fun getStatusLine(): String {
        return  "$httpVersion $statusCode $optionalReasonPhrase$crlfStatusLine"
    }

    // Headers (Empty)
    private val crlfHeadersLine = "\r\n"

    private fun getHeader(): String {
        if(content.isEmpty()) {
            return crlfHeadersLine
        }

        val contentType = "Content-Type: text/plain\r\n"
        val contentLength = "Content-Length: ${content.length}\r\n"

        return "$contentType$contentLength$crlfHeadersLine"
    }

    private fun getResponseBody(): String {
        return content
    }

    fun getResponse(): String {
        return "${getStatusLine()}${getHeader()}${getResponseBody()}"
    }
 }

data class ServerRequest(
    var requestStatusLine: String = "",
    var requestHostPort: String = "",
    var requestUserAgent: String = "",
    var requestHeader: String = "",
    var requestBody: String = "",
) {


    fun getUserAgent(): String {
        return requestUserAgent.split(" ").last()
    }
}

fun buildServerRequest(input: BufferedReader): ServerRequest {
    val serverRequest = ServerRequest()

    val lines = mutableListOf<String>()
    var line = input.readLine()
    while (!line.isNullOrEmpty()) {
        lines.add(line)
        line = input.readLine()
    }
    println(lines)

    if (lines.size >= 1) {
        serverRequest.requestStatusLine = lines.first()
    }

    lines
        .filter { it.contains("Host:") }
        .map { serverRequest.requestHostPort = it }

    lines
        .filter { it.contains("User-Agent:") }
        .map { serverRequest.requestUserAgent = it }

//    if (lines.size >= 5) {
//        if (lines[2] != "Accept: */*") {
//            serverRequest.requestHeader = lines[3]
//        } else {
//            serverRequest.requestHeader = lines[4]
//        }
//    }
//
//    // I think this is request body
//    if (lines.size >= 5) {
//        serverRequest.requestBody = lines[4]
//    }

    println(serverRequest)
    return serverRequest
}
