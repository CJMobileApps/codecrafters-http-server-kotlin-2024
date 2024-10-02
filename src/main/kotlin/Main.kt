import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket;

fun main() {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")

    // Uncomment this block to pass the first stage
    var serverSocket = ServerSocket(4221)

    // Since the tester restarts your program quite often, setting SO_REUSEADDR
    // ensures that we don't run into 'Address already in use' errors
    serverSocket.reuseAddress = true

    // To try this locally on macOS, you could run ./your_server.sh in one terminal session,
    // and nc -vz 127.0.0.1 4221 in another. (-v gives more verbose output, -z just scan for listening daemons, without sending any data to them.)

    val clientSocket = serverSocket.accept() // Wait for connection from client.
    println("accepted new connection")

    //while (true) {

    val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
    val output = PrintWriter(clientSocket.getOutputStream(), true)


    // Read the request line
    val requestLine = input.readLine()
    println("Request: $requestLine")

    output.print("HTTP/1.1 200 OK\\r\\n\\r\\n")
    //output.println("Content-Type: text/plain");
    //}

}
