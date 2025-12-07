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
import java.util.*;

public class DnsResolver {
    private final DatagramChannel dnsChannel;
    private final InetSocketAddress dnsResolver;
    private final Map<Integer, PendingDns> pendingDns = new HashMap<>();

    private final ByteBuffer readBuffer;

    public DnsResolver(Selector selector) throws IOException {
        this.dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        dnsChannel.bind(null);
        dnsChannel.register(selector, SelectionKey.OP_READ);

        this.dnsResolver = new InetSocketAddress(Constants.DNS_RESOLVING_GOOGLE_IP, Constants.DNS_RESOLVING_GOOGLE_PORT);

        this.readBuffer = ByteBuffer.allocate(Constants.BUFFER_DNS);
    }

    public void sendDnsQuery(ClientSession session, String hostname, int port) {
        cleanupExpiredPending();

        if (pendingDns.size() >= Constants.MAX_PENDING_REQUESTS) {
            session.onDnsFailed("dns too many pending requests");
        }

        try {
            Name name = Name.fromString(hostname.endsWith(".") ? hostname : hostname + ".");
            Record rec = Record.newRecord(name, Type.A, DClass.IN);
            Message msg = Message.newQuery(rec);

            int id = msg.getHeader().getID();
            byte[] wire = msg.toWire();
            ByteBuffer byteBuffer = ByteBuffer.wrap(wire);
            dnsChannel.send(byteBuffer, dnsResolver);
            PendingDns pending = new PendingDns(id, session, hostname, port, System.currentTimeMillis());
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
        readBuffer.clear();
        try {
            SocketAddress socketAddress = dnsChannel.receive(readBuffer);
            if (socketAddress == null) return;
        } catch (Exception e) {
            System.out.println("DNS read/parse error: " + e);
            cleanupExpiredPending();
            return;
        }
        readBuffer.flip();
        int len = readBuffer.remaining();
        if (len <= 0) return;


        byte[] bytes = new byte[len];
        readBuffer.get(bytes);
        Message response;
        try {
            response = new Message(bytes);
        } catch (Exception e) {
            System.out.println("DNS parse failed: " + e);
            return;
        }

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

        if (list.isEmpty()) {
            pending.session.onDnsFailed("no A records");
        } else {
            pending.session.onDnsResolved(list);
        }

        cleanupExpiredPending();
    }

    private void cleanupExpiredPending() {
        if (pendingDns.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Integer> expired = new ArrayList<>();
        for (Map.Entry<Integer, PendingDns> en : pendingDns.entrySet()) {
            PendingDns pd = en.getValue();
            if (now - pd.sentAt > Constants.PENDING_TIMEOUT_MS) expired.add(en.getKey());
        }
        for (Integer id : expired) {
            PendingDns pd = pendingDns.remove(id);
            if (pd != null) pd.session.onDnsFailed("dns timeout");
        }
    }

    public void cancelPendingForSession(ClientSession session) {
        if (session == null) return;
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, PendingDns> e : pendingDns.entrySet()) {
            if (e.getValue().session == session) toRemove.add(e.getKey());
        }
        for (Integer id : toRemove) {
            pendingDns.remove(id);
        }
    }

    private record PendingDns(int id, ClientSession session, String hostname, int port, long sentAt) {
    }
}
