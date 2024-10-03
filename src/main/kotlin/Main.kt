import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket;

fun main() {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")

    // Uncomment this block to pass the first stage
    val serverSocket = ServerSocket(4221)

    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true

    // To try this locally on macOS, you could run ./your_server.sh in one terminal session,
    // and nc -vz 127.0.0.1 4221 in another. (-v gives more verbose output, -z just scan for listening daemons, without sending any data to them.)
    // curl -v http://localhost:4221

    while (true) {
        val clientSocket = serverSocket.accept() // Wait for connection from client.
        println("accepted new connection")
        val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
        val output = PrintWriter(clientSocket.getOutputStream(), true)

        // Read the request line
        val requestStatusLine = input.readLine()
        println("requestStatusLine: $requestStatusLine")

        val requestHostPort = input.readLine()
        println("requestUrl: $requestHostPort")

        val requestUserAgent = input.readLine()
        println("requestUserAgent: $requestUserAgent")

        val requestHeader = input.readLine()
        println("requestHeader: $requestHeader")

        // I think this is request body
        val requestBody = input.readLine()
        println("requestBody: $requestBody")

        val httpResponse = buildResponse(
            requestStatusLine = requestStatusLine,
            requestHostPort = requestHostPort,
        )
        println()
        println("httpResponse $httpResponse")

        output.print(httpResponse)
        output.close()
    }
    //output.close()
}

fun buildResponse(requestStatusLine: String, requestHostPort: String): String {

    // Status
    val responseStatusLine = buildResponseStatusLine(requestStatusLine, requestHostPort)

    // Headers (Empty)
    val crlfHeadersLine = "\r\n"

    // Response body (empty)

    return "$responseStatusLine $crlfHeadersLine"
}

fun buildResponseStatusLine(
    requestStatusLine: String,
    requestHostPort: String,
): String {
    // GET /index.html HTTP/1.1
    val requestStatusLineArray = requestStatusLine.split(" ")

    // Host: localhost:4221\r\n
    val requestUrlArray = requestHostPort.split(" ")

    // localhost:4221  /index.html
    val requestUrl = requestUrlArray[1] + requestStatusLineArray[1]
    val localServerUrl = "localhost:4221/"

    val httpVersion = "HTTP/1.1"
    val crlfStatusLine = "\r\n"

    return if (requestUrl == localServerUrl) {
        val statusCode = "200"
        val optionalReasonPhrase = "OK"

        // Status line
        "$httpVersion $statusCode $optionalReasonPhrase$crlfStatusLine"
    } else {
        val statusCode = "404"
        val optionalReasonPhrase = "Not Found"

        // Status line
        "$httpVersion $statusCode $optionalReasonPhrase$crlfStatusLine"
    }
}
