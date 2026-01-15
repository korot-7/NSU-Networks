package lab4.snake.view;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import lab4.snake.model.NodeRole;
import lab4.snake.model.GamePlayer;

import java.util.Comparator;
import java.util.List;

public class ScoreboardView {
    private final VBox container;

    public ScoreboardView(VBox container) {
        this.container = container;
        container.setSpacing(5);
        container.setPadding(new Insets(10));
    }

    public void update(List<GamePlayer> players, int myPlayerId) {
        container.getChildren().clear();

        Label title = new Label("Игроки");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.WHITE);
        container.getChildren().add(title);

        List<GamePlayer> activePlayers = players.stream()
                .filter(p -> p.getRole() != NodeRole.VIEWER)
                .sorted(Comparator.comparingInt(GamePlayer::getScore).reversed())
                .toList();

        for (int i = 0; i < activePlayers.size(); i++) {
            GamePlayer player = activePlayers.get(i);
            Label label = createPlayerLabel(player, i + 1, player.getId() == myPlayerId);
            container.getChildren().add(label);
        }
    }

    private Label createPlayerLabel(GamePlayer player, int rank, boolean isMe) {
        String roleMarker = getRoleMarker(player.getRole());
        String meMarker = isMe ? " (вы)" : "";
        String text = String.format("%d. %s%s: %d%s",
                rank, roleMarker, player.getName(), player.getScore(), meMarker);

        Label label = new Label(text);

        Color textColor;
        FontWeight weight = FontWeight.NORMAL;

        if (isMe) {
            textColor = Color.LIGHTGREEN;
            weight = FontWeight.BOLD;
        } else {
            textColor = switch (player.getRole()) {
                case MASTER -> Color.GOLD;
                case DEPUTY -> Color.LIGHTSKYBLUE;
                default -> Color.WHITE;
            };
        }

        label.setFont(Font.font("System", weight, 14));
        label.setTextFill(textColor);

        return label;
    }

    private String getRoleMarker(NodeRole role) {
        return switch (role) {
            case MASTER -> "★ ";
            case DEPUTY -> "☆ ";
            case NORMAL, VIEWER -> "";
        };
    }
}
