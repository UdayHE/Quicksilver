package io.github.udayhe.quicksilver.client;

import io.github.udayhe.quicksilver.cluster.ClusterClient;
import io.github.udayhe.quicksilver.cluster.ClusterNode;
import io.github.udayhe.quicksilver.cluster.ClusterService;
import io.github.udayhe.quicksilver.command.CommandRegistry;
import io.github.udayhe.quicksilver.db.DB;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.github.udayhe.quicksilver.constant.Constants.*;
import static io.github.udayhe.quicksilver.enums.Command.*;
import static io.github.udayhe.quicksilver.util.ClusterUtil.isLocalNode;

public class ClientHandler<K, V> implements Runnable {

    private static final Logger log = Logger.getLogger(ClientHandler.class.getName());
    private final Socket socket;
    private final DB<K, V> db;
    private final BufferedReader in;
    private final PrintWriter out;
    private final ClusterService clusterService;

    public ClientHandler(Socket socket,
                         DB<K, V> db,
                         ClusterService clusterService) throws IOException {
        this.socket = socket;
        this.db = db;
        this.clusterService = clusterService;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void run() {
        log.log(Level.INFO, "📡 New client connected: {0}", socket.getRemoteSocketAddress());
        CommandRegistry<K, V> commandRegistry = new CommandRegistry<>(db, clusterService.getClusterManager(), socket);
        try {
            this.out.println(LOGO);
            String line;
            while ((line = readCommand()) != null) {
                log.log(Level.INFO, "📩 Received command: {0}", line);
                String[] parts = line.trim().split(SPACE);
                if (parts.length == 0 || parts[0].isEmpty()) continue;

                String cmd = parts[0].toUpperCase();
                K key = (parts.length > 1) ? (K) parts[1] : null;
                V value = (parts.length > 2) ? (V) parts[2] : null;

                if (exit(cmd)) return;
                if (shouldContinue(cmd, commandRegistry, key)) continue;

                ClusterNode targetNode = this.clusterService.getConsistentHashing().getNodeForKey(parts[1]);
                if (redirectToOtherNode(targetNode, line)) continue;

                // Process command locally
                String response = commandRegistry.executeCommand(cmd, key, value);
                sendResponse(response);
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "❌ Client communication error:", e);
        }
    }


    /**
     * Reads a command from the client
     */
    public String readCommand() throws IOException {
        return this.in.readLine();
    }

    /**
     * Sends a response back to the client
     */
    public void sendResponse(String response) {
        this.out.println(response);
    }

    private boolean invalidCommand(String cmd, K key) {
        if ((cmd.equals(SET.name()) || cmd.equals(DEL.name())) && key == null) {
            sendResponse("ERROR: Missing key");
            return true;
        }
        if (cmd.equals(GET.name()) && key == null) {
            sendResponse("ERROR: GET requires a key");
            return true;
        }
        return false;
    }

    private boolean commandWithoutKeyValue(String command,
                                           String commandValue,
                                           CommandRegistry<K, V> commandRegistry) {
        if (command.equalsIgnoreCase(commandValue)) {
            String response = commandRegistry.executeCommand(command, null, null);
            sendResponse(response);
            return true;
        }
        return false;
    }

    private boolean exit(String command) throws IOException {
        if (command.equalsIgnoreCase(EXIT.name())) {
            log.log(Level.INFO, "🔌 Client disconnected: {0}", socket.getRemoteSocketAddress());
            sendResponse(BYE);
            this.socket.close();
            return true;
        }
        return false;
    }

    private boolean redirectToOtherNode(ClusterNode targetNode, String line) {
        if (!isLocalNode(targetNode, this.socket.getLocalPort())) {
            log.log(Level.INFO, "🔄 Redirecting request [{0}] to node {1}", new Object[]{line, targetNode});
            String response = ClusterClient.sendRequest(targetNode, line);
            if (!response.equals(ERROR)) {
                sendResponse(response);
            } else {
                log.log(Level.SEVERE, "❌ Failed to process command [{0}] on node {1}", new Object[]{line, targetNode});
                sendResponse("ERROR: Failed to process request");
            }
            return true;
        }
        return false;
    }

    private boolean shouldContinue(String cmd, CommandRegistry<K, V> commandRegistry, K key) {
        if (commandWithoutKeyValue(cmd, FLUSH.name(), commandRegistry)) return true;
        if (commandWithoutKeyValue(cmd, DUMP.name(), commandRegistry)) return true;
        return invalidCommand(cmd, key);
    }
}