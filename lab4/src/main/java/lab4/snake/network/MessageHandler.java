package lab4.snake.network;

import lab4.protobuf.SnakesProto;
import lab4.snake.model.*;
import lab4.snake.util.ProtoConverter;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

public class MessageHandler {

    private Consumer<AnnouncementEvent> onAnnouncement;
    private Consumer<DiscoverEvent> onDiscover;
    private Consumer<JoinEvent> onJoin;
    private Consumer<AckEvent> onAck;
    private Consumer<StateEvent> onState;
    private Consumer<SteerEvent> onSteer;
    private Consumer<PingEvent> onPing;
    private Consumer<RoleChangeEvent> onRoleChange;
    private Consumer<ErrorEvent> onError;

    public record AnnouncementEvent(
            SnakesProto.GameMessage.AnnouncementMsg announcement,
            InetSocketAddress sender
    ) {}

    public record DiscoverEvent(InetSocketAddress sender) {}

    public record JoinEvent(
            SnakesProto.GameMessage.JoinMsg join,
            long msgSeq,
            InetSocketAddress sender
    ) {}

    public record AckEvent(
            long msgSeq,
            int senderId,
            int receiverId,
            InetSocketAddress sender
    ) {}

    public record StateEvent(
            SnakesProto.GameState state,
            long msgSeq,
            InetSocketAddress sender
    ) {}

    public record SteerEvent(
            Direction direction,
            long msgSeq,
            int senderId,
            InetSocketAddress sender
    ) {}

    public record PingEvent(
            long msgSeq,
            int senderId,
            InetSocketAddress sender
    ) {}

    public record RoleChangeEvent(
            NodeRole senderRole,
            NodeRole receiverRole,
            int senderId,
            int receiverId,
            long msgSeq,
            InetSocketAddress sender
    ) {}

    public record ErrorEvent(
            String errorMessage,
            long msgSeq,
            InetSocketAddress sender
    ) {}





    public void handle(SnakesProto.GameMessage message, InetSocketAddress sender) {
        if (message.hasAnnouncement()) {
            handleAnnouncement(message, sender);
        } else if (message.hasDiscover()) {
            handleDiscover(sender);
        } else if (message.hasJoin()) {
            handleJoin(message, sender);
        } else if (message.hasAck()) {
            handleAck(message, sender);
        } else if (message.hasState()) {
            handleState(message, sender);
        } else if (message.hasSteer()) {
            handleSteer(message, sender);
        } else if (message.hasPing()) {
            handlePing(message, sender);
        } else if (message.hasRoleChange()) {
            handleRoleChange(message, sender);
        } else if (message.hasError()) {
            handleError(message, sender);
        }
    }

    private void handleAnnouncement(SnakesProto.GameMessage message, InetSocketAddress sender) {
        if (onAnnouncement != null) {
            onAnnouncement.accept(new AnnouncementEvent(message.getAnnouncement(), sender));
        }
    }

    private void handleDiscover(InetSocketAddress sender) {
        if (onDiscover != null) {
            onDiscover.accept(new DiscoverEvent(sender));
        }
    }

    private void handleJoin(SnakesProto.GameMessage message, InetSocketAddress sender) {
        if (onJoin != null) {
            onJoin.accept(new JoinEvent(
                    message.getJoin(),
                    message.getMsgSeq(),
                    sender
            ));
        }
    }

    private void handleAck(SnakesProto.GameMessage message, InetSocketAddress sender) {
        if (onAck != null) {
            onAck.accept(new AckEvent(
                    message.getMsgSeq(),
                    message.hasSenderId() ? message.getSenderId() : -1,
                    message.hasReceiverId() ? message.getReceiverId() : -1,
                    sender
            ));
        }
    }

    private void handleState(SnakesProto.GameMessage message, InetSocketAddress sender) {
        if (onState != null) {
            onState.accept(new StateEvent(
                    message.getState().getState(),
                    message.getMsgSeq(),
                    sender
            ));
        }
    }

    private void handleSteer(SnakesProto.GameMessage message, InetSocketAddress sender) {
        if (onSteer != null) {
            Direction direction = ProtoConverter.fromProto(message.getSteer().getDirection());
            onSteer.accept(new SteerEvent(
                    direction,
                    message.getMsgSeq(),
                    message.hasSenderId() ? message.getSenderId() : -1,
                    sender
            ));
        }
    }

    private void handlePing(SnakesProto.GameMessage message, InetSocketAddress sender) {
        if (onPing != null) {
            onPing.accept(new PingEvent(
                    message.getMsgSeq(),
                    message.hasSenderId() ? message.getSenderId() : -1,
                    sender
            ));
        }
    }

    private void handleRoleChange(SnakesProto.GameMessage message, InetSocketAddress sender) {
        if (onRoleChange != null) {
            SnakesProto.GameMessage.RoleChangeMsg rc = message.getRoleChange();

            NodeRole senderRole = rc.hasSenderRole() ?
                    ProtoConverter.fromProto(rc.getSenderRole()) : null;
            NodeRole receiverRole = rc.hasReceiverRole() ?
                    ProtoConverter.fromProto(rc.getReceiverRole()) : null;

            onRoleChange.accept(new RoleChangeEvent(
                    senderRole,
                    receiverRole,
                    message.hasSenderId() ? message.getSenderId() : -1,
                    message.hasReceiverId() ? message.getReceiverId() : -1,
                    message.getMsgSeq(),
                    sender
            ));
        }
    }

    private void handleError(SnakesProto.GameMessage message, InetSocketAddress sender) {
        if (onError != null) {
            onError.accept(new ErrorEvent(
                    message.getError().getErrorMessage(),
                    message.getMsgSeq(),
                    sender
            ));
        }
    }




    public void setOnAnnouncement(Consumer<AnnouncementEvent> handler) {
        this.onAnnouncement = handler;
    }

    public void setOnDiscover(Consumer<DiscoverEvent> handler) {
        this.onDiscover = handler;
    }

    public void setOnJoin(Consumer<JoinEvent> handler) {
        this.onJoin = handler;
    }

    public void setOnAck(Consumer<AckEvent> handler) {
        this.onAck = handler;
    }

    public void setOnState(Consumer<StateEvent> handler) {
        this.onState = handler;
    }

    public void setOnSteer(Consumer<SteerEvent> handler) {
        this.onSteer = handler;
    }

    public void setOnPing(Consumer<PingEvent> handler) {
        this.onPing = handler;
    }

    public void setOnRoleChange(Consumer<RoleChangeEvent> handler) {
        this.onRoleChange = handler;
    }

    public void setOnError(Consumer<ErrorEvent> handler) {
        this.onError = handler;
    }
}