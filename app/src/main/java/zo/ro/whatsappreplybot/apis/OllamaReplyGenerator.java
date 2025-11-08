package zo.ro.whatsappreplybot.apis;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import zo.ro.whatsappreplybot.R;
import zo.ro.whatsappreplybot.helpers.CustomMethods;
import zo.ro.whatsappreplybot.helpers.WhatsAppMessageHandler;
import zo.ro.whatsappreplybot.models.Message;

public class OllamaReplyGenerator {

    private static final String TAG = "MADARA";
    private final String API_URL;
    private final String LLM_MODEL;
    private final WhatsAppMessageHandler messageHandler;
    private List<Message> messagesList;
    private final String defaultReplyMessage;
    private final String aiReplyLanguage;
    private final String customPrompt;
    private final String botName;
    private final Context context;

    public OllamaReplyGenerator(Context context, SharedPreferences sharedPreferences, WhatsAppMessageHandler whatsAppMessageHandler) {
        this.context = context;
        this.messageHandler = whatsAppMessageHandler;
        // Ollama runs locally, default to localhost:11434
        String ollamaUrl = sharedPreferences.getString("ollama_api_url", context.getString(R.string.default_ollama_url)).trim();
        API_URL = ollamaUrl.isEmpty() ? context.getString(R.string.default_ollama_url) : ollamaUrl;
        LLM_MODEL = sharedPreferences.getString("llm_model", "llama2");
        defaultReplyMessage = sharedPreferences.getString("default_reply_message", context.getString(R.string.default_bot_message));
        aiReplyLanguage = sharedPreferences.getString("ai_reply_language", "English");
        botName = sharedPreferences.getString("bot_name", "Yuji");
        customPrompt = sharedPreferences.getString("custom_ai_prompt", "").trim();
    }

    public void generateReply(String sender, String message, OnReplyGeneratedListener listener) {

        new Thread(() -> {

            JSONObject container = new JSONObject();
            JSONArray httpRequestMessages = new JSONArray();

            JSONObject systemRole = new JSONObject();
            JSONObject userRole1 = new JSONObject();
            JSONObject userRole2 = new JSONObject();

            messageHandler.getMessagesHistory(sender, messages -> {

                messagesList = messages;

                StringBuilder chatHistory = getChatHistory();

                try {
                    // Use custom prompt if provided, otherwise use default
                    if (!customPrompt.isEmpty()) {
                        // Process custom prompt template
                        String processedPrompt = CustomMethods.processPromptTemplate(
                            customPrompt, aiReplyLanguage, botName, sender, message, chatHistory.toString()
                        );
                        
                        // For custom prompt, use it as system message and combine everything
                        systemRole.put("role", "system");
                        systemRole.put("content", processedPrompt);
                        
                        httpRequestMessages.put(systemRole);
                    } else {
                        // Default behavior
                        systemRole.put("role", "system");
                        systemRole.put(
                                "content", "You are a WhatsApp auto-reply bot. " +
                                        "Your task is to read the provided previous chat history and reply to the most recent incoming message. " +
                                        "Always respond in " + aiReplyLanguage + ". Be polite, context-aware, and ensure your replies are relevant to the conversation."
                        );

                        userRole1.put("role", "user");

                        if (chatHistory.toString().isEmpty()) {
                            userRole1.put("content", "There are no any previous chat history. This is the first message from the sender.");
                        } else {
                            userRole1.put("content", "Previous chat history: " + chatHistory);
                        }

                        userRole2.put("role", "user");
                        userRole2.put("content", "Most recent message from the sender (" + sender + "): " + message);

                        httpRequestMessages.put(systemRole);
                        httpRequestMessages.put(userRole1);
                        httpRequestMessages.put(userRole2);
                    }

                    container.put("model", LLM_MODEL);
                    container.put("messages", httpRequestMessages);
                    container.put("stream", false); // Ollama supports streaming, but we use non-streaming

                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(60, TimeUnit.SECONDS)  // Longer timeout for local Ollama
                            .readTimeout(60, TimeUnit.SECONDS)     // Longer timeout for local Ollama
                            .writeTimeout(60, TimeUnit.SECONDS)    // Longer timeout for local Ollama
                            .build();

                    MediaType JSON = MediaType.get("application/json; charset=utf-8");

                    String jsonBody = container.toString();

                    RequestBody requestBody = RequestBody.create(jsonBody, JSON);

                    Request request = new Request.Builder()
                            .url(API_URL)
                            .addHeader("Content-Type", "application/json")
                            .post(requestBody)
                            .build();

                    client.newCall(request).enqueue(new Callback() {

                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Log.e(TAG, "onFailure: ", e);
                            listener.onReplyGenerated(defaultReplyMessage);
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                            if (!response.isSuccessful()) {
                                listener.onReplyGenerated(defaultReplyMessage);
                                Log.d(TAG, "onResponse: " + response.code());
                                return;
                            }

                            ResponseBody body = response.body();

                            if (body != null) {
                                String responseData = body.string();
                                String ollamaReply = parseResponse(responseData);

                                if (ollamaReply != null) {
                                    listener.onReplyGenerated(ollamaReply);
                                } else {
                                    Log.d(TAG, "onResponse: ollamaReply is null");
                                    listener.onReplyGenerated(defaultReplyMessage);
                                    Log.d(TAG, "onResponse: " + responseData);
                                }
                            } else {
                                Log.e(TAG, "onResponse: Response body is null");
                                listener.onReplyGenerated(defaultReplyMessage);
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "generateReply: ", e);
                    listener.onReplyGenerated(defaultReplyMessage);
                }
            });
        }).start();
    }

//    ----------------------------------------------------------------------------------------------

    private @NonNull StringBuilder getChatHistory() {
        StringBuilder chatHistory = new StringBuilder();

        Log.d(TAG, "getChatHistory: " + messagesList.size());

        if (messagesList != null && !messagesList.isEmpty()) {

            for (Message msg : messagesList) {

                String senderName = msg.getSender();
                String senderMessage = msg.getMessage();
                String senderMessageTimestamp = msg.getTimestamp();
                String myReplyToSenderMessage = msg.getReply();

                chatHistory.append(senderName).append(": ").append(senderMessage);
                chatHistory.append("\n");
                chatHistory.append("Time: ").append(senderMessageTimestamp);
                chatHistory.append("\n");
                chatHistory.append("My reply: ").append(myReplyToSenderMessage);
                chatHistory.append("\n\n");
            }
        }
        return chatHistory;
    }

//    ----------------------------------------------------------------------------------------------

    private String parseResponse(String responseData) {
        try {
            JSONObject jsonObject = new JSONObject(responseData);
            JSONObject message = jsonObject.getJSONObject("message");
            return message.getString("content");
        } catch (Exception e) {
            Log.e(TAG, "parseResponse: ", e);
        }
        return null;
    }

//    ----------------------------------------------------------------------------------------------

    public interface OnReplyGeneratedListener {
        void onReplyGenerated(String reply);
    }
}

