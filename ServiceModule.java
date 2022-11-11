import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.sql.*;
import java.io.File;
import java.util.Scanner;

class QueryRunner implements Runnable {

    // Declare socket for client access
    protected Socket socketConnection;

    public static String bookTickets(
            Connection conn,
            String trainNo,
            String date,
            String pref,
            String[] names
        ) {
        try {
            conn.beginRequest();

            Statement stmt = conn.createStatement();
            // stmt.executeUpdate("begin;");
            // stmt.executeUpdate("set transaction isolation level serializable;");

            CallableStatement cstmt = conn.prepareCall("{call book_tickets(?, ?, ?, ?, ?)}");

            cstmt.setString(1, trainNo);
            cstmt.setDate(2, java.sql.Date.valueOf(date));
            cstmt.setString(3, pref);
            cstmt.setArray(4, conn.createArrayOf("text", names));
            cstmt.registerOutParameter(5, java.sql.Types.VARCHAR);

            cstmt.executeUpdate();
            // stmt.executeUpdate("commit;");

            String result = cstmt.getString(7);

            cstmt.close();
            return result;

        } catch (Exception e) {
            return e.getMessage();

        }
    }

    public QueryRunner(Socket clientSocket) {
        this.socketConnection = clientSocket;
    }

    public void run() {
        try {
            // Reading data from client
            InputStreamReader inputStream = new InputStreamReader(socketConnection.getInputStream());
            BufferedReader bufferedInput = new BufferedReader(inputStream);
            OutputStreamWriter outputStream = new OutputStreamWriter(socketConnection.getOutputStream());
            BufferedWriter bufferedOutput = new BufferedWriter(outputStream);
            PrintWriter printWriter = new PrintWriter(bufferedOutput, true);
            String clientCommand = "";
            String responseQuery = "";
            Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/train_system",
                "postgres", "2486"
            );

            conn.setAutoCommit(true);
            conn.setTransactionIsolation(8);

            String[] params; 
            String trainNo; 
            String date; 
            String preference; 
            Integer numberOfPassengers;
            // Read client query from the socket endpoint
            clientCommand = bufferedInput.readLine();
            while (!clientCommand.equals("#")) {
                
                System.out.println("Recieved data <" + clientCommand + "> from client : " + socketConnection.getRemoteSocketAddress().toString());
                params = clientCommand.split("\\s+");
                numberOfPassengers = Integer.valueOf(params[0]);
                String[] names = new String[numberOfPassengers]; 

                for (int i=0; i<numberOfPassengers; i++){
                    names[i] = params[i+1].substring(0, params[i+1].length()-1);
                }

                trainNo = params[numberOfPassengers+1];
                date = params[numberOfPassengers+2];
                preference = params[numberOfPassengers+3];

                System.out.println(trainNo.length());
                System.out.println(date);
                System.out.println(names.toString());

                responseQuery = bookTickets(conn, trainNo, date, preference, names);
                /*******************************************
                ********************************************/

                // Dummy response send to client
                // Sending data back to the client
                printWriter.println(responseQuery);
                // Read next client query
                clientCommand = bufferedInput.readLine();
            }
            conn.close();
            inputStream.close();
            bufferedInput.close();
            outputStream.close();
            bufferedOutput.close();
            printWriter.close();
            socketConnection.close();
        } catch (Exception e) {
            return;
        }
    }
}

/**
 * Main Class to controll the program flow
 */
public class ServiceModule {
    // Server listens to port
    static int serverPort = 7008;
    // Max no of parallel requests the server can process
    static int numServerCores = 5;

    public static String addTrain(Connection conn, String trainNo) {
        try {
            conn.beginRequest();
            CallableStatement cstmt = conn.prepareCall("{call add_train(?)}");

            cstmt.setString(1, trainNo);
            cstmt.executeUpdate();

            cstmt.close();
            return String.format("Train: %s added", trainNo);

        } catch (Exception e) {

            return e.getMessage();

        }
    }

    public static String releaseTrain(
            Connection conn,
            String trainNo,
            String date,
            Integer acCoaches,
            Integer slCoaches) {
        try {
            conn.beginRequest();
            CallableStatement cstmt = conn.prepareCall("{call release_train(?, ?, ?, ?)}");

            cstmt.setString(1, trainNo);
            cstmt.setDate(2, Date.valueOf(date));
            cstmt.setInt(3, acCoaches);
            cstmt.setInt(4, slCoaches);
            cstmt.executeUpdate();

            cstmt.close();

            return String.format("Released train %s with %d AC coaches %d SL coaches on %s", trainNo, acCoaches,
                    slCoaches, date);

        } catch (Exception e) {

            return e.getMessage();

        }
    }

    public static void adminTask(String filename) {
        Connection adminConn = null;
        File input = null;
        Scanner inputScanner = null;

        try {
            input = new File(filename);
            inputScanner = new Scanner(input);
            Class.forName("org.postgresql.Driver");

            String query = "";
            String[] params;
            String trainNo = "";
            String Date = "";
            Integer acCoaches = 0;
            Integer slCoaches = 0;

            adminConn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/train_system",
                    "postgres", "2486");

            while (inputScanner.hasNextLine()) {
                query = inputScanner.nextLine();
                if (query.equals("#"))
                    break;
                params = query.split("\\s+");
                trainNo = params[0];
                Date = params[1];
                acCoaches = Integer.valueOf(params[2]);
                slCoaches = Integer.valueOf(params[3]);
                System.out.println(releaseTrain(adminConn, trainNo, Date, acCoaches, slCoaches));
                System.out.println(String.format("Inserted train: %s", trainNo));
            }

            inputScanner.close();
            adminConn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }

    }

    // ------------ Main----------------------
    public static void main(String[] args) throws IOException {

        // Creating a thread pool

        adminTask("./Input/admin_input.txt");

        ExecutorService executorService = Executors.newFixedThreadPool(numServerCores);

        // Creating a server socket to listen for clients
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            Socket socketConnection = null;

            // Always-ON server
            while (true) {
                System.out.println("Listening port : " + serverPort
                        + "\nWaiting for clients...");
                socketConnection = serverSocket.accept(); // Accept a connection from a client
                System.out.println("Accepted client :"
                        + socketConnection.getRemoteSocketAddress().toString()
                        + "\n");
                // Create a runnable task
                Runnable runnableTask = new QueryRunner(socketConnection);
                // Submit task for execution
                executorService.submit(runnableTask);
            }
        }
    }
}