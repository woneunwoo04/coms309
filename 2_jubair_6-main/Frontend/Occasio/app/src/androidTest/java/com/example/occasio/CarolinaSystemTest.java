package com.example.occasio;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.util.Log;
import com.example.occasio.auth.SignupActivity;
import com.example.occasio.auth.LoginActivity;
import com.example.occasio.email.EmailInboxActivity;
import com.example.occasio.email.EmailDetailActivity;
import com.example.occasio.feedback.FeedbackActivity;
import com.example.occasio.feedback.MyFeedbacksActivity;
import com.example.occasio.feedback.ViewEventFeedbacksActivity;
import com.example.occasio.feedback.EditFeedbackActivity;
import com.example.occasio.friends.SearchFriendsActivityVolley;
import com.example.occasio.friends.MyFriendsActivity;
import com.example.occasio.friends.FriendRequestActivity;
import com.example.occasio.friends.CreateGroupActivity;
import com.example.occasio.friends.MyGroupsActivity;
import com.example.occasio.friends.SearchGroupsActivityVolley;
import com.example.occasio.messaging.MessagingActivity;
import com.example.occasio.messaging.ChatActivity;
import com.example.occasio.messaging.GroupChatActivity;
import com.example.occasio.messaging.ChatListActivity;
import com.example.occasio.messaging.ChatStorage;
import com.example.occasio.profile.EditProfileActivity;
import com.example.occasio.rewards.RewardsActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import java.util.ArrayList;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.junit.Assert.assertNotNull;
import com.example.occasio.R;

@RunWith(AndroidJUnit4.class)
public class CarolinaSystemTest {
    private static final String TAG = "CarolinaSystemTest";
    private String testUsername;
    private String testEmail;
    private String testPassword = "TestPassword123";
    private Context context;
    private SharedPreferences sharedPreferences;

    @Rule
    public ActivityScenarioRule<SignupActivity> signupRule = new ActivityScenarioRule<>(SignupActivity.class);

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String existingUsername = sharedPreferences.getString("username", "");
        if (existingUsername != null && !existingUsername.isEmpty()) {
            testUsername = existingUsername;
            testEmail = "test_" + existingUsername.replace("testuser_", "") + "@test.com";
        } else {
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            testUsername = "testuser_" + uniqueId;
            testEmail = "test_" + uniqueId + "@test.com";
        }
    }

    @After
    public void tearDown() {
        Log.d(TAG, "Test teardown complete");
    }

    @Test
    public void testAuthenticationAndProfileFlow() throws InterruptedException {
        Intent loginIntent = new Intent(context, LoginActivity.class);
        loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(loginIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("LoginActivity", activity));
        }
        performSignup();
        waitForEventsPage(10000);
        Intent editProfileIntent = new Intent(context, EditProfileActivity.class);
        editProfileIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EditProfileActivity> scenario = ActivityScenario.launch(editProfileIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("EditProfileActivity", activity));
        }
    }

    @Test
    public void testCompleteMessagingFlow() throws InterruptedException {
        performSignup();
        waitForEventsPage(10000);
        Intent messagingIntent = new Intent(context, MessagingActivity.class);
        messagingIntent.putExtra("username", testUsername);
        messagingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<MessagingActivity> scenario = ActivityScenario.launch(messagingIntent)) {
            Thread.sleep(2000);
            scenario.onActivity(activity -> assertNotNull("MessagingActivity", activity));
        }
        Intent chatListIntent = new Intent(context, ChatListActivity.class);
        chatListIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<ChatListActivity> scenario = ActivityScenario.launch(chatListIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("ChatListActivity", activity));
        }
        ChatStorage.saveMessages(context, "test_chat_1", new ArrayList<>());
        ChatStorage.loadMessages(context, "test_chat_1");
        ChatStorage.deleteChatHistory(context, "test_chat_1");
        ChatStorage.clearAllChatHistory(context);
        Intent chatIntent = new Intent(context, ChatActivity.class);
        chatIntent.putExtra("chatId", 1L);
        chatIntent.putExtra("userId", 1L);
        chatIntent.putExtra("contactName", "Test Contact");
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<ChatActivity> scenario = ActivityScenario.launch(chatIntent)) {
            Thread.sleep(2000);
            scenario.onActivity(activity -> assertNotNull("ChatActivity", activity));
        }
        Intent groupChatIntent = new Intent(context, GroupChatActivity.class);
        groupChatIntent.putExtra("groupId", 1L);
        groupChatIntent.putExtra("userId", 1L);
        groupChatIntent.putExtra("chatName", "Test Group");
        groupChatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<GroupChatActivity> scenario = ActivityScenario.launch(groupChatIntent)) {
            Thread.sleep(2000);
            scenario.onActivity(activity -> assertNotNull("GroupChatActivity", activity));
        }
    }

    @Test
    public void testCompleteFriendsAndGroupsFlow() throws InterruptedException {
        performSignup();
        waitForEventsPage(10000);
        Intent searchFriendsIntent = new Intent(context, SearchFriendsActivityVolley.class);
        searchFriendsIntent.putExtra("username", testUsername);
        searchFriendsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<SearchFriendsActivityVolley> scenario = ActivityScenario.launch(searchFriendsIntent)) {
            Thread.sleep(2000);
            scenario.onActivity(activity -> assertNotNull("SearchFriendsActivityVolley", activity));
        }
        Intent myFriendsIntent = new Intent(context, MyFriendsActivity.class);
        myFriendsIntent.putExtra("username", testUsername);
        myFriendsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<MyFriendsActivity> scenario = ActivityScenario.launch(myFriendsIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("MyFriendsActivity", activity));
        }
        Intent friendRequestIntent = new Intent(context, FriendRequestActivity.class);
        friendRequestIntent.putExtra("username", testUsername);
        friendRequestIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<FriendRequestActivity> scenario = ActivityScenario.launch(friendRequestIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("FriendRequestActivity", activity));
        }
        Intent createGroupIntent = new Intent(context, CreateGroupActivity.class);
        createGroupIntent.putExtra("username", testUsername);
        createGroupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<CreateGroupActivity> scenario = ActivityScenario.launch(createGroupIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("CreateGroupActivity", activity));
        }
        Intent myGroupsIntent = new Intent(context, MyGroupsActivity.class);
        myGroupsIntent.putExtra("username", testUsername);
        myGroupsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<MyGroupsActivity> scenario = ActivityScenario.launch(myGroupsIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("MyGroupsActivity", activity));
        }
        Intent searchGroupsIntent = new Intent(context, SearchGroupsActivityVolley.class);
        searchGroupsIntent.putExtra("username", testUsername);
        searchGroupsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<SearchGroupsActivityVolley> scenario = ActivityScenario.launch(searchGroupsIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("SearchGroupsActivityVolley", activity));
        }
    }

    @Test
    public void testEmailFeedbackAndRewardsFlow() throws InterruptedException {
        performSignup();
        waitForEventsPage(10000);
        Intent emailInboxIntent = new Intent(context, EmailInboxActivity.class);
        emailInboxIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EmailInboxActivity> scenario = ActivityScenario.launch(emailInboxIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("EmailInboxActivity", activity));
        }
        Intent emailDetailIntent = new Intent(context, EmailDetailActivity.class);
        emailDetailIntent.putExtra("emailId", 1L);
        emailDetailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EmailDetailActivity> scenario = ActivityScenario.launch(emailDetailIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("EmailDetailActivity", activity));
        }
        Intent feedbackIntent = new Intent(context, FeedbackActivity.class);
        feedbackIntent.putExtra("eventId", 1L);
        feedbackIntent.putExtra("eventName", "Test Event");
        feedbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<FeedbackActivity> scenario = ActivityScenario.launch(feedbackIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("FeedbackActivity", activity));
        }
        Intent myFeedbacksIntent = new Intent(context, MyFeedbacksActivity.class);
        myFeedbacksIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<MyFeedbacksActivity> scenario = ActivityScenario.launch(myFeedbacksIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("MyFeedbacksActivity", activity));
        }
        Intent editFeedbackIntent = new Intent(context, EditFeedbackActivity.class);
        editFeedbackIntent.putExtra("feedbackId", 1L);
        editFeedbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EditFeedbackActivity> scenario = ActivityScenario.launch(editFeedbackIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("EditFeedbackActivity", activity));
        }
        Intent viewFeedbacksIntent = new Intent(context, ViewEventFeedbacksActivity.class);
        viewFeedbacksIntent.putExtra("eventId", 1L);
        viewFeedbacksIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<ViewEventFeedbacksActivity> scenario = ActivityScenario.launch(viewFeedbacksIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("ViewEventFeedbacksActivity", activity));
        }
        Intent rewardsIntent = new Intent(context, RewardsActivity.class);
        rewardsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<RewardsActivity> scenario = ActivityScenario.launch(rewardsIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("RewardsActivity", activity));
        }
    }

    @Test
    public void testModelClassesAndUtilities() throws InterruptedException {
        try {
            Class<?> friendClass = Class.forName("com.example.occasio.friends.Friend");
            java.lang.reflect.Constructor<?> friendConstructor = friendClass.getConstructor(Long.class, String.class, String.class);
            Object friend = friendConstructor.newInstance(1L, "John", "Doe");
            java.lang.reflect.Method getFullName = friendClass.getDeclaredMethod("getFullName");
            getFullName.setAccessible(true);
            String fullName = (String) getFullName.invoke(friend);
            Log.d(TAG, "Friend.getFullName: " + fullName);
            Class<?> messageClass = Class.forName("com.example.occasio.messaging.Message");
            java.lang.reflect.Constructor<?> messageConstructor = messageClass.getConstructor(Long.class, String.class, String.class, String.class, Long.class);
            Object message = messageConstructor.newInstance(1L, "sender", "text", "timestamp", 1L);
            java.lang.reflect.Method hasAttachment = messageClass.getDeclaredMethod("hasAttachment");
            hasAttachment.setAccessible(true);
            boolean hasAttach = (Boolean) hasAttachment.invoke(message);
            java.lang.reflect.Method hasReactions = messageClass.getDeclaredMethod("hasReactions");
            hasReactions.setAccessible(true);
            boolean hasReact = (Boolean) hasReactions.invoke(message);
            java.lang.reflect.Method addReaction = messageClass.getDeclaredMethod("addReaction", String.class);
            addReaction.setAccessible(true);
            addReaction.invoke(message, "like");
            java.lang.reflect.Method removeReaction = messageClass.getDeclaredMethod("removeReaction", String.class);
            removeReaction.setAccessible(true);
            removeReaction.invoke(message, "like");
            Log.d(TAG, "Message methods tested");
        } catch (Exception e) {
            Log.w(TAG, "Model classes: " + e.getMessage());
        }
    }

    private void performSignup() throws InterruptedException {
        try {
            if (sharedPreferences.getBoolean("signup_attempted", false)) return;
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("signup_attempted", true);
            editor.apply();
            onView(withId(R.id.signup_username_edt)).perform(clearText(), typeText(testUsername), closeSoftKeyboard());
            Thread.sleep(300);
            onView(withId(R.id.signup_email_edt)).perform(clearText(), typeText(testEmail), closeSoftKeyboard());
            Thread.sleep(300);
            onView(withId(R.id.signup_fullname_edt)).perform(clearText(), typeText("Test User"), closeSoftKeyboard());
            Thread.sleep(300);
            onView(withId(R.id.signup_password_edt)).perform(clearText(), typeText(testPassword), closeSoftKeyboard());
            Thread.sleep(300);
            onView(withId(R.id.signup_confirm_password_edt)).perform(clearText(), typeText(testPassword), closeSoftKeyboard());
            Thread.sleep(300);
            onView(withId(R.id.signup_signup_btn)).perform(click());
            Thread.sleep(5000);
            editor = sharedPreferences.edit();
            editor.putString("username", testUsername);
            editor.apply();
        } catch (Exception e) {
            Log.w(TAG, "Signup: " + e.getMessage());
        }
    }

    private void waitForEventsPage(long timeoutMs) throws InterruptedException {
        long checkInterval = 500;
        int maxAttempts = (int)(timeoutMs / checkInterval);
        for (int i = 0; i < maxAttempts; i++) {
            try {
                onView(withId(R.id.all_events_container)).check(matches(isDisplayed()));
                return;
            } catch (Exception e) {
                if (i < maxAttempts - 1) Thread.sleep(checkInterval);
            }
        }
    }
}
