package zo.ro.whatsappreplybot.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

import zo.ro.whatsappreplybot.R;
import zo.ro.whatsappreplybot.apis.ChatGPTReplyGenerator;
import zo.ro.whatsappreplybot.apis.CustomReplyGenerator;
import zo.ro.whatsappreplybot.apis.DeepSeekReplyGenerator;
import zo.ro.whatsappreplybot.apis.GeminiReplyGenerator;
import zo.ro.whatsappreplybot.apis.OllamaReplyGenerator;
import zo.ro.whatsappreplybot.helpers.WhatsAppMessageHandler;

public class MyNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "MADARA";
    private final String notificationChannelId = "wa_auto_reply_channel";
    private final Set<String> respondedMessages = new HashSet<>();
    private SharedPreferences sharedPreferences;
    private WhatsAppMessageHandler messageHandler;
    private String botReplyMessage;

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        super.onNotificationPosted(statusBarNotification);

        if (statusBarNotification.getPackageName().equalsIgnoreCase("com.whatsapp")) {

            Bundle extras = statusBarNotification.getNotification().extras;
            String messageId = statusBarNotification.getKey();
            String title = extras.getString(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);

            // Check if we've already responded to this message
            if (respondedMessages.contains(messageId)) {
                return;
            }

            // Skip if message is from the user themselves
            if (isMessageFromUser(text)) {
                Log.d(TAG, "Skipping reply: Message is from user");
                return;
            }

            // Skip if notification is for an active/foreground chat
            if (isChatActive(statusBarNotification)) {
                Log.d(TAG, "Skipping reply: Chat is currently active");
                return;
            }

            // Check if notification has reply action - if not, we can't reply anyway
            Notification.Action[] actions = statusBarNotification.getNotification().actions;
            if (actions == null || actions.length == 0) {
                Log.d(TAG, "Skipping reply: No reply actions available");
                return;
            }

            // Check if any action has remote input (reply capability)
            boolean hasReplyAction = false;
            for (Notification.Action action : actions) {
                if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                    hasReplyAction = true;
                    break;
                }
            }
            
            if (!hasReplyAction) {
                Log.d(TAG, "Skipping reply: No reply action found in notification");
                return;
            }

            // Add this message to the set of responded messages to avoid looping
            respondedMessages.add(messageId);

            // Process the message and send auto-reply
            if (text != null && !text.toString().isEmpty()) {

                String senderMessage = text.toString();

                if (sharedPreferences.getBoolean("is_bot_enabled", true)) {

                    int maxReply = Integer.parseInt(sharedPreferences.getString("max_reply", "100"));

                    messageHandler.getAllMessagesBySender(title, messages -> {

                        if (messages != null && messages.size() < maxReply) {

                            boolean groupReplyEnabled = sharedPreferences.getBoolean("is_group_reply_enabled", false);

                            if (groupReplyEnabled) {
                                processAutoReply(statusBarNotification, title, senderMessage, messageId);
                            } else {
                                if (!isGroupMessage(title)) {
                                    processAutoReply(statusBarNotification, title, senderMessage, messageId);
                                }
                            }
                        }
                    });
                }
            }

            // Clear the set if it reaches size 50 for ram memory free // but no necessary currently
            if (respondedMessages.size() > 50) {
                respondedMessages.clear();
            }
        }
    }

//    ----------------------------------------------------------------------------------------------

    private void processAutoReply(StatusBarNotification statusBarNotification, String sender, String message, String messageId) {

        Notification.Action[] actions = statusBarNotification.getNotification().actions;

        if (actions != null) {

            for (Notification.Action action : actions) {

                // Here is validating sender's message. Not whatsapp checking for messages
                if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {

                    //..............................................................................

                    String replyPrefix = sharedPreferences.getString("reply_prefix_message", getString(R.string.default_reply_prefix)).trim();

                    if (isAIConfigured()) {

                        String llmModel = sharedPreferences.getString("llm_model", "gpt-4o-mini").toLowerCase();

                        if (llmModel.startsWith("gpt")) {

                            ChatGPTReplyGenerator chatGPTReplyGenerator = new ChatGPTReplyGenerator(this, sharedPreferences, messageHandler);

                            chatGPTReplyGenerator.generateReply(sender, message, reply -> {
                                botReplyMessage = replyPrefix + " " + reply;
                                String botReplyWithoutPrefix = botReplyMessage.replace(replyPrefix, "").trim();
                                messageHandler.handleIncomingMessage(sender, message, botReplyWithoutPrefix);
                                sendWithNaturalDelay(action, botReplyMessage, messageId);
                            });

                        } else if (llmModel.startsWith("custom")) {

                            CustomReplyGenerator customReplyGenerator = new CustomReplyGenerator(this, sharedPreferences, messageHandler);

                            customReplyGenerator.generateReply(sender, message, reply -> {
                                botReplyMessage = replyPrefix + " " + reply;
                                String botReplyWithoutPrefix = botReplyMessage.replace(replyPrefix, "").trim();
                                messageHandler.handleIncomingMessage(sender, message, botReplyWithoutPrefix);
                                sendWithNaturalDelay(action, botReplyMessage, messageId);
                            });

                        } else if (llmModel.startsWith("gemini")) {

                            GeminiReplyGenerator geminiReplyGenerator = new GeminiReplyGenerator(this, sharedPreferences, messageHandler);

                            geminiReplyGenerator.generateReply(sender, message, reply -> {
                                botReplyMessage = replyPrefix + " " + reply;
                                String botReplyWithoutPrefix = botReplyMessage.replace(replyPrefix, "").trim();
                                messageHandler.handleIncomingMessage(sender, message, botReplyWithoutPrefix);
                                sendWithNaturalDelay(action, botReplyMessage, messageId);
                            });

                        } else if (llmModel.startsWith("deepseek")) {

                            DeepSeekReplyGenerator deepSeekReplyGenerator = new DeepSeekReplyGenerator(this, sharedPreferences, messageHandler);

                            deepSeekReplyGenerator.generateReply(sender, message, reply -> {
                                botReplyMessage = replyPrefix + " " + reply;
                                String botReplyWithoutPrefix = botReplyMessage.replace(replyPrefix, "").trim();
                                messageHandler.handleIncomingMessage(sender, message, botReplyWithoutPrefix);
                                sendWithNaturalDelay(action, botReplyMessage, messageId);
                            });

                        } else if (llmModel.startsWith("llama") || llmModel.startsWith("mistral") || 
                                   llmModel.startsWith("codellama") || llmModel.startsWith("phi")) {

                            OllamaReplyGenerator ollamaReplyGenerator = new OllamaReplyGenerator(this, sharedPreferences, messageHandler);

                            ollamaReplyGenerator.generateReply(sender, message, reply -> {
                                botReplyMessage = replyPrefix + " " + reply;
                                String botReplyWithoutPrefix = botReplyMessage.replace(replyPrefix, "").trim();
                                messageHandler.handleIncomingMessage(sender, message, botReplyWithoutPrefix);
                                sendWithNaturalDelay(action, botReplyMessage, messageId);
                            });
                        }

                    } else {
                        botReplyMessage = (replyPrefix + " " + sharedPreferences.getString("default_reply_message", getString(R.string.default_bot_message))).trim();
                        String botReplyWithoutPrefix = botReplyMessage.replace(replyPrefix, "").trim();
                        messageHandler.handleIncomingMessage(sender, message, botReplyWithoutPrefix);
                        sendWithNaturalDelay(action, botReplyMessage, messageId);
                    }

                    //..............................................................................

                    break;
                }
            }
        }
    }

//    ----------------------------------------------------------------------------------------------

    /**
     * Calculates delay in milliseconds based on reply text length to make it feel more natural
     * Short messages (1-20 chars): 1-2 seconds
     * Medium messages (21-100 chars): 2-4 seconds
     * Long messages (100+ chars): 4-8 seconds
     */
    private long calculateNaturalDelay(String replyText) {
        if (replyText == null || replyText.isEmpty()) {
            return 1000; // Default 1 second for empty messages
        }

        int length = replyText.length();
        long delay;

        if (length <= 20) {
            // Short messages: 1-2 seconds (base 1000ms + up to 1000ms)
            delay = 1000 + (length * 50); // 1s + 50ms per character
        } else if (length <= 100) {
            // Medium messages: 2-4 seconds
            delay = 2000 + ((length - 20) * 25); // 2s base + 25ms per extra character
        } else {
            // Long messages: 4-8 seconds (capped at 8 seconds)
            delay = 4000 + ((length - 100) * 40); // 4s base + 40ms per extra character
            if (delay > 8000) {
                delay = 8000; // Cap at 8 seconds
            }
        }

        // Ensure minimum delay of 1 second
        return Math.max(delay, 1000);
    }

//    ----------------------------------------------------------------------------------------------

    private void send(Notification.Action action, String botReplyMessage) {

        RemoteInput remoteInput = action.getRemoteInputs()[0];

        Intent intent = new Intent();

        Bundle bundle = new Bundle();
        bundle.putCharSequence(remoteInput.getResultKey(), botReplyMessage);

        RemoteInput.addResultsToIntent(new RemoteInput[]{remoteInput}, intent, bundle);

        try {
            action.actionIntent.send(this, 0, intent);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "sendAutoReply: ", e);
        }
    }

//    ----------------------------------------------------------------------------------------------

    /**
     * Sends reply with delay (custom delay takes priority, then natural delay, then instant)
     * Removes messageId from respondedMessages after sending to prevent duplicates
     */
    private void sendWithNaturalDelay(Notification.Action action, String botReplyMessage, String messageId) {
        long delay = 0;
        boolean customDelaySet = false;
        
        // Check for custom delay first (takes priority)
        String customDelayStr = sharedPreferences.getString("custom_delay_seconds", "").trim();
        if (!customDelayStr.isEmpty()) {
            try {
                double customDelaySeconds = Double.parseDouble(customDelayStr);
                customDelaySet = true; // Custom delay field is set (even if 0)
                if (customDelaySeconds > 0) {
                    delay = (long) (customDelaySeconds * 1000); // Convert seconds to milliseconds
                }
                // If custom delay is 0, delay stays 0 and we won't check natural delay
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid custom delay value: " + customDelayStr, e);
            }
        }
        
        // If custom delay is NOT set (empty), check for natural delay
        // If custom delay is set to 0, we skip natural delay (user wants instant)
        if (!customDelaySet) {
            boolean isNaturalDelayEnabled = sharedPreferences.getBoolean("is_natural_delay_enabled", true);
            if (isNaturalDelayEnabled) {
                delay = calculateNaturalDelay(botReplyMessage);
            }
        }
        
        // Send with calculated delay (or instantly if delay is 0)
        if (delay > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                send(action, botReplyMessage);
                // Remove messageId after sending (wait additional 2 seconds to prevent duplicates from notification updates)
                new Handler(Looper.getMainLooper()).postDelayed(() -> respondedMessages.remove(messageId), 2000);
            }, delay);
        } else {
            // Send instantly if no delay
            send(action, botReplyMessage);
            // Remove messageId after a short delay to prevent immediate duplicates
            new Handler(Looper.getMainLooper()).postDelayed(() -> respondedMessages.remove(messageId), 2000);
        }
    }

//    ----------------------------------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();

        messageHandler = new WhatsAppMessageHandler(this);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, notificationChannelId)
                .setSmallIcon(R.drawable.notifications_24)
                .setContentTitle("Auto-Reply Active")
                .setContentText("WhatsApp auto-reply is running")
                .setPriority(NotificationCompat.PRIORITY_LOW);

        startForeground(1, builder.build());
    }

//    ----------------------------------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    notificationChannelId,
                    "Auto Reply Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Channel for Auto Reply Service");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

//    ----------------------------------------------------------------------------------------------

    private boolean isGroupMessage(String title) {
        return title != null && title.contains(":");
    }

//    ----------------------------------------------------------------------------------------------

    /**
     * Checks if the chat is currently active/foreground
     * When WhatsApp chat is open, notifications might still be posted but shouldn't trigger auto-reply
     */
    private boolean isChatActive(StatusBarNotification statusBarNotification) {
        Notification notification = statusBarNotification.getNotification();
        
        if (notification != null) {
            Bundle extras = notification.extras;
            if (extras != null) {
                CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
                String title = extras.getString(Notification.EXTRA_TITLE);
                
                // If title and text are the same, it might be a status update or system message
                if (title != null && text != null && title.equals(text.toString())) {
                    return true;
                }
                
                // Check if notification is silent (no sound/vibration) - might indicate active chat
                // When chat is open, WhatsApp might post silent notifications
                if ((notification.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0) {
                    // This flag is set when notification should only alert once
                    // Combined with other checks, this might indicate active chat
                }
            }
            
            // Check if notification is marked as ongoing (less likely to be a new message)
            if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
                return true;
            }
        }
        
        return false;
    }

//    ----------------------------------------------------------------------------------------------

    /**
     * Checks if the message is from the user themselves
     * WhatsApp notifications for user's own messages often have specific patterns
     */
    private boolean isMessageFromUser(CharSequence text) {
        if (text == null || text.toString().isEmpty()) {
            return false;
        }
        
        String messageText = text.toString().toLowerCase();
        
        // Check for common patterns indicating user's own messages
        // WhatsApp might show "You:" prefix or similar indicators
        if (messageText.startsWith("you:") || 
            messageText.startsWith("you ") ||
            messageText.contains("you sent") ||
            messageText.contains("your message")) {
            return true;
        }
        
        // Also check for status update patterns
        if (messageText.contains("read") || 
            messageText.contains("delivered") ||
            messageText.contains("typing...") ||
            messageText.contains("online") ||
            messageText.contains("last seen")) {
            return true;
        }
        
        return false;
    }

//    ----------------------------------------------------------------------------------------------

    private boolean isAIConfigured() {
        boolean isAIConfigured = false;
        if (sharedPreferences.getBoolean("is_ai_reply_enabled", false)) {
            if (!sharedPreferences.getString("api_key", "").isEmpty()) {
                isAIConfigured = true;
            }
        }
        return isAIConfigured;
    }

//    ----------------------------------------------------------------------------------------------
}