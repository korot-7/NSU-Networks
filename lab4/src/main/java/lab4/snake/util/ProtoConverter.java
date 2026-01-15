package lab4.snake.util;

import lab4.protobuf.SnakesProto;
import lab4.snake.model.*;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

public final class ProtoConverter {
    private ProtoConverter() {}

    public static Direction fromProto(SnakesProto.Direction proto) {
        return switch (proto) {
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case LEFT -> Direction.LEFT;
            case RIGHT -> Direction.RIGHT;
        };
    }

    public static SnakesProto.Direction toProto(Direction dir) {
        return switch (dir) {
            case UP -> SnakesProto.Direction.UP;
            case DOWN -> SnakesProto.Direction.DOWN;
            case LEFT -> SnakesProto.Direction.LEFT;
            case RIGHT -> SnakesProto.Direction.RIGHT;
        };
    }




    public static NodeRole fromProto(SnakesProto.NodeRole proto) {
        return switch (proto) {
            case NORMAL -> NodeRole.NORMAL;
            case MASTER -> NodeRole.MASTER;
            case DEPUTY -> NodeRole.DEPUTY;
            case VIEWER -> NodeRole.VIEWER;
        };
    }

    public static SnakesProto.NodeRole toProto(NodeRole role) {
        return switch (role) {
            case NORMAL -> SnakesProto.NodeRole.NORMAL;
            case MASTER -> SnakesProto.NodeRole.MASTER;
            case DEPUTY -> SnakesProto.NodeRole.DEPUTY;
            case VIEWER -> SnakesProto.NodeRole.VIEWER;
        };
    }





    public static PlayerType fromProto(SnakesProto.PlayerType proto) {
        return switch (proto) {
            case HUMAN -> PlayerType.HUMAN;
            case ROBOT -> PlayerType.ROBOT;
        };
    }

    public static SnakesProto.PlayerType toProto(PlayerType type) {
        return switch (type) {
            case HUMAN -> SnakesProto.PlayerType.HUMAN;
            case ROBOT -> SnakesProto.PlayerType.ROBOT;
        };
    }





    public static SnakeState fromProto(SnakesProto.GameState.Snake.SnakeState proto) {
        return switch (proto) {
            case ALIVE -> SnakeState.ALIVE;
            case ZOMBIE -> SnakeState.ZOMBIE;
        };
    }

    public static SnakesProto.GameState.Snake.SnakeState toProto(SnakeState state) {
        return switch (state) {
            case ALIVE -> SnakesProto.GameState.Snake.SnakeState.ALIVE;
            case ZOMBIE -> SnakesProto.GameState.Snake.SnakeState.ZOMBIE;
        };
    }





    public static Coord fromProto(SnakesProto.GameState.Coord proto) {
        return new Coord(proto.getX(), proto.getY());
    }

    public static SnakesProto.GameState.Coord toProto(Coord coord) {
        return SnakesProto.GameState.Coord.newBuilder()
                .setX(coord.x())
                .setY(coord.y())
                .build();
    }

    public static List<Coord> coordListFromProto(List<SnakesProto.GameState.Coord> protoList) {
        return protoList.stream()
                .map(ProtoConverter::fromProto)
                .collect(Collectors.toList());
    }

    public static List<SnakesProto.GameState.Coord> coordListToProto(List<Coord> coords) {
        return coords.stream()
                .map(ProtoConverter::toProto)
                .collect(Collectors.toList());
    }





    public static GameConfig fromProto(SnakesProto.GameConfig proto) {
        return new GameConfig(
                proto.getWidth(),
                proto.getHeight(),
                proto.getFoodStatic(),
                proto.getStateDelayMs()
        );
    }

    public static SnakesProto.GameConfig toProto(GameConfig config) {
        return SnakesProto.GameConfig.newBuilder()
                .setWidth(config.width())
                .setHeight(config.height())
                .setFoodStatic(config.foodStatic())
                .setStateDelayMs(config.stateDelayMs())
                .build();
    }






    public static GamePlayer fromProto(SnakesProto.GamePlayer proto, InetSocketAddress senderAddress) {
        InetSocketAddress address = null;

        if (proto.hasIpAddress() && proto.hasPort() &&
                !proto.getIpAddress().isEmpty() && proto.getPort() > 0) {
            try {
                address = new InetSocketAddress(proto.getIpAddress(), proto.getPort());
            } catch (Exception _) {
            }
        }

        return new GamePlayer(
                proto.getId(),
                proto.getName(),
                address,
                fromProto(proto.getRole()),
                fromProto(proto.getType()),
                proto.getScore()
        );
    }


    public static SnakesProto.GamePlayer toProto(GamePlayer player, boolean includeAddress) {
        SnakesProto.GamePlayer.Builder builder = SnakesProto.GamePlayer.newBuilder()
                .setId(player.getId())
                .setName(player.getName())
                .setRole(toProto(player.getRole()))
                .setType(toProto(player.getType()))
                .setScore(player.getScore());

        if (includeAddress && player.getAddress() != null) {
            builder.setIpAddress(player.getAddress().getAddress().getHostAddress());
            builder.setPort(player.getAddress().getPort());
        }

        return builder.build();
    }

    public static List<GamePlayer> playerListFromProto(SnakesProto.GamePlayers proto,
                                                       InetSocketAddress senderAddress) {
        return proto.getPlayersList().stream()
                .map(p -> fromProto(p, senderAddress))
                .collect(Collectors.toList());
    }

    public static SnakesProto.GamePlayers playersToProto(List<GamePlayer> players, int senderId) {
        SnakesProto.GamePlayers.Builder builder = SnakesProto.GamePlayers.newBuilder();

        for (GamePlayer player : players) {
            boolean includeAddress = (player.getId() != senderId) ||
                    (player.getRole() == NodeRole.MASTER) ||
                    (player.getRole() == NodeRole.DEPUTY);
            builder.addPlayers(toProto(player, includeAddress));
        }

        return builder.build();
    }





    public static Snake fromProto(SnakesProto.GameState.Snake proto) {
        List<Coord> keyPoints = proto.getPointsList().stream()
                .map(ProtoConverter::fromProto)
                .collect(Collectors.toList());

        return new Snake(
                proto.getPlayerId(),
                keyPoints,
                fromProto(proto.getHeadDirection()),
                fromProto(proto.getState())
        );
    }

    public static SnakesProto.GameState.Snake toProto(Snake snake) {
        return SnakesProto.GameState.Snake.newBuilder()
                .setPlayerId(snake.getPlayerId())
                .addAllPoints(coordListToProto(snake.getKeyPoints()))
                .setHeadDirection(toProto(snake.getHeadDirection()))
                .setState(toProto(snake.getState()))
                .build();
    }

    public static List<Snake> snakeListFromProto(List<SnakesProto.GameState.Snake> protoList) {
        return protoList.stream()
                .map(ProtoConverter::fromProto)
                .collect(Collectors.toList());
    }

    public static List<SnakesProto.GameState.Snake> snakeListToProto(List<Snake> snakes) {
        return snakes.stream()
                .map(ProtoConverter::toProto)
                .collect(Collectors.toList());
    }





    public static GameState fromProto(SnakesProto.GameState proto, GameConfig config,
                                      InetSocketAddress senderAddress) {
        return new GameState(
                proto.getStateOrder(),
                snakeListFromProto(proto.getSnakesList()),
                coordListFromProto(proto.getFoodsList()),
                playerListFromProto(proto.getPlayers(), senderAddress),
                config
        );
    }

    public static SnakesProto.GameState toProto(GameState state, int senderId) {
        return SnakesProto.GameState.newBuilder()
                .setStateOrder(state.getStateOrder())
                .addAllSnakes(snakeListToProto(state.getSnakes()))
                .addAllFoods(coordListToProto(state.getFoods()))
                .setPlayers(playersToProto(state.getPlayers(), senderId))
                .build();
    }





    public static GameAnnouncement fromProto(SnakesProto.GameAnnouncement proto,
                                             InetSocketAddress senderAddress) {
        List<GamePlayer> players = playerListFromProto(proto.getPlayers(), senderAddress);

        InetSocketAddress masterAddress = null;
        for (GamePlayer p : players) {
            if (p.getRole() == NodeRole.MASTER && p.getAddress() != null) {
                masterAddress = p.getAddress();
                break;
            }
        }

        if (masterAddress == null) {
            if (senderAddress != null && senderAddress.getPort() != Config.MULTICAST_PORT) {
                masterAddress = senderAddress;
            } else {
                System.err.println("[PROTO] WARNING: No valid MASTER address found!");
            }
        }

        return new GameAnnouncement(
                proto.getGameName(),
                fromProto(proto.getConfig()),
                players,
                proto.getCanJoin(),
                masterAddress
        );
    }

    public static SnakesProto.GameAnnouncement toProto(GameAnnouncement announcement,
                                                       int senderId, boolean canJoin) {
        return SnakesProto.GameAnnouncement.newBuilder()
                .setGameName(announcement.getGameName())
                .setConfig(toProto(announcement.getConfig()))
                .setPlayers(playersToProto(announcement.getPlayers(), senderId))
                .setCanJoin(canJoin)
                .build();
    }






    public static SnakesProto.GameMessage.Builder createPingMsg() {
        return SnakesProto.GameMessage.newBuilder()
                .setPing(SnakesProto.GameMessage.PingMsg.getDefaultInstance());
    }


    public static SnakesProto.GameMessage.Builder createSteerMsg(Direction direction) {
        return SnakesProto.GameMessage.newBuilder()
                .setSteer(SnakesProto.GameMessage.SteerMsg.newBuilder()
                        .setDirection(toProto(direction))
                        .build());
    }


    public static SnakesProto.GameMessage createAckMsg(long msgSeq, int senderId, int receiverId) {
        return SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(msgSeq)
                .setSenderId(senderId)
                .setReceiverId(receiverId)
                .setAck(SnakesProto.GameMessage.AckMsg.getDefaultInstance())
                .build();
    }


    public static SnakesProto.GameMessage.Builder createStateMsg(GameState state, int senderId) {
        return SnakesProto.GameMessage.newBuilder()
                .setState(SnakesProto.GameMessage.StateMsg.newBuilder()
                        .setState(toProto(state, senderId))
                        .build());
    }


    public static SnakesProto.GameMessage createAnnouncementMsg(GameAnnouncement announcement,
                                                                int senderId, boolean canJoin) {
        return SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(0)
                .setAnnouncement(SnakesProto.GameMessage.AnnouncementMsg.newBuilder()
                        .addGames(toProto(announcement, senderId, canJoin))
                        .build())
                .build();
    }


    public static SnakesProto.GameMessage createDiscoverMsg() {
        return SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(0)
                .setDiscover(SnakesProto.GameMessage.DiscoverMsg.getDefaultInstance())
                .build();
    }


    public static SnakesProto.GameMessage.Builder createJoinMsg(String playerName,
                                                                String gameName,
                                                                NodeRole requestedRole,
                                                                PlayerType playerType) {
        return SnakesProto.GameMessage.newBuilder()
                .setJoin(SnakesProto.GameMessage.JoinMsg.newBuilder()
                        .setPlayerName(playerName)
                        .setGameName(gameName)
                        .setRequestedRole(toProto(requestedRole))
                        .setPlayerType(toProto(playerType))
                        .build());
    }



    public static SnakesProto.GameMessage.Builder createErrorMsg(String errorMessage) {
        return SnakesProto.GameMessage.newBuilder()
                .setError(SnakesProto.GameMessage.ErrorMsg.newBuilder()
                        .setErrorMessage(errorMessage)
                        .build());
    }



    public static SnakesProto.GameMessage.Builder createRoleChangeMsg(NodeRole senderRole,
                                                                      NodeRole receiverRole,
                                                                      int senderId,
                                                                      int receiverId) {
        SnakesProto.GameMessage.RoleChangeMsg.Builder rcBuilder =
                SnakesProto.GameMessage.RoleChangeMsg.newBuilder();

        if (senderRole != null) {
            rcBuilder.setSenderRole(toProto(senderRole));
        }
        if (receiverRole != null) {
            rcBuilder.setReceiverRole(toProto(receiverRole));
        }

        return SnakesProto.GameMessage.newBuilder()
                .setSenderId(senderId)
                .setReceiverId(receiverId)
                .setRoleChange(rcBuilder.build());
    }
}
