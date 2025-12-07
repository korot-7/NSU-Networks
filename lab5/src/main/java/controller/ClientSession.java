package controller;

import constants.Constants;
import dns.DnsResolver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.List;

public class ClientSession {
    enum State {HANDSHAKE, REQUEST, CONNECTING, RELAY, CLOSED}

    ClientSession.State state = ClientSession.State.HANDSHAKE;
    final SocketChannel clientChannel;
    SelectionKey clientKey;
    private final Selector selector;
    private final DnsResolver dnsResolver;

    SocketChannel remoteChannel;
    SelectionKey remoteKey;

    final ByteBuffer clientToRemote = ByteBuffer.allocateDirect(Constants.BUFFER_SIZE);
    final ByteBuffer remoteToClient = ByteBuffer.allocateDirect(Constants.BUFFER_SIZE);

    String dstHost;
    int dstPort;
    InetSocketAddress dstAddr;

    public ClientSession(SocketChannel clientChannel, SelectionKey clientKey, Selector selector, DnsResolver dnsResolver) {
        this.clientChannel = clientChannel;
        this.clientKey = clientKey;
        this.selector = selector;
        this.dnsResolver = dnsResolver;
    }

    void onReadable(SelectionKey key) throws IOException {
        if (key.channel() == clientChannel) {
            if (state == ClientSession.State.HANDSHAKE || state == ClientSession.State.REQUEST) {
                int r = clientChannel.read(clientToRemote);
                if (r < 0) {
                    close();
                    return;
                }
                clientToRemote.flip();
                if (state == ClientSession.State.HANDSHAKE) {
                    if (!tryHandleHandshake()) {
                        clientToRemote.compact();
                        return;
                    }
                    state = ClientSession.State.REQUEST;
                }
                if (state == ClientSession.State.REQUEST) {
                    if (!tryHandleRequest()) {
                        clientToRemote.compact();
                        return;
                    }
                }
                clientToRemote.compact();
            } else if (state == ClientSession.State.RELAY) {
                if (clientToRemote.remaining() == 0) {
                    disableOp(clientKey, SelectionKey.OP_READ);
                    return;
                }
                int r = clientChannel.read(clientToRemote);
                if (r < 0) {
                    if (remoteChannel != null && remoteChannel.isOpen()) remoteChannel.shutdownOutput();
                    disableOp(clientKey, SelectionKey.OP_READ);
                } else if (r > 0) {
                    enableOp(remoteKey, SelectionKey.OP_WRITE);
                }
            }
        } else if (key.channel() == remoteChannel) {
            if (remoteToClient.remaining() == 0) {
                disableOp(remoteKey, SelectionKey.OP_READ);
                return;
            }
            int r = remoteChannel.read(remoteToClient);
            if (r < 0) {
                if (clientChannel != null && clientChannel.isOpen()) clientChannel.shutdownOutput();
                disableOp(remoteKey, SelectionKey.OP_READ);
            } else if (r > 0) {
                if (clientKey != null && clientKey.isValid()) enableOp(clientKey, SelectionKey.OP_WRITE);
            }
        }
    }

    void onWritable(SelectionKey key) throws IOException {
        if (key.channel() == clientChannel) {
            remoteToClient.flip();
            clientChannel.write(remoteToClient);
            remoteToClient.compact();
            if (remoteToClient.position() < Constants.BUFFER_SIZE) {
                if (remoteKey != null && remoteKey.isValid()) enableOp(remoteKey, SelectionKey.OP_READ);
            }
            if (remoteToClient.position() == 0) disableOp(clientKey, SelectionKey.OP_WRITE);
        } else if (key.channel() == remoteChannel) {
            clientToRemote.flip();
            remoteChannel.write(clientToRemote);
            clientToRemote.compact();
            if (clientToRemote.position() == 0) disableOp(remoteKey, SelectionKey.OP_WRITE);
        }
    }

    void onConnectable(SelectionKey key) {
        if (key.channel() != remoteChannel) return;
        try {
            if (remoteChannel.finishConnect()) {
                sendSocksReplySuccess();
                state = ClientSession.State.RELAY;
                enableOp(clientKey, SelectionKey.OP_READ);
                enableOp(remoteKey, SelectionKey.OP_READ);
            }
        } catch (IOException e) {
            sendSocksReplyFailure(Constants.REP_CONNECTION_REFUSED);
            close();
        }
    }

    private boolean tryHandleHandshake() throws IOException {
        if (clientToRemote.remaining() < Constants.BUFFER_HANDSHAKE) return false;
        clientToRemote.mark();
        byte ver = clientToRemote.get();
        byte nmethods = clientToRemote.get();
        if (ver != Constants.VERSION_SOCKS) {
            close();
            return false;
        }

        if (clientToRemote.remaining() < (nmethods & Constants.BYTE_MASK)) {
            clientToRemote.reset();
            return false;
        }
        boolean noAuth = false;
        for (int i = 0; i < nmethods; i++) if (clientToRemote.get() == Constants.NOAUTH) noAuth = true;
        ByteBuffer out = ByteBuffer.allocate(Constants.BUFFER_HANDSHAKE);
        out.put(Constants.VERSION_SOCKS);
        out.put(noAuth ? Constants.NOAUTH : Constants.NO_ACCEPTABLE_METHODS);
        out.flip();
        clientChannel.write(out);
        enableOp(clientKey, SelectionKey.OP_WRITE);
        if (!noAuth) {
            close();
            return false;
        }
        return true;
    }

    private boolean tryHandleRequest() throws IOException {
        if (clientToRemote.remaining() < Constants.BUFFER_REQUEST) return false;
        clientToRemote.mark();
        byte ver = clientToRemote.get();
        byte cmd = clientToRemote.get();
        clientToRemote.get();
        byte atyp = clientToRemote.get();
        if (ver != Constants.VERSION_SOCKS) {
            close();
            return false;
        }
        if (cmd != Constants.CMD_CONNECT) {
            sendSocksReplyFailure(Constants.REP_COMMAND_NOT_SUPPORTED);
            close();
            return true;
        }
        if (atyp == Constants.ATYP_IP_V4) {
            if (clientToRemote.remaining() < Constants.IPV4_ADDRESS_BYTES + Constants.PORT_BYTES) {
                clientToRemote.reset();
                return false;
            }
            byte[] addr = new byte[Constants.IPV4_ADDRESS_BYTES];
            clientToRemote.get(addr);
            int port = ((clientToRemote.get() & Constants.BYTE_MASK) << Constants.COUNT_BITS_IN_BYTE) | (clientToRemote.get() & Constants.BYTE_MASK);
            dstAddr = new InetSocketAddress(InetAddress.getByAddress(addr), port);
            startConnectToDst();
            return true;
        } else if (atyp == Constants.ATYP_DOMAINNAME) {
            if (clientToRemote.remaining() < Constants.DOMAIN_LENGTH_BYTES) {
                clientToRemote.reset();
                return false;
            }
            int len = clientToRemote.get() & Constants.BYTE_MASK;
            if (clientToRemote.remaining() < len + Constants.PORT_BYTES) {
                clientToRemote.reset();
                return false;
            }
            byte[] name = new byte[len];
            clientToRemote.get(name);
            dstHost = new String(name);
            dstPort = ((clientToRemote.get() & Constants.BYTE_MASK) << Constants.COUNT_BITS_IN_BYTE) | (clientToRemote.get() & Constants.BYTE_MASK);
            state = ClientSession.State.CONNECTING;
            dnsResolver.sendDnsQuery(this, dstHost, dstPort);
            return true;
        } else {
            sendSocksReplyFailure(Constants.REP_ADDRESS_TYPE_NOT_SUPPORTED);
            close();
            return true;
        }
    }

    public void onDnsResolved(List<InetSocketAddress> addrs) {
        if (state == ClientSession.State.CLOSED) return;
        if (addrs.isEmpty()) {
            onDnsFailed("no addresses");
            return;
        }
        this.dstAddr = addrs.getFirst();
        try {
            startConnectToDst();
        } catch (IOException e) {
            onDnsFailed("connect error: " + e);
        }
    }

    public void onDnsFailed(String reason) {
        System.out.println("DNS failed: " + reason);
        sendSocksReplyFailure(Constants.REP_HOST_UNREACHABLE);
        close();
    }

    private void startConnectToDst() throws IOException {
        if (dstAddr == null) {
            sendSocksReplyFailure(Constants.REP_HOST_UNREACHABLE);
            close();
            return;
        }
        remoteChannel = SocketChannel.open();
        remoteChannel.configureBlocking(false);
        remoteChannel.connect(dstAddr);
        remoteKey = remoteChannel.register(selector, SelectionKey.OP_CONNECT, this);
        disableOp(clientKey, SelectionKey.OP_READ);
        state = ClientSession.State.CONNECTING;
    }

    private void sendSocksReplySuccess() {
        try {
            ByteBuffer out = ByteBuffer.allocate(Constants.BUFFER_REPLY);
            out.put(Constants.VERSION_SOCKS).put(Constants.REP_SUCCEEDED).put(Constants.RESERVED).put(Constants.ATYP_IP_V4);
            out.put(Constants.EMPTY_IPV4);
            out.putShort(Constants.EMPTY_PORT);
            out.flip();
            clientChannel.write(out);
            enableOp(clientKey, SelectionKey.OP_WRITE);
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    private void sendSocksReplyFailure(byte rep) {
        try {
            ByteBuffer out = ByteBuffer.allocate(Constants.BUFFER_REPLY);
            out.put(Constants.VERSION_SOCKS).put(rep).put(Constants.RESERVED).put(Constants.ATYP_IP_V4);
            out.put(Constants.EMPTY_IPV4);
            out.putShort(Constants.EMPTY_PORT);
            out.flip();
            clientChannel.write(out);
            enableOp(clientKey, SelectionKey.OP_WRITE);
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    void close() {
        state = ClientSession.State.CLOSED;
        try {
            dnsResolver.cancelPendingForSession(this);
            if (clientKey != null) clientKey.cancel();
            if (remoteKey != null) remoteKey.cancel();
            if (clientChannel != null) clientChannel.close();
            if (remoteChannel != null) remoteChannel.close();
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    void enableOp(SelectionKey key, int op) {
        if (key == null || !key.isValid()) return;
        int ops = key.interestOps();
        if ((ops & op) == 0) key.interestOps(ops | op);
    }

    void disableOp(SelectionKey key, int op) {
        if (key == null || !key.isValid()) return;
        int ops = key.interestOps();
        if ((ops & op) != 0) key.interestOps(ops & ~op);
    }
}
