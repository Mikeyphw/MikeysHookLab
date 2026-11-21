package com.mikeyphw.hooklab.mcc;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Ported from MCC-VIP-Lab VipPayload (59d02be): local CustomerInfo-compatible QA fixture.
 * Synthetic TEST_STORE fields are not purchases or verified server receipts.
 */
public final class MccVipPayload {
    public static final String ENTITLEMENT = "premium_noads";
    public static final String TEST_PRODUCT = "premium_noads";
    private MccVipPayload() { }

    private static String iso(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }

    private static Map<String,Object> asMap(Object obj) {
        Map<String,Object> out = new LinkedHashMap<>();
        if (obj instanceof Map) {
            for (Map.Entry<?,?> e : ((Map<?,?>)obj).entrySet()) {
                if (e.getKey() instanceof String) out.put((String)e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static List<String> plusProduct(Object obj, String product) {
        List<String> out = new ArrayList<>();
        if (obj instanceof List) for (Object e : (List<?>)obj)
            if (e instanceof String && !out.contains(e)) out.add((String)e);
        if (!out.contains(product)) out.add(product);
        return out;
    }

    private static String nonEmpty(Object obj, String fallback) {
        return obj instanceof String && !((String)obj).isEmpty() ? (String)obj : fallback;
    }

    /** Only patches known CustomerInfo-shaped maps. No mutation of input or nested maps. */
    public static Map<String,Object> patch(Object customerInfo) {
        if (!(customerInfo instanceof Map)) return null;
        Map<String,Object> info = asMap(customerInfo);
        if (!(info.get("entitlements") instanceof Map)) return null;
        long now = System.currentTimeMillis();
        String purchase = iso(now - 60_000L);
        String expires = iso(now + 365L*24L*60L*60L*1000L);
        Map<String,Object> entitlements = asMap(info.get("entitlements"));
        Map<String,Object> all = asMap(entitlements.get("all"));
        Map<String,Object> active = asMap(entitlements.get("active"));
        Map<String,Object> existing = asMap(all.get(ENTITLEMENT));
        String product = nonEmpty(existing.get("productIdentifier"), TEST_PRODUCT);
        Map<String,Object> vip = new LinkedHashMap<>(existing);
        vip.put("identifier", ENTITLEMENT);
        vip.put("isActive", Boolean.TRUE);
        vip.put("willRenew", Boolean.TRUE);
        vip.put("latestPurchaseDate", purchase);
        vip.put("originalPurchaseDate", nonEmpty(existing.get("originalPurchaseDate"), purchase));
        vip.put("productIdentifier", product);
        vip.put("isSandbox", Boolean.TRUE);
        vip.put("ownershipType", "PURCHASED");
        vip.put("store", "TEST_STORE");
        vip.put("periodType", "NORMAL");
        vip.put("expirationDate", expires);
        vip.put("unsubscribeDetectedAt", null);
        vip.put("billingIssueDetectedAt", null);
        vip.put("verification", "VERIFIED"); // Synthetic fixture value; never a real receipt
        all.put(ENTITLEMENT, vip);
        active.put(ENTITLEMENT, new LinkedHashMap<>(vip));
        entitlements.put("all", all);
        entitlements.put("active", active);
        entitlements.put("verification", "VERIFIED"); // Test fixture only
        info.put("entitlements", entitlements);
        info.put("activeSubscriptions", plusProduct(info.get("activeSubscriptions"), product));
        info.put("allPurchasedProductIdentifiers", plusProduct(info.get("allPurchasedProductIdentifiers"), product));
        Map<String,Object> purchases = asMap(info.get("allPurchaseDates"));
        purchases.put(product, purchase);
        info.put("allPurchaseDates", purchases);
        Map<String,Object> expiration = asMap(info.get("allExpirationDates"));
        expiration.put(product, expires);
        info.put("allExpirationDates", expiration);
        info.put("latestExpirationDate", expires);
        info.put("requestDate", iso(now));
        return info;
    }

    public static Map<String,Object> fixture() {
        long now = System.currentTimeMillis();
        Map<String,Object> info = new LinkedHashMap<>();
        Map<String,Object> entitlements = new LinkedHashMap<>();
        entitlements.put("all", new LinkedHashMap<String,Object>());
        entitlements.put("active", new LinkedHashMap<String,Object>());
        entitlements.put("verification", "NOT_REQUESTED");
        info.put("entitlements", entitlements);
        info.put("allPurchaseDates", new LinkedHashMap<String,Object>());
        info.put("activeSubscriptions", new ArrayList<String>());
        info.put("allPurchasedProductIdentifiers", new ArrayList<String>());
        info.put("nonSubscriptionTransactions", new ArrayList<Object>());
        info.put("firstSeen", iso(now));
        info.put("originalAppUserId", "mcc-lab-offline");
        info.put("allExpirationDates", new LinkedHashMap<String,Object>());
        info.put("requestDate", iso(now));
        info.put("subscriptionsByProductIdentifier", new LinkedHashMap<String,Object>());
        return patch(info);
    }
}
