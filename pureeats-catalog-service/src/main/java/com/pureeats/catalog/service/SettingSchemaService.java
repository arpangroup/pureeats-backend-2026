package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.SettingFieldDefinition;
import com.pureeats.catalog.dto.SettingGroupDefinition;
import com.pureeats.catalog.dto.SettingOptionDefinition;
import com.pureeats.catalog.dto.SettingSectionDefinition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single source of truth for every admin-editable platform setting: what sections/tabs exist,
 * what fields live in each, their input type/default/help copy. The admin panel's Settings page
 * renders entirely off {@link #schema()} (GET /api/v1/admin/settings/schema) rather than keeping its
 * own hardcoded field list — add a field here (or a whole new section) and it shows up in the admin
 * UI with no frontend change. {@link #validKeys()} backs the other half of that contract:
 * PUT /api/v1/admin/settings (see ContentService#updateSettings) rejects any key that isn't in this
 * registry, so a value can never be silently saved for a setting the schema doesn't know about
 * either.
 *
 * Excluded on purpose: Razorpay credentials, Firebase web config, the Google Maps API key, and the
 * home page's section-visibility toggles (promo slider / top picks / recommended / cuisine
 * category). Those live as typed fields on {@link AppConfigService}'s structured blob instead of
 * this generic key/value store (see docs in the customer app's src/config/locationResolution.ts and
 * AppConfigService itself) — they need type safety (a write-only secret, structured nested config)
 * this generic string-keyed registry deliberately doesn't provide.
 */
@Service
public class SettingSchemaService {

    public List<SettingSectionDefinition> schema() {
        return List.of(
                generalSection(),
                paymentsSection(),
                smsGatewaysSection(),
                emailSettingsSection(),
                pushNotificationsSection(),
                socialLoginSection(),
                googleMapSection(),
                googleAnalyticsSection(),
                customerAppSection(),
                deliveryAppSection(),
                storeDashboardSection()
        );
    }

    /** Flattened set of every field key across every section — the allow-list PUT /api/v1/admin/settings checks incoming updates against. */
    public Set<String> validKeys() {
        return schema().stream()
                .flatMap(s -> s.groups().stream())
                .flatMap(g -> g.fields().stream())
                .map(SettingFieldDefinition::key)
                .collect(Collectors.toSet());
    }

    // ---- General ----

    private SettingSectionDefinition generalSection() {
        return new SettingSectionDefinition("general", "General", "Settings", List.of(
                group("App info", "Sparkles", List.of(
                        field("app_name", "App name", "text", "PureEats"),
                        field("currency_symbol", "Currency symbol", "text", "₹"),
                        field("currency_code", "Store currency", "text", "INR")
                )),
                group("Contact & support", "Mail", List.of(
                        field("support_email", "Support email", "email", ""),
                        field("support_phone", "Support phone", "text", "")
                )),
                group("Commerce", "Defaults applied to new restaurants and payouts.", "Percent", List.of(
                        field("default_tax_percent", "Default tax (%)", "number", "5"),
                        field("default_commission_rate", "Default commission (%)", "number", "15"),
                        field("min_withdrawal_amount", "Minimum withdrawal (₹)", "number", "500")
                )),
                group("Platform", "Take the customer app offline for maintenance.", "Settings", List.of(
                        field("maintenance_mode", "Maintenance mode", "boolean", "false")
                                .info("When on, customers see a maintenance page instead of the app.")
                )),
                group("Order timing", "How long a restaurant or delivery partner has to respond before an order times out.", "Timer", List.of(
                        field("max_time_accept_order", "Max time to accept order", "number", "10")
                                .placeholder("e.g. 10")
                                .info("Minutes a restaurant has to accept a new order before it is auto-cancelled."),
                        field("max_time_accept_delivery", "Max time to accept delivery", "number", "5")
                                .placeholder("e.g. 5")
                                .info("Minutes a delivery partner has to accept an assigned order before it's reassigned.")
                ))
        ));
    }

    // ---- Payment gateways (Razorpay excluded — see class doc) ----

    private SettingSectionDefinition paymentsSection() {
        return new SettingSectionDefinition("payments", "Payment Gateways", "CreditCard", List.of(
                group("Stripe", "Online payment with Stripe.", "CreditCard", List.of(
                        field("stripe_public_key", "Stripe public key", "text", "").placeholder("pk_live_…"),
                        field("stripe_secret_key", "Stripe secret key", "password", "")
                                .placeholder("sk_live_…")
                                .warning("Never share your secret key or commit it to source control.")
                )),
                group("PayPal", "PayPal Express Checkout.", "CreditCard", List.of(
                        field("paypal_environment", "PayPal environment", "dropdown", "sandbox")
                                .options(option("Sandbox (testing)", "sandbox"), option("Production (live)", "production")),
                        field("paypal_sandbox_key", "PayPal sandbox key", "password", ""),
                        field("paypal_production_key", "PayPal production key", "password", "")
                )),
                group("PayStack", "PayStack payment gateway.", "CreditCard", List.of(
                        field("paystack_public_key", "PayStack public key", "text", ""),
                        field("paystack_private_key", "PayStack private key", "password", "")
                )),
                group("PayTm", "Paytm payment gateway.", "CreditCard", List.of(
                        field("paytm_merchant_id", "Merchant ID", "text", ""),
                        field("paytm_merchant_key", "Merchant key", "password", ""),
                        field("paytm_website", "Website", "text", "").placeholder("WEBSTAGING / DEFAULT"),
                        field("paytm_industry_type", "Industry type", "text", "").placeholder("Retail"),
                        field("paytm_channel_id_website", "Channel ID (for website)", "text", "").placeholder("WEB"),
                        field("paytm_channel_id_mobile", "Channel ID (for mobile)", "text", "").placeholder("WAP"),
                        field("paytm_transaction_url", "Transaction URL", "url", "").placeholder("https://securegw.paytm.in/..."),
                        field("paytm_transaction_status_url", "Transaction status URL (callback)", "url", "").placeholder("https://yourapp.com/api/paytm/callback")
                )),
                group("PayUmoney", "PayUMoney payment gateway.", "CreditCard", List.of(
                        field("payumoney_merchant_key", "Merchant key", "password", ""),
                        field("payumoney_salt", "Salt", "password", ""),
                        field("payumoney_working_key", "Working key", "password", ""),
                        field("payumoney_success_url", "Success URL", "url", "").placeholder("https://yourapp.com/api/payumoney/success"),
                        field("payumoney_failure_url", "Failure URL", "url", "").placeholder("https://yourapp.com/api/payumoney/failure")
                )),
                group("CCAvenue", "CCAvenue gateway.", "CreditCard", List.of(
                        field("ccavenue_merchant_id", "Merchant ID", "text", ""),
                        field("ccavenue_access_code", "Access code", "text", ""),
                        field("ccavenue_working_key", "Working key", "password", ""),
                        field("ccavenue_redirect_url", "Redirect URL", "url", ""),
                        field("ccavenue_cancel_url", "Cancel URL", "url", ""),
                        field("ccavenue_currency", "Currency", "text", "INR").placeholder("INR"),
                        field("ccavenue_language", "Language", "text", "EN").placeholder("EN")
                ))
        ));
    }

    // ---- SMS gateways ----

    private SettingSectionDefinition smsGatewaysSection() {
        return new SettingSectionDefinition("sms-gateways", "SMS Gateways", "MessageSquare", List.of(
                group("Gateway", "MessageSquare", List.of(
                        field("default_sms_gateway", "Default SMS gateway", "dropdown", "msg91")
                                .options(option("Custom", "custom"), option("Twilio", "twilio"), option("MSG91", "msg91"))
                )),
                group("Custom SMS settings", "Used only when the default gateway above is set to Custom.", "Code2", List.of(
                        field("custom_sms_base_url", "Base URL", "url", "").placeholder("https://sms-provider.com/api/send"),
                        field("custom_sms_auth_key", "Auth key", "password", ""),
                        field("custom_sms_sender_id", "Sender ID", "text", "").placeholder("PUREET"),
                        field("custom_sms_method_type", "Method type", "dropdown", "GET").options(option("GET", "GET"), option("POST", "POST")),
                        field("custom_sms_sample_url", "Sample URL", "textarea", "")
                                .placeholder("https://sms-provider.com/api/send?authkey={key}&mobiles={mobile}&message={message}")
                                .info("Use {key}, {mobile}, {message} as placeholders for the values sent on each request."),
                        field("custom_sms_extra_param_1", "Extra parameter 1", "text", ""),
                        field("custom_sms_extra_param_2", "Extra parameter 2", "text", ""),
                        field("custom_sms_extra_param_3", "Extra parameter 3", "text", ""),
                        field("custom_sms_extra_param_4", "Extra parameter 4", "text", ""),
                        field("custom_sms_extra_param_5", "Extra parameter 5", "text", "")
                )),
                group("Twilio settings", "Used only when the default gateway above is set to Twilio.", "MessageSquare", List.of(
                        field("twilio_sid", "Twilio SID", "text", ""),
                        field("twilio_access_token", "Twilio access token", "password", ""),
                        field("twilio_phone_number", "Twilio phone number", "text", "").placeholder("+1XXXXXXXXXX")
                )),
                group("OTP verification", "Bell", List.of(
                        field("otp_verification_registration", "OTP verification on registration", "boolean", "true"),
                        field("otp_message", "OTP message", "textarea", "Your OTP verification code is: {otp}")
                                .placeholder("Your OTP verification code is: {otp}")
                                .info("Use {otp} as a placeholder for the generated code.")
                )),
                group("Order SMS notifications", "MessageSquare", List.of(
                        field("sms_notification_store_owners", "SMS notification for store owners", "boolean", "true"),
                        field("store_owner_new_order_message", "Store owner's new order message", "textarea", "You have a new order #{order_id}.")
                                .placeholder("You have a new order #{order_id}.")
                                .info("Use {order_id} as a placeholder for the order number."),
                        field("include_order_value_sms", "Include order value in the SMS", "boolean", "true"),
                        field("sms_notification_delivery_guys", "SMS notification for delivery guys", "boolean", "true"),
                        field("delivery_guy_new_order_message", "Delivery guy's new order message", "textarea", "A new delivery #{order_id} has been assigned to you.")
                                .placeholder("A new delivery #{order_id} has been assigned to you.")
                                .info("Use {order_id} as a placeholder for the order number.")
                ))
        ));
    }

    // ---- Email ----

    private SettingSectionDefinition emailSettingsSection() {
        return new SettingSectionDefinition("email-settings", "Email Settings", "Mail", List.of(
                group("Email delivery", "Transactional emails are sent through SendGrid.", "Mail", List.of(
                        field("enable_password_reset_email", "Enable password reset email", "boolean", "true"),
                        field("sendgrid_api_key", "API key (SendGrid)", "password", "")
                                .placeholder("SG.xxxxxxxx")
                                .link("SendGrid API keys", "https://app.sendgrid.com/settings/api_keys"),
                        field("send_emails_from_email", "Send emails from \"Email\"", "email", "").placeholder("no-reply@yourdomain.com"),
                        field("send_emails_from_name", "Send emails from \"Name\"", "text", "").placeholder("PureEats"),
                        field("password_reset_email_subject", "Password reset email \"Subject\"", "text", "Reset your password").placeholder("Reset your password")
                ))
        ));
    }

    // ---- Push notifications (Firebase Cloud Messaging excluded — see class doc) ----

    private SettingSectionDefinition pushNotificationsSection() {
        return new SettingSectionDefinition("push-notifications", "Push Notifications", "Bell", List.of(
                group("Push notifications", "Bell", List.of(
                        field("enable_push_notifications", "Enable push notifications", "boolean", "true"),
                        field("push_notifications_order_updates", "Push notifications for order updates", "boolean", "true")
                ))
        ));
    }

    // ---- Social login ----

    private SettingSectionDefinition socialLoginSection() {
        return new SettingSectionDefinition("social-login", "Social Login", "UserCheck", List.of(
                group("Facebook login", "UserCheck", List.of(
                        field("enable_facebook_login", "Enable Facebook login", "boolean", "false"),
                        field("facebook_app_id", "Facebook app ID", "text", "")
                                .placeholder("e.g. 1234567890123456")
                                .link("Facebook for Developers", "https://developers.facebook.com/apps"),
                        field("facebook_login_button_text", "Facebook login button text", "text", "Continue with Facebook").placeholder("Continue with Facebook")
                )),
                group("Google login", "UserCheck", List.of(
                        field("enable_google_login", "Enable Google login", "boolean", "true"),
                        field("google_app_id", "Google app ID", "text", "")
                                .placeholder("e.g. xxxx.apps.googleusercontent.com")
                                .link("Google Cloud console credentials", "https://console.cloud.google.com/apis/credentials"),
                        field("google_login_button_text", "Google login button text", "text", "Continue with Google").placeholder("Continue with Google")
                ))
        ));
    }

    // ---- Google Map (the API key fields excluded — see class doc) ----

    private SettingSectionDefinition googleMapSection() {
        return new SettingSectionDefinition("google-map", "Google Map", "MapPin", List.of(
                group("Google Maps", "MapPin", List.of(
                        field("show_map_order_tracking", "Show map on order tracking page", "boolean", "true")
                ))
        ));
    }

    // ---- Google Analytics ----

    private SettingSectionDefinition googleAnalyticsSection() {
        return new SettingSectionDefinition("google-analytics", "Google Analytics", "BarChart3", List.of(
                group("Google Analytics", "BarChart3", List.of(
                        field("enable_google_analytics", "Enable Google Analytics", "boolean", "false"),
                        field("analytics_ua_id", "Analytics UA ID", "text", "")
                                .placeholder("e.g. G-XXXXXXXXXX or UA-XXXXXXXXX-X")
                                .info("Accepts either a legacy Universal Analytics ID or a GA4 measurement ID.")
                ))
        ));
    }

    // ---- Tax settings ----
    // First pass, filled in to unblock a compile error (schema() called this before it existed) —
    // adjust the actual fields to whatever "Tax Settings" is meant to cover. Deliberately distinct
    // keys from General → Commerce's default_tax_percent, which is a per-new-restaurant default,
    // not a platform-wide display/registration setting.

    private SettingSectionDefinition taxSettingSection() {
        return new SettingSectionDefinition("tax-settings", "Tax Settings", "Percent", List.of(
                group("Tax display", "Percent", List.of(
                        field("tax_label", "Tax label shown to customers", "text", "GST").placeholder("GST"),
                        field("tax_inclusive_pricing", "Menu prices already include tax", "boolean", "false")
                                .info("When on, the tax line is shown as already included in the item price rather than added at checkout."),
                        field("tax_registration_number", "Tax registration number", "text", "").placeholder("e.g. GSTIN")
                ))
        ));
    }

    // ---- Customer app ----

    private SettingSectionDefinition customerAppSection() {
        return new SettingSectionDefinition("customer-app", "Customer Application", "Smartphone", List.of(
                group("Address & checkout", "MapPin", List.of(
                        field("flat_mandatory", "Flat/Apartment mandatory in address", "boolean", "false")
                                .info("Requires the flat/apartment/house number field before an address can be saved."),
                        field("delivery_pin", "Delivery PIN", "boolean", "false")
                                .info("Customer gets a 4-digit PIN they share with the delivery partner to confirm handover."),
                        field("self_pickup", "Self pickup", "boolean", "true")
                                .info("Lets customers choose to collect their order from the restaurant instead of delivery."),
                        field("default_country_code", "Default country code on phone field", "text", "+91").placeholder("+91")
                )),
                group("Browsing & merchandising", "Sparkles", List.of(
                        // Promo slider / Top picks / Recommended / Cuisine category section visibility
                        // live on AppConfig instead (see SectionVisibilityPanel in the admin panel,
                        // same "typed field, not this generic store" reasoning as Razorpay/Firebase/
                        // Google Maps above) - they used to be duplicated here as generic keys that
                        // silently did nothing, since the customer app never read them.
                        field("veg_nonveg_badge", "Veg/Non-veg badge", "boolean", "true"),
                        field("show_discount_percentage", "Show product discount percentage", "boolean", "true"),
                        field("hide_zero_price", "Hide item price when zero", "boolean", "false")
                                .info("Useful for items priced only through variants or add-ons.")
                )),
                group("Display", "Monitor", List.of(
                        field("beautify_datetime", "Beautify date/time", "boolean", "true")
                                .info("Shows friendly relative timestamps (\"2 hours ago\") instead of raw dates."),
                        field("round_up_delivery_charge", "Round up dynamic delivery charge", "boolean", "false")
                                .info("Rounds the calculated delivery fee up to the nearest whole currency unit.")
                ))
        ));
    }

    // ---- Delivery app ----

    private SettingSectionDefinition deliveryAppSection() {
        return new SettingSectionDefinition("delivery-app", "Delivery Application", "Bike", List.of(
                group("Earnings", "Wallet", List.of(
                        field("enable_delivery_earnings", "Enable delivery guy's earnings", "boolean", "true")
                                .info("Shows an earnings summary inside the delivery partner app."),
                        field("delivery_earning_from", "Delivery guy's earning from", "dropdown", "delivery_charge")
                                .options(
                                        option("Delivery charge", "delivery_charge"),
                                        option("Fixed amount per order", "fixed_amount"),
                                        option("Percentage of order total", "percentage_of_order"))
                                .info("Determines how a delivery partner's per-order earning is calculated.")
                )),
                group("Order list", "Bike", List.of(
                        field("show_full_address_order_list", "Show full address on order list", "boolean", "false")
                                .info("When off, the delivery app shows only the area/locality until the order is accepted.")
                ))
        ));
    }

    // ---- Store dashboard ----

    private SettingSectionDefinition storeDashboardSection() {
        return new SettingSectionDefinition("store-dashboard", "Store Dashboard", "Store", List.of(
                group("Order notifications", "Bell", List.of(
                        field("new_order_fetch_rate", "New order fetch rate", "dropdown", "15")
                                .options(
                                        option("Every 5 seconds", "5"),
                                        option("Every 15 seconds", "15"),
                                        option("Every 25 seconds", "25"),
                                        option("Every 30 seconds", "30"))
                                .info("How often the store dashboard polls for new orders."),
                        field("notification_tone", "Notification tone", "radio", "alert-1")
                                .options(option("Alert 1", "alert-1"), option("Alert 2", "alert-2"), option("Alert 3", "alert-3"))
                                .info("Sound played on the store dashboard when a new order arrives.")
                ))
        ));
    }

    // ---- tiny builders, just to keep the sections above readable ----

    private static SettingFieldDefinition field(String key, String label, String fieldType, String defaultValue) {
        return SettingFieldDefinition.of(key, label, fieldType, defaultValue);
    }

    private static SettingOptionDefinition option(String label, String value) {
        return new SettingOptionDefinition(label, value);
    }

    private static SettingGroupDefinition group(String title, String icon, List<SettingFieldDefinition> fields) {
        return SettingGroupDefinition.of(title, icon, fields);
    }

    private static SettingGroupDefinition group(String title, String description, String icon, List<SettingFieldDefinition> fields) {
        return SettingGroupDefinition.of(title, description, icon, fields);
    }
}
