package dns;

import constants.Constants;
import controller.ClientSession;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DnsResolver {
    private final DatagramChannel dnsChannel;
    private final InetSocketAddress dnsResolver;
    private final Map<Integer, PendingDns> pendingDns = new HashMap<>();

    public DnsResolver(Selector selector) throws IOException {
        this.dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        dnsChannel.bind(null);
        dnsChannel.register(selector, SelectionKey.OP_READ);

        this.dnsResolver = new InetSocketAddress(Constants.DNS_RESOLVING_GOOGLE_IP, Constants.DNS_RESOLVING_GOOGLE_PORT);
    }

    public void sendDnsQuery(ClientSession session, String hostname, int port) {
        try {
            Name name = Name.fromString(hostname.endsWith(".") ? hostname : hostname + ".");
            Record rec = Record.newRecord(name, Type.A, DClass.IN);
            Message msg = Message.newQuery(rec);

            int id = msg.getHeader().getID();
            byte[] wire = msg.toWire();
            ByteBuffer byteBuffer = ByteBuffer.wrap(wire);
            dnsChannel.send(byteBuffer, dnsResolver);
            PendingDns pending = new PendingDns(id, session, hostname, port);
            pendingDns.put(id, pending);
            System.out.println("DNS query id=" + id + " for " + hostname);
        } catch (Exception e) {
            session.onDnsFailed("dns build/send error: " + e);
        }
    }

    public boolean isDnsChannel(Object channel) {
        return channel == dnsChannel;
    }

    public void handleDnsRead() {
        try {
            ByteBuffer buf = ByteBuffer.allocate(Constants.BUFFER_DNS);
            SocketAddress socketAddress = dnsChannel.receive(buf);
            if (socketAddress == null) return;
            buf.flip();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            Message response = new Message(bytes);
            int id = response.getHeader().getID();
            PendingDns pending = pendingDns.remove(id);
            if (pending == null) {
                System.out.println("DNS reply id=" + id + " not matched");
                return;
            }

            List<InetSocketAddress> list = new ArrayList<>();
            for (Record r : response.getSectionArray(Section.ANSWER)) {
                if (r.getType() == Type.A) {
                    InetAddress ia = ((ARecord) r).getAddress();
                    list.add(new InetSocketAddress(ia, pending.port));
                }
            }
            if (list.isEmpty()) pending.session.onDnsFailed("no A records");
            else pending.session.onDnsResolved(list);
        } catch (Exception e) {
            System.out.println("DNS read/parse error: " + e);
        }
    }

    private record PendingDns(int id, ClientSession session, String hostname, int port) {
    }
}
