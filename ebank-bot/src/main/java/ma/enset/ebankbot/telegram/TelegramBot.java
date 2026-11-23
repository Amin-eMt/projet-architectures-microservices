package ma.enset.ebankbot.telegram;

import jakarta.annotation.PostConstruct;
import ma.enset.ebankbot.agents.EbankAIAgent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final EbankAIAgent aiAgent;
    private final String botUsername;

    public TelegramBot(
            EbankAIAgent aiAgent,
            @Value("${telegram.token}") String botToken,
            @Value("${telegram.username}") String botUsername) {

        super(botToken);
        this.aiAgent = aiAgent;
        this.botUsername = botUsername;
    }

    @PostConstruct
    public void registerTelegramBot() {
        try {
            TelegramBotsApi api =
                    new TelegramBotsApi(DefaultBotSession.class);

            api.registerBot(this);

            System.out.println("Telegram Bot registered successfully!");

        } catch (TelegramApiException e) {
            throw new IllegalStateException(
                    "Unable to register Telegram Bot", e);
        }
    }

    @Override
    public String getBotUsername() {
        return "Ebank11Bot";
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update == null || !update.hasMessage()) {
            return;
        }

        var message = update.getMessage();

        Long chatId = message.getChatId();

        // Ignore messages sent by bots
        if (message.getFrom() != null
                && Boolean.TRUE.equals(message.getFrom().getIsBot())) {
            return;
        }

        String conversationId = "telegram-" + chatId;

        try {

            // Handle text messages
            if (message.hasText()) {

                String query = message.getText();

                String response = aiAgent.chat(
                        conversationId,
                        query
                );

                sendMessage(chatId, response);
                return;
            }

            // Handle image messages
            if (message.hasPhoto()) {

                List<PhotoSize> photos = message.getPhoto();

                // Get the largest available photo
                PhotoSize photo = photos.stream()
                        .max(Comparator.comparing(PhotoSize::getFileSize))
                        .orElseThrow();

                String caption = message.getCaption();

                if (caption == null || caption.isBlank()) {
                    caption = "Describe this image in detail.";
                }

                // Download image from Telegram
                GetFile getFile = new GetFile();
                getFile.setFileId(photo.getFileId());

                org.telegram.telegrambots.meta.api.objects.File telegramFile =
                        execute(getFile);

                File downloadedFile =
                        downloadFile(telegramFile.getFilePath());

                byte[] imageBytes =
                        Files.readAllBytes(downloadedFile.toPath());

                // Send image to AI Agent
                String response = aiAgent.chatWithImage(
                        conversationId,
                        caption,
                        imageBytes
                );

                sendMessage(chatId, response);
                return;
            }

            sendMessage(chatId,
                    "Please send a text message or an image.");

        } catch (TelegramApiException | IOException e) {

            e.printStackTrace();

            sendMessage(chatId,
                    "Sorry, an error occurred while processing your message.");

        } catch (Exception e) {

            e.printStackTrace();

            sendMessage(chatId,
                    "Sorry, the AI service is temporarily unavailable.");
        }
    }

    private void sendMessage(Long chatId, String text) {

        if (text == null || text.isBlank()) {
            text = "Sorry, I couldn't generate a response.";
        }

        // Telegram text messages have a 4096-character limit
        for (int i = 0; i < text.length(); i += 4000) {

            String part = text.substring(
                    i,
                    Math.min(i + 4000, text.length())
            );

            SendMessage message = new SendMessage();

            message.setChatId(chatId.toString());
            message.setText(part);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }
}
