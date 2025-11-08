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
     * Processes a prompt template by replacing placeholders with actual values
     * Supported placeholders: {language}, {botName}, {sender}, {message}, {chatHistory}
     */
    public static String processPromptTemplate(String template, String language, String botName, 
                                                String sender, String message, String chatHistory) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        String processed = template;
        
        // Replace placeholders
        processed = processed.replace("{language}", language != null ? language : "English");
        processed = processed.replace("{botName}", botName != null ? botName : "Bot");
        processed = processed.replace("{sender}", sender != null ? sender : "");
        processed = processed.replace("{message}", message != null ? message : "");
        processed = processed.replace("{chatHistory}", chatHistory != null ? chatHistory : "");
        
        // Replace \n with actual newlines
        processed = processed.replace("\\n", "\n");
        
        return processed;
    }

//    ----------------------------------------------------------------------------------------------


}
