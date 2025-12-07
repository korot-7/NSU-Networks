package controller;

import dns.DnsResolver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;


public class ServerController {
    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;
    private final DnsResolver dnsResolver;


    public ServerController(String[] args) throws IOException {
        if (args.length < 1) throw new IllegalArgumentException("Missing port");
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Port must be a number (0-65535)");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port out of range (0-65535): " + port);
        }

        this.selector = Selector.open();
        this.serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.dnsResolver = new DnsResolver(selector);

        System.out.println("SOCKS5 proxy started on port " + port);
    }

    public void start() throws IOException {
        try {
            while (!Thread.interrupted()) {
                selector.select();
                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    try {
                        if (!key.isValid()) continue;

                        if (key.channel() == serverSocketChannel && key.isAcceptable()) {
                            handleAccept();
                            continue;
                        }

                        if (dnsResolver.isDnsChannel(key.channel()) && key.isReadable()) {
                            dnsResolver.handleDnsRead();
                            continue;
                        }

                        ClientSession session = (ClientSession) key.attachment();

                        if (key.isReadable()) {
                            session.onReadable(key);
                            if (!key.isValid()) continue;
                        }
                        if (key.isWritable()) {
                            session.onWritable(key);
                            if (!key.isValid()) continue;
                        }
                        if (key.isConnectable()) {
                            session.onConnectable(key);
                        }
                    } catch (Exception e) {
                        ClientSession session = (ClientSession) key.attachment();
                        session.close();
                    }
                }
            }
        } finally {
            if (serverSocketChannel != null) serverSocketChannel.close();
            if (selector != null) selector.close();
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel client = serverSocketChannel.accept();
        client.configureBlocking(false);
        SelectionKey key = client.register(selector, SelectionKey.OP_READ);
        ClientSession session = new ClientSession(client, key, selector, dnsResolver);
        key.attach(session);
        System.out.println("Accepted client " + client.getRemoteAddress());
    }
}