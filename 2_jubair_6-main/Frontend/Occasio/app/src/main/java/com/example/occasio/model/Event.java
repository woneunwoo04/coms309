package com.example.occasio.model;
import com.example.occasio.R;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Event implements Serializable {
    private int id;
    private String title;
    private String description;
    private String location;
    private String startTime;
    private String endTime;
    private String eventType;
    private String organizerId;
    private String organizerName;
    private int maxAttendees;
    private int currentAttendees;
    private boolean isPublic;
    private String imageUrl;
    private String category;
    private String tags;
    private double latitude;
    private double longitude;
    private boolean hasActiveSession; // Whether an attendance session is active for this event
    private Double registrationFee; // Registration fee in USD (null = free event)

    // Default constructor
    public Event() {}

    // Basic constructor
    public Event(int id, String title, String description, String location, 
                String startTime, String endTime, String eventType, String organizerId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
        this.eventType = eventType;
        this.organizerId = organizerId;
    }

    // Full constructor
    public Event(int id, String title, String description, String location, 
                String startTime, String endTime, String eventType, String organizerId,
                String organizerName, int maxAttendees, int currentAttendees, 
                boolean isPublic, String imageUrl, String category, String tags,
                double latitude, double longitude) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
        this.eventType = eventType;
        this.organizerId = organizerId;
        this.organizerName = organizerName;
        this.maxAttendees = maxAttendees;
        this.currentAttendees = currentAttendees;
        this.isPublic = isPublic;
        this.imageUrl = imageUrl;
        this.category = category;
        this.tags = tags;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    public String getOrganizerName() { return organizerName; }
    public void setOrganizerName(String organizerName) { this.organizerName = organizerName; }

    public int getMaxAttendees() { return maxAttendees; }
    public void setMaxAttendees(int maxAttendees) { this.maxAttendees = maxAttendees; }

    public int getCurrentAttendees() { return currentAttendees; }
    public void setCurrentAttendees(int currentAttendees) { this.currentAttendees = currentAttendees; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public boolean hasActiveSession() { return hasActiveSession; }
    public void setHasActiveSession(boolean hasActiveSession) { this.hasActiveSession = hasActiveSession; }

    public Double getRegistrationFee() { return registrationFee; }
    public void setRegistrationFee(Double registrationFee) { this.registrationFee = registrationFee; }
    
    public boolean requiresPayment() {
        return registrationFee != null && registrationFee > 0;
    }

    // Helper methods
    public boolean isFull() {
        return maxAttendees > 0 && currentAttendees >= maxAttendees;
    }

    public int getAvailableSpots() {
        return maxAttendees > 0 ? maxAttendees - currentAttendees : -1;
    }

    public String getFormattedStartTime() {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault());
            Date date = inputFormat.parse(startTime);
            return outputFormat.format(date);
        } catch (ParseException e) {
            return startTime;
        }
    }

    public String getFormattedEndTime() {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            Date date = inputFormat.parse(endTime);
            return outputFormat.format(date);
        } catch (ParseException e) {
            return endTime;
        }
    }

    public String getDuration() {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date start = format.parse(startTime);
            Date end = format.parse(endTime);
            long diffInMinutes = (end.getTime() - start.getTime()) / (1000 * 60);
            
            if (diffInMinutes < 60) {
                return diffInMinutes + " minutes";
            } else if (diffInMinutes < 1440) {
                return (diffInMinutes / 60) + " hours";
            } else {
                return (diffInMinutes / 1440) + " days";
            }
        } catch (ParseException e) {
            return "Unknown duration";
        }
    }

    @Override
    public String toString() {
        return "Event{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", location='" + location + '\'' +
                ", startTime='" + startTime + '\'' +
                ", endTime='" + endTime + '\'' +
                ", eventType='" + eventType + '\'' +
                ", organizerId='" + organizerId + '\'' +
                ", organizerName='" + organizerName + '\'' +
                ", maxAttendees=" + maxAttendees +
                ", currentAttendees=" + currentAttendees +
                ", isPublic=" + isPublic +
                '}';
    }
}
