package io.github.udayhe.quicksilver.command.implementation;

import io.github.udayhe.quicksilver.command.Command;

import java.net.Socket;
import java.util.logging.Logger;

import static io.github.udayhe.quicksilver.constant.Constants.BYE;

public class Exit<K, V> implements Command<K, V> {

    private static final Logger log = Logger.getLogger(Exit.class.getName());
    private final Socket socket;

    public Exit(Socket socket) {
        this.socket = socket;
    }

    @Override
    public String execute(K unusedKey, V unusedValue) {
        try {
            log.info("🔌 Closing connection for client: "+ socket.getRemoteSocketAddress());
            socket.close();
            return BYE;
        } catch (Exception e) {
            log.severe("❌ Error closing client connection:"+ e);
            return "ERROR: Unable to close connection";
        }
    }
}
