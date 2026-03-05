package com.example.occasio.utils;

/**
 * Centralized server configuration for the entire application
 * Change the BASE_URL here to update it throughout the app
 */
public class ServerConfig {
    // Production server
    // public static final String BASE_URL = "http://coms-3090-031.class.las.iastate.edu:8080";
    
    // Local testing (change when testing locally)
    public static final String BASE_URL = "http://10.0.2.2:8080";  // For emulator (localhost)
    // public static final String BASE_URL = "http://192.168.1.116:8080";  // For physical device (replace with your computer's IP)
    
    // API Endpoints
    public static final String LOGIN_URL = BASE_URL + "/user_info/login";
    public static final String SIGNUP_URL = BASE_URL + "/user_info/signup";
    public static final String USER_INFO_URL = BASE_URL + "/user_info";
    public static final String USER_INFO_BY_USERNAME = BASE_URL + "/user_info/username/";
    public static final String USER_INFO_EDIT = BASE_URL + "/user_info/edit/";
    
    // Events
    public static final String EVENTS_URL = BASE_URL + "/api/events";
    public static final String EVENTS_REGISTER_URL = BASE_URL + "/api/attendance/register";
    public static final String EVENTS_UNREGISTER_URL = BASE_URL + "/api/attendance/unregister";
    public static final String EVENTS_FAVORITES_URL = BASE_URL + "/api/attendance/favorites/";
    public static final String EVENTS_SEARCH_URL = BASE_URL + "/api/events/search";
    
    // Organizations
    public static final String ORG_LOGIN_URL = BASE_URL + "/api/organizations/login";
    public static final String ORG_SIGNUP_URL = BASE_URL + "/api/organizations";
    public static final String ORG_INFO_URL = BASE_URL + "/api/organizations";
    
    // Friends
    public static final String FRIENDS_URL = BASE_URL + "/api/friends";
    public static final String FRIENDS_REQUEST_URL = BASE_URL + "/api/friends/request";
    public static final String FRIENDS_ACCEPT_URL = BASE_URL + "/api/friends/accept/";
    public static final String FRIENDS_REJECT_URL = BASE_URL + "/api/friends/reject/";
    public static final String FRIENDS_REMOVE_URL = BASE_URL + "/api/friends/remove";
    public static final String FRIENDS_PENDING_URL = BASE_URL + "/api/friends/pending/";
    public static final String FRIENDS_SENT_URL = BASE_URL + "/api/friends/sent/";
    public static final String FRIENDS_CHECK_URL = BASE_URL + "/api/friends/check";
    
    // Chat & Messaging
    public static final String CHAT_URL = BASE_URL + "/api/chats";
    public static final String CHAT_DIRECT_URL = BASE_URL + "/api/chats/direct";
    public static final String CHAT_GROUP_URL = BASE_URL + "/api/chats/group";
    public static final String MESSAGES_URL = BASE_URL + "/api/messages";
    
    // Groups
    public static final String GROUPS_URL = BASE_URL + "/api/groups";
    public static final String GROUP_JOIN_URL = BASE_URL + "/api/groups/join/";
    public static final String GROUP_LEAVE_URL = BASE_URL + "/api/groups/leave/";
    
    // Notifications
    public static final String NOTIFICATIONS_URL = BASE_URL + "/api/notifications";
    public static final String NOTIFICATIONS_CATEGORIES_URL = BASE_URL + "/api/notification-categories";
    public static final String NOTIFICATIONS_PREFERENCES_URL = BASE_URL + "/api/notification-preferences";
    
    // Rewards
    public static final String REWARDS_URL = BASE_URL + "/api/rewards";
    public static final String REWARDS_EARN_URL = BASE_URL + "/api/rewards/earn/attendance";
    public static final String REWARDS_REDEEM_URL = BASE_URL + "/api/rewards/redeem/";
    public static final String REWARDS_POINTS_URL = BASE_URL + "/api/rewards/{userInfoId}/points";
    public static final String REWARDS_TRANSACTIONS_URL = BASE_URL + "/api/rewards/{userInfoId}/transactions";
    public static final String VOUCHERS_URL = BASE_URL + "/api/vouchers";
    
    // Attendance
    public static final String ATTENDANCE_URL = BASE_URL + "/api/attendance";
    public static final String COURSE_ATTENDANCE_URL = BASE_URL + "/api/hybrid-attendance";
    public static final String EVENT_ATTENDANCE_URL = BASE_URL + "/api/event-attendance";
    public static final String EVENT_ATTENDANCE_SESSIONS_URL = BASE_URL + "/api/event-attendance/sessions";
    
    // Calendar
    public static final String CALENDAR_URL = BASE_URL + "/api/calendar";
    
    // Payment endpoints
    public static final String PAYMENT_CREATE_INTENT_URL = BASE_URL + "/api/payments/create-intent";
    public static final String PAYMENT_CONFIRM_URL = BASE_URL + "/api/payments/confirm";
    public static final String PAYMENT_HISTORY_URL = BASE_URL + "/api/payments/history/";
    
    // Stripe publishable key configured for test mode
    public static final String STRIPE_PUBLISHABLE_KEY = "pk_test_51ScVEbPqBMfA33X0xc5MAhGSZgc9hNRIllPJ4nrBa2Gylqvxz0uOmto0H9AEqUliMvWYFQGdik9czzTmRujTzNr200owop6MQy";
    
    // WebSocket Endpoints (dynamically generated from BASE_URL)
    public static final String WS_NOTIFICATION_URL = getWebSocketBase() + "/ws/notifications/";
    public static final String WS_CHAT_URL = getWebSocketBase() + "/ws/chat/";
    public static final String WS_ATTENDANCE_URL = getWebSocketBase() + "/ws/attendance/";
    public static final String WS_REWARD_URL = getWebSocketBase() + "/ws/reward/";
    public static final String WS_STOMP_URL = getWebSocketBase() + "/ws-stomp";
    
    // Network Settings
    public static final int DEFAULT_TIMEOUT_MS = 15000; // 15 seconds
    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final float DEFAULT_BACKOFF_MULT = 1.0f;
    
    // Helper method to check if we're in offline mode
    public static boolean isOfflineMode() {
        // This can be expanded to check actual network connectivity
        return false;
    }
    
    // Helper method to get the base URL for WebSocket
    public static String getWebSocketBase() {
        return BASE_URL.replace("http://", "ws://").replace("https://", "wss://");
    }
}
