package constants;

public class Constants {
    public static final int BUFFER_SIZE = 64 * 1024;
    public static final String DNS_RESOLVING_GOOGLE_IP = "8.8.8.8";
    public static final int DNS_RESOLVING_GOOGLE_PORT = 53;
    public static final int BUFFER_DNS = 4 * 1024;

    public static final byte NOAUTH = 0x00;
    public static final byte REP_SUCCEEDED = 0x00;
    public static final byte RESERVED = 0x00;


    public static final byte ATYP_IP_V4 = 0x01;
    public static final byte CMD_CONNECT = 0x01;

    public static final byte ATYP_DOMAINNAME = 0x03;

    public static final byte REP_HOST_UNREACHABLE = 0x04;

    public static final byte VERSION_SOCKS = 0x05;
    public static final byte REP_CONNECTION_REFUSED = 0x05;

    public static final byte REP_COMMAND_NOT_SUPPORTED = 0x07;

    public static final byte REP_ADDRESS_TYPE_NOT_SUPPORTED = 0x08;

    public static final byte NO_ACCEPTABLE_METHODS = (byte) 0xFF;
    public static final int BYTE_MASK = 0xFF;

    public static final int BUFFER_HANDSHAKE = 2;
    public static final int BUFFER_REQUEST = 4;
    public static final int BUFFER_REPLY = 10;

    public static final int PORT_BYTES = 2;
    public static final int IPV4_ADDRESS_BYTES = 4;
    public static final int DOMAIN_LENGTH_BYTES = 1;

    public static final int COUNT_BITS_IN_BYTE = 8;

    public static final byte[] EMPTY_IPV4 = new byte[]{0, 0, 0, 0};
    public static final short EMPTY_PORT = 0;

    public static final int PENDING_TIMEOUT_MS = 5000;
    public static final int MAX_PENDING_REQUESTS = 5000;
}
