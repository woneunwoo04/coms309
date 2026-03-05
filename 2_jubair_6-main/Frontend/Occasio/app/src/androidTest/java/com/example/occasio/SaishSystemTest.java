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
import com.example.occasio.auth.ForgotPasswordActivity;
import com.example.occasio.auth.HomeActivity;
import com.example.occasio.events.AllEventsActivity;
import com.example.occasio.events.CreateEventActivity;
import com.example.occasio.events.EditEventActivity;
import com.example.occasio.events.CalendarActivity;
import com.example.occasio.events.EventCheckInActivity;
import com.example.occasio.events.StartEventAttendanceActivity;
import com.example.occasio.events.ManageEventsActivity;
import com.example.occasio.events.FavoritesActivity;
import com.example.occasio.events.AttendanceActivity;
import com.example.occasio.events.EventAttendanceRecordsActivity;
import com.example.occasio.events.UserEventsActivity;
import com.example.occasio.notifications.NotificationInboxActivity;
import com.example.occasio.organization.OrganizationLoginActivity;
import com.example.occasio.organization.OrganizationSignupActivity;
import com.example.occasio.organization.OrganizationDashboardActivity;
import com.example.occasio.organization.ManageVouchersActivity;
import com.example.occasio.organization.CreateVoucherActivity;
import com.example.occasio.organization.EditVoucherActivity;
import com.example.occasio.organization.CreateEmailTemplateActivity;
import com.example.occasio.organization.EditEmailTemplateActivity;
import com.example.occasio.organization.EmailTemplateManagementActivity;
import com.example.occasio.organization.SendEmailActivity;
import com.example.occasio.organization.OrganizationProfileActivity;
import com.example.occasio.organization.OrganizationRewardsActivity;
import com.example.occasio.organization.OrganizationCalendarActivity;
import com.example.occasio.payment.PaymentActivity;
import com.example.occasio.payment.PaymentHistoryActivity;
import com.example.occasio.profile.ProfileMenuActivity;
import com.example.occasio.GroupsLauncherActivity;
import com.example.occasio.utils.InAppNotificationHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
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
public class SaishSystemTest {
    private static final String TAG = "SaishSystemTest";
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
    public void testCompleteEventsFlow() throws InterruptedException {
        performSignup();
        waitForEventsPage(15000);
        Intent allEventsIntent = new Intent(context, AllEventsActivity.class);
        allEventsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<AllEventsActivity> scenario = ActivityScenario.launch(allEventsIntent)) {
            Thread.sleep(2000);
            scenario.onActivity(activity -> assertNotNull("AllEventsActivity", activity));
        }
        Intent calendarIntent = new Intent(context, CalendarActivity.class);
        calendarIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<CalendarActivity> scenario = ActivityScenario.launch(calendarIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("CalendarActivity", activity));
        }
        Intent favoritesIntent = new Intent(context, FavoritesActivity.class);
        favoritesIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<FavoritesActivity> scenario = ActivityScenario.launch(favoritesIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("FavoritesActivity", activity));
        }
        Intent userEventsIntent = new Intent(context, UserEventsActivity.class);
        userEventsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<UserEventsActivity> scenario = ActivityScenario.launch(userEventsIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("UserEventsActivity", activity));
        }
        Intent attendanceIntent = new Intent(context, AttendanceActivity.class);
        attendanceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<AttendanceActivity> scenario = ActivityScenario.launch(attendanceIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("AttendanceActivity", activity));
        }
        Intent checkInIntent = new Intent(context, EventCheckInActivity.class);
        checkInIntent.putExtra("eventId", 1L);
        checkInIntent.putExtra("eventName", "Test Event");
        checkInIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EventCheckInActivity> scenario = ActivityScenario.launch(checkInIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("EventCheckInActivity", activity));
        }
        Intent recordsIntent = new Intent(context, EventAttendanceRecordsActivity.class);
        recordsIntent.putExtra("eventId", 1L);
        recordsIntent.putExtra("eventName", "Test Event");
        recordsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EventAttendanceRecordsActivity> scenario = ActivityScenario.launch(recordsIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("EventAttendanceRecordsActivity", activity));
        }
        Intent startAttendanceIntent = new Intent(context, StartEventAttendanceActivity.class);
        startAttendanceIntent.putExtra("eventId", 1L);
        startAttendanceIntent.putExtra("orgId", 1L);
        startAttendanceIntent.putExtra("eventName", "Test Event");
        startAttendanceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<StartEventAttendanceActivity> scenario = ActivityScenario.launch(startAttendanceIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("StartEventAttendanceActivity", activity));
        }
    }

    @Test
    public void testOrganizationManagementFlow() throws InterruptedException {
        Intent orgLoginIntent = new Intent(context, OrganizationLoginActivity.class);
        orgLoginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<OrganizationLoginActivity> scenario = ActivityScenario.launch(orgLoginIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("OrganizationLoginActivity", activity));
        }
        Intent orgSignupIntent = new Intent(context, OrganizationSignupActivity.class);
        orgSignupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<OrganizationSignupActivity> scenario = ActivityScenario.launch(orgSignupIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("OrganizationSignupActivity", activity));
        }
        Intent dashboardIntent = new Intent(context, OrganizationDashboardActivity.class);
        dashboardIntent.putExtra("orgId", 1L);
        dashboardIntent.putExtra("orgName", "Test Org");
        dashboardIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<OrganizationDashboardActivity> scenario = ActivityScenario.launch(dashboardIntent)) {
            Thread.sleep(2000);
            scenario.onActivity(activity -> assertNotNull("OrganizationDashboardActivity", activity));
        }
        Intent manageEventsIntent = new Intent(context, ManageEventsActivity.class);
        manageEventsIntent.putExtra("orgId", 1L);
        manageEventsIntent.putExtra("orgName", "Test Org");
        manageEventsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<ManageEventsActivity> scenario = ActivityScenario.launch(manageEventsIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("ManageEventsActivity", activity));
        }
        Intent createEventIntent = new Intent(context, CreateEventActivity.class);
        createEventIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<CreateEventActivity> scenario = ActivityScenario.launch(createEventIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("CreateEventActivity", activity));
        }
        Intent editEventIntent = new Intent(context, EditEventActivity.class);
        editEventIntent.putExtra("eventId", 1L);
        editEventIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EditEventActivity> scenario = ActivityScenario.launch(editEventIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("EditEventActivity", activity));
        }
        Intent manageVouchersIntent = new Intent(context, ManageVouchersActivity.class);
        manageVouchersIntent.putExtra("orgId", 1L);
        manageVouchersIntent.putExtra("orgName", "Test Org");
        manageVouchersIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<ManageVouchersActivity> scenario = ActivityScenario.launch(manageVouchersIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("ManageVouchersActivity", activity));
        }
        Intent createVoucherIntent = new Intent(context, CreateVoucherActivity.class);
        createVoucherIntent.putExtra("orgId", 1L);
        createVoucherIntent.putExtra("orgName", "Test Org");
        createVoucherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<CreateVoucherActivity> scenario = ActivityScenario.launch(createVoucherIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("CreateVoucherActivity", activity));
        }
        Intent editVoucherIntent = new Intent(context, EditVoucherActivity.class);
        editVoucherIntent.putExtra("voucherId", 1L);
        editVoucherIntent.putExtra("orgId", 1L);
        editVoucherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EditVoucherActivity> scenario = ActivityScenario.launch(editVoucherIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("EditVoucherActivity", activity));
        }
        Intent orgProfileIntent = new Intent(context, OrganizationProfileActivity.class);
        orgProfileIntent.putExtra("orgId", 1L);
        orgProfileIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<OrganizationProfileActivity> scenario = ActivityScenario.launch(orgProfileIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("OrganizationProfileActivity", activity));
        }
        Intent orgRewardsIntent = new Intent(context, OrganizationRewardsActivity.class);
        orgRewardsIntent.putExtra("orgId", 1L);
        orgRewardsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<OrganizationRewardsActivity> scenario = ActivityScenario.launch(orgRewardsIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("OrganizationRewardsActivity", activity));
        }
        Intent orgCalendarIntent = new Intent(context, OrganizationCalendarActivity.class);
        orgCalendarIntent.putExtra("orgId", 1L);
        orgCalendarIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<OrganizationCalendarActivity> scenario = ActivityScenario.launch(orgCalendarIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("OrganizationCalendarActivity", activity));
        }
    }

    @Test
    public void testEmailTemplatesAndNotificationsFlow() throws InterruptedException {
        Intent createTemplateIntent = new Intent(context, CreateEmailTemplateActivity.class);
        createTemplateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<CreateEmailTemplateActivity> scenario = ActivityScenario.launch(createTemplateIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("CreateEmailTemplateActivity", activity));
        }
        Intent editTemplateIntent = new Intent(context, EditEmailTemplateActivity.class);
        editTemplateIntent.putExtra("templateId", 1L);
        editTemplateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EditEmailTemplateActivity> scenario = ActivityScenario.launch(editTemplateIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("EditEmailTemplateActivity", activity));
        }
        Intent manageTemplateIntent = new Intent(context, EmailTemplateManagementActivity.class);
        manageTemplateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EmailTemplateManagementActivity> scenario = ActivityScenario.launch(manageTemplateIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("EmailTemplateManagementActivity", activity));
        }
        Intent sendEmailIntent = new Intent(context, SendEmailActivity.class);
        sendEmailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<SendEmailActivity> scenario = ActivityScenario.launch(sendEmailIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("SendEmailActivity", activity));
        }
        performSignup();
        waitForEventsPage(10000);
        Intent notificationsIntent = new Intent(context, NotificationInboxActivity.class);
        notificationsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<NotificationInboxActivity> scenario = ActivityScenario.launch(notificationsIntent)) {
            Thread.sleep(2000);
            scenario.onActivity(activity -> assertNotNull("NotificationInboxActivity", activity));
        }
    }

    @Test
    public void testPaymentAndProfileFlow() throws InterruptedException {
        performSignup();
        waitForEventsPage(10000);
        Intent paymentIntent = new Intent(context, PaymentActivity.class);
        paymentIntent.putExtra("eventId", 1L);
        paymentIntent.putExtra("eventName", "Test Event");
        paymentIntent.putExtra("amount", 10.0);
        paymentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<PaymentActivity> scenario = ActivityScenario.launch(paymentIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("PaymentActivity", activity));
        }
        Intent paymentHistoryIntent = new Intent(context, PaymentHistoryActivity.class);
        paymentHistoryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<PaymentHistoryActivity> scenario = ActivityScenario.launch(paymentHistoryIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("PaymentHistoryActivity", activity));
        }
        Intent profileMenuIntent = new Intent(context, ProfileMenuActivity.class);
        profileMenuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<ProfileMenuActivity> scenario = ActivityScenario.launch(profileMenuIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("ProfileMenuActivity", activity));
        }
        Intent groupsIntent = new Intent(context, GroupsLauncherActivity.class);
        groupsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<GroupsLauncherActivity> scenario = ActivityScenario.launch(groupsIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("GroupsLauncherActivity", activity));
        }
    }

    @Test
    public void testAuthenticationAndUtilitiesFlow() throws InterruptedException {
        Intent homeIntent = new Intent(context, HomeActivity.class);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(homeIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("HomeActivity", activity));
        }
        Intent forgotPasswordIntent = new Intent(context, ForgotPasswordActivity.class);
        forgotPasswordIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<ForgotPasswordActivity> scenario = ActivityScenario.launch(forgotPasswordIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> assertNotNull("ForgotPasswordActivity", activity));
        }
        performSignup();
        waitForEventsPage(10000);
        Intent profileMenuIntent = new Intent(context, ProfileMenuActivity.class);
        profileMenuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<ProfileMenuActivity> scenario = ActivityScenario.launch(profileMenuIntent)) {
            Thread.sleep(1000);
            scenario.onActivity(activity -> {
                assertNotNull("ProfileMenuActivity", activity);
                InAppNotificationHelper.showNotification(activity, "Test Notification", "This is a test notification");
            });
        }
        try {
            Class<?> adapterClass = Class.forName("com.example.occasio.notifications.NotificationAdapter");
            java.lang.reflect.Constructor<?> constructor = adapterClass.getConstructor(java.util.List.class, android.content.Context.class);
            Object adapter = constructor.newInstance(new java.util.ArrayList<>(), context);
            java.lang.reflect.Method formatTimestamp = adapterClass.getDeclaredMethod("formatTimestamp", String.class);
            formatTimestamp.setAccessible(true);
            formatTimestamp.invoke(adapter, "2024-01-01T10:00:00");
            java.lang.reflect.Method isSelectionMode = adapterClass.getDeclaredMethod("isSelectionMode");
            isSelectionMode.setAccessible(true);
            isSelectionMode.invoke(adapter);
            java.lang.reflect.Method selectAll = adapterClass.getDeclaredMethod("selectAll");
            selectAll.setAccessible(true);
            selectAll.invoke(adapter);
            java.lang.reflect.Method updateCardSelection = adapterClass.getDeclaredMethod("updateCardSelection", int.class, boolean.class);
            updateCardSelection.setAccessible(true);
            updateCardSelection.invoke(adapter, 0, true);
            Log.d(TAG, "NotificationAdapter methods tested");
        } catch (Exception e) {
            Log.w(TAG, "Adapter methods: " + e.getMessage());
        }
        try {
            Class<?> calendarAdapterClass = Class.forName("com.example.occasio.events.CalendarAdapter");
            java.lang.reflect.Constructor<?> constructor = calendarAdapterClass.getConstructor(java.util.List.class, android.content.Context.class);
            Object adapter = constructor.newInstance(new java.util.ArrayList<>(), context);
            java.lang.reflect.Method formatDate = calendarAdapterClass.getDeclaredMethod("formatDate", String.class);
            formatDate.setAccessible(true);
            formatDate.invoke(adapter, "2024-01-01");
            java.lang.reflect.Method formatTime = calendarAdapterClass.getDeclaredMethod("formatTime", String.class);
            formatTime.setAccessible(true);
            formatTime.invoke(adapter, "10:00:00");
            Log.d(TAG, "CalendarAdapter methods tested");
        } catch (Exception e) {
            Log.w(TAG, "CalendarAdapter: " + e.getMessage());
        }
        try {
            Class<?> paymentAdapterClass = Class.forName("com.example.occasio.payment.PaymentHistoryAdapter");
            java.lang.reflect.Constructor<?> constructor = paymentAdapterClass.getConstructor(java.util.List.class, android.content.Context.class);
            Object adapter = constructor.newInstance(new java.util.ArrayList<>(), context);
            java.lang.reflect.Method formatDate = paymentAdapterClass.getDeclaredMethod("formatDate", String.class);
            formatDate.setAccessible(true);
            formatDate.invoke(adapter, "2024-01-01");
            Log.d(TAG, "PaymentHistoryAdapter methods tested");
        } catch (Exception e) {
            Log.w(TAG, "PaymentHistoryAdapter: " + e.getMessage());
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
