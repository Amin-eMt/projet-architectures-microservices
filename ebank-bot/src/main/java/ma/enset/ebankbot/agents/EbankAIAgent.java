package ma.enset.ebankbot.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Service
public class EbankAIAgent {

    private final ChatClient chatClient;

    public EbankAIAgent(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            ToolCallbackProvider tools) {

        this.chatClient = builder
                .defaultSystem("""
                        Vous êtes un assistant bancaire intelligent.
                        Vous répondez aux questions des utilisateurs
                        concernant les clients et les comptes bancaires,
                        en fonction du contexte fourni.

                        Si aucun contexte n'est fourni,
                        répondez : JE NE SAIS PAS.
                        """)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultTools(tools)
                .build();
    }

    // Handle text messages
    public String chat(String conversationId, String query) {

        return chatClient.prompt()
                .user(query)
                .advisors(a -> a.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();
    }

    // Handle image messages
    public String chatWithImage(
            String conversationId,
            String query,
            byte[] imageBytes) {

        return chatClient.prompt()
                .user(user -> user
                        .text(query)
                        .media(
                                MimeTypeUtils.IMAGE_JPEG,
                                new ByteArrayResource(imageBytes)
                        )
                )
                .advisors(a -> a.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();
    }

    // Handle Prompt objects
    public String chat(Prompt prompt) {

        return chatClient.prompt(prompt)
                .call()
                .content();
    }
}
