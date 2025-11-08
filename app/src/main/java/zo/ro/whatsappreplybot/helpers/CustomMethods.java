package zo.ro.whatsappreplybot.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CustomMethods {

    public static String getCurrentDateTime(){
        Calendar calendar = Calendar.getInstance();
        DateFormat usDateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.US);
        return usDateFormat.format(calendar.getTime());
    }

//    ----------------------------------------------------------------------------------------------

    /**
     * Processes a prompt template by automatically appending chat history and message
     * No placeholders needed - users just write the behavior prompt
     */
    public static String processPromptTemplate(String template, String language, String botName, 
                                                String sender, String message, String chatHistory) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        // Automatically append chat history and message at the end
        StringBuilder finalPrompt = new StringBuilder(template);
        
        // Add chat history if available
        if (chatHistory != null && !chatHistory.trim().isEmpty()) {
            finalPrompt.append("\n\nPrevious chat history:\n").append(chatHistory);
        } else {
            finalPrompt.append("\n\nThere is no previous chat history. This is the first message from the sender.");
        }
        
        // Add current message
        if (sender != null && message != null) {
            finalPrompt.append("\n\nMost recent message (from ").append(sender).append("): ").append(message);
        }
        
        return finalPrompt.toString();
    }

//    ----------------------------------------------------------------------------------------------


}
