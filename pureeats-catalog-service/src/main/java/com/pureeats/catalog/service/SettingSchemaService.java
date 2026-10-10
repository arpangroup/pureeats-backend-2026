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

    // Keys read back by business logic elsewhere (via SettingValueService) - kept as constants so a
    // rename here can't silently desync from the code that honours the value.
    public static final String ORDER_ALERT_SOUND_URL = "order_alert_sound_url";
    /** Order tax rate (%) - applied to every order by OrderPricingService. Key kept as "default_tax_percent" so any value already saved under it takes effect. */
    public static final String TAX_PERCENTAGE = "default_tax_percent";
    /** Platform's cut of a restaurant's item sales, for stores without their own commission rate - deducted from the restaurant payout. */
    public static final String DEFAULT_COMMISSION_RATE = "default_commission_rate";
    /** FLAT or PERCENTAGE - how PLATFORM_FEE_AMOUNT / PLATFORM_FEE_PERCENTAGE turn into the platform fee on an order. */
    public static final String PLATFORM_FEE_TYPE = "platform_fee_type";
    public static final String PLATFORM_FEE_AMOUNT = "platform_fee_amount";
    public static final String PLATFORM_FEE_PERCENTAGE = "platform_fee_percentage";
    /** Upper limit for a PERCENTAGE platform fee (0 = no cap). */
    public static final String PLATFORM_FEE_MAX_AMOUNT = "platform_fee_max_amount";
    public static final String MAX_ACTIVE_ORDERS_PER_CUSTOMER = "max_active_orders_per_customer";
    public static final String MAX_ACTIVE_ORDERS_MESSAGE = "max_active_orders_message";
    public static final String DRIVER_AUTO_OFFLINE_ENABLED = "driver_auto_offline_enabled";
    public static final String DRIVER_INACTIVITY_TIMEOUT_MINUTES = "driver_inactivity_timeout_minutes";
    /** Show per-order payout (commission + tip) to delivery partners in the rider app. */
    public static final String DRIVER_SHOW_PAYOUT = "driver_show_payout";
    /** Delivery partners' default commission (%) - used for every partner without a rate of their own. */
    public static final String DEFAULT_RIDER_COMMISSION_RATE = "default_rider_commission_rate";
    /** What the partner's commission % is applied to: delivery_charge (default) or percentage_of_order. */
    public static final String DELIVERY_EARNING_FROM = "delivery_earning_from";
    /** Shown when a blocked / deleted user opens any app or tries to sign in. */
    public static final String ACCOUNT_BLOCKED_MESSAGE = "account_blocked_message";
    /** Delivery time estimates: T1 default, T2, and how much slower the customer's countdown runs. */
    public static final String DEFAULT_PREP_TIME_MINUTES = "default_prep_time_minutes";
    public static final String RIDER_TO_RESTAURANT_MINUTES = "rider_to_restaurant_minutes";
    public static final String CUSTOMER_ETA_SLOWDOWN = "customer_eta_slowdown_factor";
    public static final String ACCOUNT_DELETED_MESSAGE = "account_deleted_message";
    /** STRAIGHT_LINE (default) or GOOGLE_DISTANCE_MATRIX - see com.pureeats.geo.distance.DistanceSettings. */
    public static final String DISTANCE_METHOD = "distance_calculation_method";
    /** Server-side Google key for the Distance Matrix API (password field - never sent to the apps). */
    public static final String GOOGLE_DISTANCE_API_KEY = "google_distance_matrix_api_key";
    /** Which delivery partners hear about a new order: LINKED_STORES (default), NEARBY or ALL. */
    public static final String RIDER_DISPATCH_MODE = "rider_order_dispatch_mode";
    /** NEARBY mode: partners whose last location is within this many km of the restaurant. */
    public static final String RIDER_DISPATCH_RADIUS_KM = "rider_order_dispatch_radius_km";
    /** LINKED_STORES mode: when a store has no linked partner, offer its orders to nearby partners instead of nobody. */
    public static final String RIDER_DISPATCH_FALLBACK_NEARBY = "rider_order_dispatch_fallback_nearby";
    /** Profile fields a delivery partner may edit in the rider app (all view-only by default). Read by RiderService / ProfileContactChangeService too. */
    public static final String DRIVER_EDIT_NAME = "driver_edit_name";
    public static final String DRIVER_EDIT_VEHICLE_NUMBER = "driver_edit_vehicle_number";
    public static final String DRIVER_EDIT_AGE = "driver_edit_age";
    public static final String DRIVER_EDIT_GENDER = "driver_edit_gender";
    public static final String DRIVER_EDIT_ABOUT = "driver_edit_about";
    public static final String DRIVER_EDIT_PHONE = "driver_edit_phone";
    public static final String DRIVER_EDIT_EMAIL = "driver_edit_email";
    public static final String DRIVER_EDIT_LICENSE = "driver_edit_license";
    public static final String DRIVER_EDIT_ID_PROOF = "driver_edit_id_proof";
    public static final String DRIVER_EDIT_VEHICLE_TYPE = "driver_edit_vehicle_type";
    public static final String DRIVER_EDIT_PAYOUT = "driver_edit_payout";

    public static final String DEFAULT_MAX_ACTIVE_ORDERS_MESSAGE =
            "You already have {count} orders in progress - please wait for one to be delivered before placing another.";

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
    /** Keys of "password" fields - secrets that must never leave the admin API (see ContentService#getPublicSettings). */
    public Set<String> secretKeys() {
        return schema().stream()
                .flatMap(s -> s.groups().stream())
                .flatMap(g -> g.fields().stream())
                .filter(f -> "password".equals(f.fieldType()))
                .map(SettingFieldDefinition::key)
                .collect(Collectors.toSet());
    }

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
                group("Commerce", "Tax charged to customers and the platform's commission on restaurant sales.", "Percent", List.of(
                        field(TAX_PERCENTAGE, "Tax on orders (%)", "number", "5")
                                .info("Charged to the customer on the amount after discount. Example: items ₹400, coupon −₹50 → ₹350 × 5% = ₹17.50 tax. "
                                        + "Applies to new orders and the cart preview; orders already placed keep the rate they were charged."),
                        field(DEFAULT_COMMISSION_RATE, "Default commission (%)", "number", "15")
                                .info("The platform's cut of a restaurant's item total, deducted from the restaurant's payout - customers never see it. "
                                        + "Used for every store that has no commission rate of its own (a store's own rate, set on its edit page, wins). "
                                        + "Example: items ₹500 at 15% → ₹75 commission; the restaurant is paid ₹500 − ₹75 + its packaging charge."),
                        field("min_withdrawal_amount", "Minimum withdrawal (₹)", "number", "500")
                                .info("Reserved for self-service payout requests (e.g. a balance below ₹500 can't be withdrawn). "
                                        + "Not enforced yet - payouts are currently recorded by an admin.")
                )),
                group("Platform fee", "A charge the platform adds to every customer bill, on top of items, tax, packaging and delivery.", "Wallet", List.of(
                        field(PLATFORM_FEE_TYPE, "Platform fee type", "dropdown", "FLAT")
                                .options(option("Flat amount (₹ per order)", "FLAT"), option("Percentage of the order", "PERCENTAGE"))
                                .info("Flat: the same fee on every order. Percentage: a % of the amount after discount, optionally capped. "
                                        + "Shown to customers as \"Platform fee\" in the cart, at checkout, on order tracking and on the invoice. Kept by the platform."),
                        field(PLATFORM_FEE_AMOUNT, "Flat fee (₹)", "number", "0")
                                .placeholder("e.g. 5")
                                .info("Used when the type is Flat. Example: ₹5 → every order pays ₹5, whether it's ₹150 or ₹1,500. 0 = no platform fee."),
                        field(PLATFORM_FEE_PERCENTAGE, "Percentage fee (%)", "number", "0")
                                .placeholder("e.g. 2")
                                .info("Used when the type is Percentage, on the amount after discount. Example: 2% of a ₹350 order = ₹7. 0 = no platform fee."),
                        field(PLATFORM_FEE_MAX_AMOUNT, "Maximum fee (₹)", "number", "0")
                                .placeholder("e.g. 25")
                                .info("Cap for a Percentage fee. Example: 2% capped at ₹25 → a ₹2,000 order pays ₹25, not ₹40. 0 = no cap. Ignored for Flat.")
                )),
                group("Account messages", "What a user sees when their account can't be used - in the customer, delivery partner and restaurant partner apps.", "ShieldAlert", List.of(
                        field(ACCOUNT_BLOCKED_MESSAGE, "Blocked account message", "textarea", "User has been blocked. Please contact customer support.")
                                .info("Shown when an admin blocks the account: the open app signs them out with this message, and signing in again shows it too."),
                        field(ACCOUNT_DELETED_MESSAGE, "Deleted account message", "textarea", "This account has been deleted. Please contact customer support.")
                                .info("Shown when the account was deleted (by an admin or by the user).")
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
                )),
                group("Distance & travel time", "How the distance and travel time between a restaurant and a customer are worked out - used for delivery charges, the delivery-area check, restaurant lists, ETAs (T3) and the delivery partner's distances.", "MapPin", List.of(
                        field(DISTANCE_METHOD, "Distance calculation", "dropdown", "STRAIGHT_LINE")
                                .options(
                                        option("Straight line (default, free)", "STRAIGHT_LINE"),
                                        option("Google Distance Matrix (real road distance & traffic)", "GOOGLE_DISTANCE_MATRIX"))
                                .info("Straight line: the direct distance between the two points - free, but shorter than the real route (e.g. 3.06 km where the road is 6.7 km). "
                                        + "Google Distance Matrix: the actual driving distance and time with live traffic, from Google Maps. Billed by Google per lookup; results are cached for 10 minutes, and restaurants already out of range in a straight line are never looked up. "
                                        + "If Google is unavailable or the key is missing, the straight line is used automatically."),
                        field(GOOGLE_DISTANCE_API_KEY, "Google Maps server API key", "password", "")
                                .placeholder("AIza…")
                                .info("A server key with the Distance Matrix API enabled (Google Cloud console -> APIs & Services). Not the browser key used for the apps' maps - restrict this one by the server's IP address. Leave empty to use the server's configured key (pureeats.distance.google.api-key).")
                                .warning("Never share this key. It's never sent to the customer, partner or store apps.")
                )),
                group("Delivery time estimates", "How the delivery time each app shows is worked out: preparation (T1) + delivery partner to the restaurant (T2) + restaurant to the customer (T3, from the map).", "Timer", List.of(
                        field(DEFAULT_PREP_TIME_MINUTES, "Default preparation time - T1 (minutes)", "number", "20")
                                .info("Used for stores that haven't set their own preparation time (store edit page -> Preparation time)."),
                        field(RIDER_TO_RESTAURANT_MINUTES, "Delivery partner to restaurant - T2 (minutes)", "number", "10")
                                .info("Buffer for the partner to reach the restaurant. Shown to the partner as a countdown after they accept."),
                        field(CUSTOMER_ETA_SLOWDOWN, "Customer countdown slowdown", "number", "1.5")
                                .info("The customer's delivery countdown runs this many times slower than real time, so small delays don't make it jump - e.g. 1.5: 90 seconds pass for every minute it counts down. 1 = real time. "
                                        + "T3 (restaurant to customer) comes from Google Maps when the server's distance provider is Google; otherwise it's estimated from the distance.")
                )),
                group("Order alert sound", "Played on the admin panel, restaurant partner dashboard and driver app when a new order arrives.", "Bell", List.of(
                        field(ORDER_ALERT_SOUND_URL, "New order sound", "audio", "")
                                .info("Upload an MP3 or WAV file (max 2MB). Leave empty to use the built-in chime - it needs no download and is also the automatic fallback if the custom file can't be played.")
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
    // keys from General → Commerce's default_tax_percent (TAX_PERCENTAGE), which is the actual order tax rate,
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
                )),
                group("Order limits", "Caps how many orders a customer can have in progress at once. Use {count} as a placeholder in the message. (The orders-per-time-window rate limit is configured in application.yml - pureeats.order.rate-limit.*.)", "Timer", List.of(
                        field(MAX_ACTIVE_ORDERS_PER_CUSTOMER, "Max orders in queue per customer", "number", "3")
                                .placeholder("e.g. 3")
                                .info("How many not-yet-delivered orders a customer may have in progress at once. 0 = no limit."),
                        field(MAX_ACTIVE_ORDERS_MESSAGE, "Message shown when the queue limit is hit", "textarea", DEFAULT_MAX_ACTIVE_ORDERS_MESSAGE)
                                .placeholder(DEFAULT_MAX_ACTIVE_ORDERS_MESSAGE)
                ))
        ));
    }

    // ---- Delivery app ----

    private SettingSectionDefinition deliveryAppSection() {
        return new SettingSectionDefinition("delivery-app", "Delivery Application", "Bike", List.of(
                group("Earnings", "Wallet", List.of(
                        field("enable_delivery_earnings", "Enable delivery guy's earnings", "boolean", "true")
                                .info("Shows an earnings summary inside the delivery partner app."),
                        field(DEFAULT_RIDER_COMMISSION_RATE, "Default delivery partner commission (%)", "number", "100")
                                .info("What a delivery partner earns per delivered order, as a percentage of what \"Delivery partner earns from\" below points at - the delivery charge by default (plus the customer's tip, paid in full). "
                                        + "Used for every partner who has no commission rate of their own - a partner's own rate (Delivery partners -> open the partner -> Commission rate) wins; set it to 0 to follow this default. "
                                        + "Example: 100% of a ₹40 delivery charge -> ₹40 per delivery. Recorded on each order when it's delivered, so changing it doesn't alter past earnings."),
                        field(DELIVERY_EARNING_FROM, "Delivery partner earns from", "dropdown", "delivery_charge")
                                .options(
                                        option("Delivery charge", "delivery_charge"),
                                        option("Order total", "percentage_of_order"))
                                .info("What the partner's commission % is applied to. Delivery charge (default): 100% of a ₹40 delivery charge = ₹40 per order. "
                                        + "Order total: the % applies to the items' total - e.g. 10% of a ₹500 order = ₹50. "
                                        + "The customer's tip is always paid on top in full. Recorded on each order when it's delivered, so a change applies to deliveries from then on.")
                )),
                group("New order alerts", "Which delivery partners are told about a new order (push notification, the available-orders list and the alert) and can accept it themselves. An admin can still assign any approved partner to any order.", "Bell", List.of(
                        field(RIDER_DISPATCH_MODE, "Who gets new orders", "dropdown", "LINKED_STORES")
                                .options(
                                        option("Partners linked to the store (assigned by admin)", "LINKED_STORES"),
                                        option("Partners near the store (by distance)", "NEARBY"),
                                        option("All online partners", "ALL"))
                                .info("Linked to the store: only partners an admin has linked to that restaurant (sidebar: Delivery partners -> Rider <-> Stores). "
                                        + "Near the store: online partners whose last known location is within the range below of the restaurant. "
                                        + "All online partners: every approved online partner, as before."),
                        field(RIDER_DISPATCH_RADIUS_KM, "Range for \"near the store\" (km)", "number", "5")
                                .placeholder("e.g. 5")
                                .info("Straight-line distance from the partner's last reported location to the restaurant."),
                        field(RIDER_DISPATCH_FALLBACK_NEARBY, "Stores with no linked partner: offer to nearby partners", "boolean", "true")
                                .info("Only for \"Partners linked to the store\". On (recommended): a store with nobody linked still gets riders, from partners within the range above. Off: its orders reach no partner until an admin links one or assigns the order.")
                )),
                group("Order list", "Bike", List.of(
                        field("show_full_address_order_list", "Show full address on order list", "boolean", "false")
                                .info("When off, the delivery app shows only the area/locality until the order is accepted.")
                )),
                group("Location tracking", "MapPin", List.of(
                        field("driver_location_tracking_enabled", "Enable driver location tracking", "boolean", "true")
                                .info("When off, every delivery partner's app stops sending GPS pings and shows a \"Maintenance mode\" banner instead of the online/offline toggle - use this to pause location tracking platform-wide (e.g. during a backend issue) without disabling the app itself.")
                )),
                group("Order screens", "Bike", List.of(
                        field(DRIVER_SHOW_PAYOUT, "Show payout to delivery partners", "boolean", "false")
                                .info("When on, the rider app shows what the partner will earn (commission + tip) on the new-order popup, the available orders list and the active delivery. "
                                        + "Off (default): the amount is hidden from those screens - earnings are still credited and visible in Wallet & Earnings.")
                )),
                group("Profile editing", "Which profile fields a delivery partner can change in the rider app. Everything is view-only by default; switch a field on to let partners edit it. The server enforces this too.", "UserCheck", List.of(
                        field(DRIVER_EDIT_NAME, "Name", "boolean", "false").info("Off: the name is shown but can only be changed from the admin panel."),
                        field(DRIVER_EDIT_VEHICLE_NUMBER, "Vehicle number", "boolean", "false").info("Off: partners can't change the vehicle on record - e.g. to stop swapping to an unverified vehicle."),
                        field(DRIVER_EDIT_AGE, "Age", "boolean", "false"),
                        field(DRIVER_EDIT_GENDER, "Gender", "boolean", "false"),
                        field(DRIVER_EDIT_ABOUT, "About you", "boolean", "false").info("The short description customers can see."),
                        field(DRIVER_EDIT_PHONE, "Mobile number", "boolean", "false").info("On: partners can change their number through an OTP check on the new number. Off: shown read-only."),
                        field(DRIVER_EDIT_EMAIL, "Email", "boolean", "false").info("On: partners can change their email through an OTP check. Off: shown read-only."),
                        field(DRIVER_EDIT_LICENSE, "Driving licence (number + photo)", "boolean", "false")
                                .info("Off: an approved partner can't change their licence - only an admin can (Delivery partners -> partner -> Partner application -> Edit). Applicants can always correct it until approved."),
                        field(DRIVER_EDIT_ID_PROOF, "Aadhaar / PAN", "boolean", "false").info("Off: the ID proof on file can only be changed by an admin."),
                        field(DRIVER_EDIT_VEHICLE_TYPE, "Vehicle type", "boolean", "false").info("Bike / Cycle / EV. Off: only an admin can change it."),
                        field(DRIVER_EDIT_PAYOUT, "Bank account / UPI", "boolean", "false")
                                .info("Where earnings are paid. Off (recommended): only an admin can change it, so a stolen login can't redirect payouts.")
                )),
                group("Inactivity auto-offline", "Bike", List.of(
                        field(DRIVER_AUTO_OFFLINE_ENABLED, "Auto-offline inactive drivers", "boolean", "true")
                                .info("A scheduler marks an online driver offline once their app stops reporting location for the timeout below. The admin panel shows these as a \"Forced stop\". Drivers with a delivery in progress are never auto-offlined."),
                        field(DRIVER_INACTIVITY_TIMEOUT_MINUTES, "Inactivity timeout (minutes)", "number", "10").placeholder("e.g. 10")
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
