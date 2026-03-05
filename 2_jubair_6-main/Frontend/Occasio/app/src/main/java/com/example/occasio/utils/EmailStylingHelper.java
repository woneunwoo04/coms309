package com.example.occasio.utils;

/**
 * Helper class to automatically apply pink & cute styling to email content
 */
public class EmailStylingHelper {

   
    public static String applyPinkCuteStyling(String content, String title) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        String contentTrimmed = content.trim();
        String contentLower = contentTrimmed.toLowerCase();
        
        // Check if content already has full HTML structure
        if (contentLower.startsWith("<!doctype") || 
            contentLower.startsWith("<html") ||
            (contentLower.contains("<html") && contentLower.contains("</html>"))) {
            // Already has full HTML structure, return as-is
            return contentTrimmed;
        }
        
        // Check if content contains HTML tags (but not full HTML structure)
        boolean hasHtmlTags = contentTrimmed.contains("<") && contentTrimmed.contains(">");
        
        // If content has HTML tags, preserve them in the message
        // Otherwise, use plain text
        String message;
        if (hasHtmlTags) {
            // Content has HTML tags - preserve them
            message = contentTrimmed;
        } else {
            // Plain text - use as-is
            message = contentTrimmed;
        }
        
        if (message.isEmpty()) {
            message = "You have a new message!";
        }
        
        // No separate details section needed since HTML is preserved in message
        String details = null;
        
        // Use title or default
        String emailTitle = title != null && !title.isEmpty() ? title : "Message from Occasio";
        
        // Apply pink & cute styling
        return createPinkCuteEmail(emailTitle, message, details, null);
    }

    /**
     * Creates a pink & cute styled email template
     */
    private static String createPinkCuteEmail(String title, String message, String details, String footer) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html lang='en'>");
        html.append("<head>");
        html.append("    <meta charset='UTF-8'>");
        html.append("    <meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("</head>");
        html.append("<body style='margin: 0; padding: 0; background-color: #f4f4f4; font-family: Arial, sans-serif;'>");
        html.append("    <div style='max-width: 600px; margin: 20px auto; background: #ffeef5; padding: 30px; border-radius: 15px;'>");
        html.append("        <div style='background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);'>");
            html.append("            <div style='text-align: center; margin-bottom: 30px;'>");
            html.append("                <h1 style='color: #ff69b4; margin: 0; font-size: 40px;'>💕</h1>");
            html.append("                <h2 style='color: #ff69b4; margin: 10px 0; font-size: 28px;'>").append(escapeHtml(title)).append("</h2>");
            html.append("            </div>");
            html.append("            <div style='color: #555; font-size: 16px; line-height: 1.8;'>").append(message).append("</div>");
        
        if (details != null && !details.trim().isEmpty()) {
            html.append("            <div style='background: #fff0f5; padding: 20px; border-radius: 8px; margin: 25px 0; border-left: 4px solid #ff69b4;'>");
            html.append("                <p style='margin: 0; color: #ff69b4; font-weight: bold; font-size: 14px;'>🌸 Details:</p>");
            html.append("                <div style='margin: 10px 0 0 0; color: #666; font-size: 14px;'>").append(details).append("</div>");
            html.append("            </div>");
        }
        
        if (footer != null && !footer.trim().isEmpty()) {
            html.append("            <p style='color: #999; font-size: 14px; text-align: center; margin-top: 30px;'>").append(escapeHtml(footer)).append("</p>");
        }
        
        html.append("        </div>");
        html.append("        <p style='text-align: center; color: #999; font-size: 12px; margin-top: 20px;'>Made with 💖 by Occasio</p>");
        html.append("    </div>");
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }

    /**
     * Escapes HTML special characters
     */
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}

