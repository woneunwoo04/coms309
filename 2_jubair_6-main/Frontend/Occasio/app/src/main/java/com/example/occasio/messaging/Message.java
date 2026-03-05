package com.example.occasio.messaging;

import java.util.HashMap;
import java.util.Map;

public class Message {
    private Long messageId;
    private Long attachmentId;
    private String text;
    private String sender;
    private long timestamp;
    private boolean isSentByUser;
    private String attachmentPath;
    private String attachmentType;
    private String attachmentName;
    private Map<String, Integer> reactions;
    private String senderProfilePictureUrl;

    public Message(String text, String sender, long timestamp, boolean isSentByUser) {
        this.messageId = null;
        this.text = text;
        this.sender = sender;
        this.timestamp = timestamp;
        this.isSentByUser = isSentByUser;
        this.attachmentPath = null;
        this.attachmentType = null;
        this.attachmentName = null;
        this.reactions = new HashMap<>();
    }
    
    public Message(String text, String sender, long timestamp, boolean isSentByUser, String attachmentPath, String attachmentType, String attachmentName) {
        this.messageId = null;
        this.text = text;
        this.sender = sender;
        this.timestamp = timestamp;
        this.isSentByUser = isSentByUser;
        this.attachmentPath = attachmentPath;
        this.attachmentType = attachmentType;
        this.attachmentName = attachmentName;
        this.reactions = new HashMap<>();
    }
    
    public Long getMessageId() {
        return messageId;
    }
    
    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }
    
    public Long getAttachmentId() {
        return attachmentId;
    }
    
    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSentByUser() {
        return isSentByUser;
    }

    public void setSentByUser(boolean sentByUser) {
        isSentByUser = sentByUser;
    }
    
    public String getAttachmentPath() {
        return attachmentPath;
    }
    
    public void setAttachmentPath(String attachmentPath) {
        this.attachmentPath = attachmentPath;
    }
    
    public String getAttachmentType() {
        return attachmentType;
    }
    
    public void setAttachmentType(String attachmentType) {
        this.attachmentType = attachmentType;
    }
    
    public String getAttachmentName() {
        return attachmentName;
    }
    
    public void setAttachmentName(String attachmentName) {
        this.attachmentName = attachmentName;
    }
    
    public boolean hasAttachment() {
        return attachmentPath != null && !attachmentPath.isEmpty();
    }
    
    public Map<String, Integer> getReactions() {
        return reactions;
    }
    
    public void setReactions(Map<String, Integer> reactions) {
        this.reactions = reactions;
    }
    
    public void addReaction(String emoji) {
        if (reactions == null) {
            reactions = new HashMap<>();
        }
        reactions.put(emoji, reactions.getOrDefault(emoji, 0) + 1);
    }
    
    public void removeReaction(String emoji) {
        if (reactions != null && reactions.containsKey(emoji)) {
            int count = reactions.get(emoji);
            if (count <= 1) {
                reactions.remove(emoji);
            } else {
                reactions.put(emoji, count - 1);
            }
        }
    }
    
    public boolean hasReactions() {
        return reactions != null && !reactions.isEmpty();
    }
    
    public String getSenderProfilePictureUrl() {
        return senderProfilePictureUrl;
    }
    
    public void setSenderProfilePictureUrl(String senderProfilePictureUrl) {
        this.senderProfilePictureUrl = senderProfilePictureUrl;
    }
}

