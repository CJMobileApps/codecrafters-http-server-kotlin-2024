import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.math.BigInteger
import java.net.ServerSocket
import java.util.HexFormat
import java.util.zip.GZIPOutputStream


lateinit var serverState: ServerState

var dirPath: String? = null

suspend fun main(arguments: Array<String>) = coroutineScope {

    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")

    // todo check if first arguemtn is "--directory"
//    var index = 0
//    while (index < arguments.size) {
//        if()
//
//    }
    if (arguments.isNotEmpty()) dirPath = arguments[1]

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

        launch {
            withContext(Dispatchers.IO) {

                val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val output = PrintWriter(clientSocket.getOutputStream(), true)

                val serverRequest = buildServerRequest(input = input)

                val serverResponse =  buildServerResponse(
                    serverRequest = serverRequest,
                    input = input,
                )

                val httpResponse = serverResponse.buildResponse()
                println()
                println("httpResponse $httpResponse")


                //output.print(httpResponse)


                val buffer = ByteArrayOutputStream()
                buffer.write(httpResponse.toByteArray())
                serverResponse.contentBytes?.let {
                    buffer.write(it)
                }

                println("HERE_ buffer " + buffer)
                clientSocket.getOutputStream().write(buffer.toByteArray())
                clientSocket.getOutputStream().close()
                //output.print(it.toString())
                //output.close()
                println("Ready for new connection...")
            }
        }
    }
}

fun buildServerResponse(serverRequest: ServerRequest, input: BufferedReader): ServerResponse {

    // Status
    var serverResponse = ServerResponse()
    serverResponse = serverResponse.buildResponseStatusLine(serverRequest = serverRequest, input = input)

    return serverResponse
}

fun ServerResponse.buildResponse(): String {
    return this.getResponse()
}

@OptIn(ExperimentalStdlibApi::class)
fun ServerResponse.buildResponseStatusLine(
    serverRequest: ServerRequest,
    input: BufferedReader,
): ServerResponse {
    val requestStatusLine = serverRequest.requestStatusLine
    val requestHostPort = serverRequest.requestHostPort

    // GET /index.html HTTP/1.1
    val requestStatusLineArray = requestStatusLine.split(" ")

    // Host: localhost:4221\r\n
    val requestUrlArray = requestHostPort.split(" ")

    // localhost:4221  /index.html
    val requestHostNamePort =
        if (requestUrlArray.size > 2) requestUrlArray[1] else serverState.localServerHostNamePort()
    val requestUrl = requestHostNamePort + requestStatusLineArray[1]
    val localServerUrl = serverState.localServerUrl()

    return if (requestUrl == localServerUrl) {
        this.setFoundOk()
    } else {
        if (requestHostNamePort != serverState.localServerHostNamePort()) {
            return this.setNotFound()
        }

        if (requestStatusLineArray[1] == "/user-agent") {
            this.contentType = "Content-Type: text/plain\r\n"
            this.content = serverRequest.getUserAgent()
            this.contentLength = content.length.toString()
            return this.setFoundOk()
        }

        val requestPaths = requestStatusLineArray[1].split("/")
        var contentFromPath = ""

        if (requestPaths[1] == "files") {
            when (requestStatusLineArray[0]) {
                "GET" -> {
                    val file = File("$dirPath${requestPaths[2]}")

                    return if (file.exists()) {

                        val text = file.readText()
                        this.contentType = "Content-Type: application/octet-stream\r\n"
                        this.content = text

                        this.contentLength = content.length.toString()
                        println("contentLength " + this.contentLength)
                        this.setFoundOk()
                    } else {
                        this.setNotFound()
                    }
                }

                "POST" -> {
                    // Content-Length: 6
                    val requestContentLengthArray = serverRequest.requestContentLength.split(" ")
                    val contentLength = requestContentLengthArray[1].toInt()
                    val body = CharArray(contentLength)
                    input.read(body)
                    val requestBody = String(body)
                    println("request body: " + String(body))

                    val writer = PrintWriter("$dirPath${requestPaths[2]}")
                    writer.print(requestBody)
                    writer.close()

                    return this.setCreated()
                }
            }
        }

        serverState.allowedPaths.forEach { entirePath ->
            val paths = "/$entirePath".split("/")

            if (requestPaths.size != paths.size) return@forEach

            paths.forEachIndexed paths@{ i, path ->
                if (path == "*") {
                    contentFromPath = requestPaths[i]
                    this.content = contentFromPath
                    this.contentLength = content.length.toString()
                    println("contentLength " + this.contentLength)
                    return@paths
                }

                if (path != requestPaths[i]) return@forEach
            }

            if(serverRequest.requestContentEncoding.isNotEmpty()) {
                val contentEncoding = serverRequest.requestContentEncoding.split(": ")

                val encodingsList = contentEncoding[1].split(", ")
                encodingsList.forEach { encoding ->
                    when (encoding) {
                        ServerState.AllowedEncoding.GZIP.name.lowercase() -> {
                            this.encoding = ServerState.AllowedEncoding.GZIP.name.lowercase()

                            println("conetnet " + contentFromPath)
                            val compressedContent = compress(contentFromPath)
                            println("CompressedContent $compressedContent")
                            println("CompressedContent ${compressedContent.toString()}")
                            println("CompressedContent ${compressedContent.size}")
                            this.contentLength = compressedContent.size.toString()
                            this.contentBytes = compressedContent


                            this.content = compressedContent.toString()
                        }
                    }
                }
            }

            this.contentType = "Content-Type: text/plain\r\n"

            return this.setFoundOk()
        }

        this.setNotFound()
    }
}

fun compress( s: String): ByteArray {
    val byteStream = ByteArrayOutputStream()
    GZIPOutputStream(byteStream)
        .bufferedWriter()
        .use { it.write(s, 0, s.length) }
    return byteStream.toByteArray()
}

fun String.toHex2(): String {
    return String.format("%040x", BigInteger(1, this.toByteArray()))
}

fun String.toHex(): String {
   return  HexFormat.of().formatHex(this.toByteArray())
}

fun ServerResponse.setCreated(): ServerResponse {
    this.statusCode = "201"
    this.optionalReasonPhrase = "Created"
    return this
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
    var contentBytes: ByteArray? = null,
    var contentLength: String = "",
    var contentType: String = "",
    var encoding: String = "",
) {

    private fun getStatusLine(): String {
        return "$httpVersion $statusCode $optionalReasonPhrase$crlfStatusLine"
    }

    // Headers (Empty)
    private val crlfHeadersLine = "\r\n"

    private fun getHeader(): String {
        if (content.isEmpty()) {
            return crlfHeadersLine
        }

        val contentLength = "Content-Length: ${this.contentLength}\r\n"

        val contentEncoding = if(encoding.isNotEmpty()) "Content-Encoding: $encoding\r\n" else ""

        return "$contentType$contentEncoding$contentLength$crlfHeadersLine"
    }

    private fun getResponseBody(): String {
        return content
    }

    fun getResponse(): String {
//        return "${getStatusLine()}${getHeader()}${getResponseBody()}"

        // if not gzip compression set responseBody
        val responseBody = if(!encoding.contains(ServerState.AllowedEncoding.GZIP.name.lowercase())) {
            getResponseBody()
        } else ""

        return "${getStatusLine()}${getHeader()}${responseBody}"

    }
}

data class ServerRequest(
    var requestStatusLine: String = "",
    var requestHostPort: String = "",
    var requestUserAgent: String = "",
    var requestHeader: String = "",
    var requestContentLength: String = "",
    var requestBody: String = "",
    var requestContentEncoding: String = "",
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

    println("lines $lines")
    if (lines.size >= 1) {
        serverRequest.requestStatusLine = lines.first()
    }

    lines
        .filter { it.contains("Host:") }
        .map { serverRequest.requestHostPort = it }

    lines
        .filter { it.contains("User-Agent:") }
        .map { serverRequest.requestUserAgent = it }

    lines
        .filter { it.contains("Content-Length: ") }
        .map { serverRequest.requestContentLength = it }

    lines
        .filter { it.contains("Accept-Encoding: ") }
        .map { serverRequest.requestContentEncoding = it }

    println(serverRequest)
    return serverRequest
}
