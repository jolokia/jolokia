package org.jolokia.discovery.multicast;

import java.io.IOException;
import java.net.*;
import java.util.List;

import org.jolokia.detector.ServerHandle;
import org.testng.annotations.*;

import static org.jolokia.discovery.multicast.MulticastUtil.*;

/**
 * @author roland
 * @since 27.01.14
 */

public class SocketListenerTest {

    URL url;
    private MulticastSocket socket;
    private SocketListener listener;

    @BeforeClass
    public void startSocketListener() throws IOException {
        socket = newSocket();
        url = new URL("http://localhost:8080/jolokia");
        ServerHandle handle = new ServerHandle("jolokia","jolokia-tester","1.0",url,null);
        listener = new SocketListener(socket,handle);

        Thread thread = new Thread(listener);
        thread.start();
    }

    @AfterClass
    public void stopSocketListener() {
        listener.setRunning(false);
        socket.close();
    }

    @Test
    public void simple() throws IOException {

        DiscoveryOutgoingMessage out =
                new DiscoveryOutgoingMessage.Builder(AbstractDiscoveryMessage.MessageType.QUERY)
                .build();
        for (int port = 22332; port < 22500; port++) {
            try {
                List<DiscoveryIncomingMessage> discovered = sendQueryAndCollectAnswers(out);
                for (DiscoveryIncomingMessage in : discovered) {
                    System.out.println(in);
                }
                return;
            } catch (BindException exp) {
                // Try next port ...
            }
        }
    }
}
