package lab4.snake.util;

import java.net.InetSocketAddress;

public final class Config {
    private Config() {}

    public static final String MULTICAST_IP = "239.192.0.4";
    public static final int MULTICAST_PORT = 9192;
    public static final InetSocketAddress MULTICAST_ADDRESS =
            new InetSocketAddress(MULTICAST_IP, MULTICAST_PORT);

    public static final int ANNOUNCEMENT_INTERVAL_MS = 1000;

    public static final int GAME_ANNOUNCEMENT_EXPIRE_MS = 3000;

    public static final int UDP_BUFFER_SIZE = 65535;

    public static final int NEW_SNAKE_SQUARE_SIZE = 5;

    public static final double FOOD_FROM_DEAD_SNAKE_PROBABILITY = 0.5;

    public static final int SOCKET_TIMEOUT = 1000;
}
