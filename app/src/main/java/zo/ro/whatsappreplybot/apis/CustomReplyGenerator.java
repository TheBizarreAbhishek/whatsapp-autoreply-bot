package zo.ro.whatsappreplybot.apis;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
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

public class CustomReplyGenerator {

    private final String API_URL;
    private static final String TAG = "MADARA";
    private final String API_KEY;
    private final String LLM_MODEL;
    private final WhatsAppMessageHandler messageHandler;
    private final String defaultReplyMessage;
    private final String aiReplyLanguage;
    private final String responseKey;
    private final String requestFormat;
    private final String customPrompt;
    private final String botName;
    private final Context context;

    public CustomReplyGenerator(Context context, SharedPreferences sharedPreferences, WhatsAppMessageHandler whatsAppMessageHandler) {
        this.context = context;
        this.messageHandler = whatsAppMessageHandler;
        API_KEY = sharedPreferences.getString("api_key", "").trim();
        LLM_MODEL = sharedPreferences.getString("llm_model", "custom-gpt-4o");
        defaultReplyMessage = sharedPreferences.getString("default_reply_message", context.getString(R.string.default_bot_message));
        aiReplyLanguage = sharedPreferences.getString("ai_reply_language", "English");
        botName = sharedPreferences.getString("bot_name", "Yuji");
        customPrompt = sharedPreferences.getString("custom_ai_prompt", "").trim();
        // Get custom API URL from settings, fallback to default if not set
        String customUrl = sharedPreferences.getString("custom_api_url", context.getString(R.string.default_custom_api_url)).trim();
        API_URL = customUrl.isEmpty() ? context.getString(R.string.default_custom_api_url) : customUrl;
        // Get response key from settings, default to "response"
        responseKey = sharedPreferences.getString("custom_api_response_key", context.getString(R.string.default_response_key)).trim();
        // Get request format (json or form), default to "form"
        requestFormat = sharedPreferences.getString("custom_api_request_format", "form").toLowerCase();
    }

    public void generateReply(String sender, String message, CustomReplyGenerator.OnReplyGeneratedListener listener) {

        new Thread(() -> messageHandler.getMessagesHistory(sender, messages -> {

            StringBuilder chatHistory = getChatHistory(messages);
            StringBuilder prompt = buildPrompt(sender, message, chatHistory);


            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)  // Set connect timeout
                    .readTimeout(30, TimeUnit.SECONDS)     // Set read timeout
                    .writeTimeout(30, TimeUnit.SECONDS)    // Set write timeout
                    .build();

            RequestBody requestBody;
            Request.Builder requestBuilder = new Request.Builder().url(API_URL);

            // Support both JSON and Form data formats
            if ("json".equals(requestFormat)) {
                try {
                    JSONObject jsonBody = new JSONObject();
                    jsonBody.put("prompt", prompt.toString());
                    jsonBody.put("model", LLM_MODEL);
                    
                    MediaType JSON = MediaType.get("application/json; charset=utf-8");
                    requestBody = RequestBody.create(jsonBody.toString(), JSON);
                    requestBuilder.addHeader("Content-Type", "application/json");
                } catch (Exception e) {
                    Log.e(TAG, "Error creating JSON request body: ", e);
                    // Fallback to form data if JSON creation fails
                    requestBody = new FormBody.Builder()
                            .add("prompt", prompt.toString())
                            .add("model", LLM_MODEL)
                            .build();
                }
            } else {
                // Default to form data
                requestBody = new FormBody.Builder()
                        .add("prompt", prompt.toString())
                        .add("model", LLM_MODEL)
                        .build();
            }

            // Add Authorization header if API key is provided
            if (!API_KEY.isEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer " + API_KEY);
            }

            Request request = requestBuilder.post(requestBody).build();

            try {
                // Execute the request
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
                            String aiReply = parseResponse(responseData);

                            if (aiReply != null) {
                                listener.onReplyGenerated(aiReply);
                            } else {
                                Log.d(TAG, "onResponse: ai reply is null");
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
        })).start();
    }

//    ----------------------------------------------------------------------------------------------

    private @NonNull StringBuilder buildPrompt(String sender, String message, StringBuilder chatHistory) {

        // Use custom prompt if provided
        if (!customPrompt.isEmpty()) {
            String processedPrompt = CustomMethods.processPromptTemplate(
                customPrompt, aiReplyLanguage, botName, sender, message, chatHistory.toString()
            );
            return new StringBuilder(processedPrompt);
        }

        // Default behavior
        StringBuilder prompt = new StringBuilder();

        if (chatHistory.toString().isEmpty()) {
            prompt.append("You are a WhatsApp auto-reply bot. Your task is to reply to the incoming message. Response only the chat and do not add any other text. ");
            prompt.append("Always respond in ").append(aiReplyLanguage).append(". Be polite, context-aware, and ensure your replies are relevant to the conversation.");
            prompt.append("\n\n\nMost recent message (from ");
            prompt.append(sender).append("): ");
            prompt.append(message);
            return prompt;
        }

        prompt.append("You are a WhatsApp auto-reply bot. Your task is to read the provided previous chat history and reply to the most recent incoming message. ");
        prompt.append("Always respond in ").append(aiReplyLanguage).append(". Be polite, context-aware, and ensure your replies are relevant to the conversation.\n\n");
        prompt.append("Previous chat history: \n").append(chatHistory);
        prompt.append("\n\n\nMost recent message (from ");
        prompt.append(sender).append("): ");
        prompt.append(message);
        return prompt;
    }

//    ----------------------------------------------------------------------------------------------

    private @NonNull StringBuilder getChatHistory(List<Message> messages) {

        StringBuilder chatHistory = new StringBuilder();

        if (!messages.isEmpty()) {

            for (Message msg : messages) {

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
            // Use the user-configured response key, fallback to "response" if key doesn't exist
            String key = responseKey.isEmpty() ? "response" : responseKey;
            
            if (jsonObject.has(key)) {
                String reply = jsonObject.getString(key);
                if (!reply.isEmpty()) {
                    return reply;
                }
            } else {
                // Try common response keys if the configured key doesn't exist
                String[] commonKeys = {"response", "reply", "message", "text", "content", "answer"};
                for (String commonKey : commonKeys) {
                    if (jsonObject.has(commonKey)) {
                        String reply = jsonObject.getString(commonKey);
                        if (!reply.isEmpty()) {
                            Log.d(TAG, "parseResponse: Using fallback key: " + commonKey);
                            return reply;
                        }
                    }
                }
                Log.d(TAG, "parseResponse: Response key '" + key + "' not found in response");
            }
        } catch (Exception e) {
            Log.d(TAG, "parseResponse: " + e.getMessage());
        }
        return null;
    }

//    ----------------------------------------------------------------------------------------------

    public interface OnReplyGeneratedListener {
        void onReplyGenerated(String reply);
    }
}
