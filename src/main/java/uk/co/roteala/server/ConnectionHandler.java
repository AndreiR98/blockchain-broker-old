package uk.co.roteala.server;

import com.google.common.util.concurrent.ListenableFuture;
import uk.co.roteala.net.MessageWriteTarget;

import java.io.IOException;

public class ConnectionHandler implements MessageWriteTarget {
    @Override
    public ListenableFuture writeBytes(byte[] message) throws IOException {
        return null;
    }

    @Override
    public void closeConnection() {

    }
}
