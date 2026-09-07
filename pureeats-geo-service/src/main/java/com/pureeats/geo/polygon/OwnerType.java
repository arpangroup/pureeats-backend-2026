package com.pureeats.geo.polygon;

/**
 * What kind of thing a {@link GeoBoundary} is attached to - a generic {@code ownerType}/{@code ownerId}
 * pair instead of a hard {@code restaurant_id} foreign key, so this module never needs to know what
 * a "restaurant" is. The same trick {@code Coupon.restaurantId = 0} already uses in this codebase to
 * mean "not tied to one specific thing," generalized into a real polymorphic-association pair.
 */
public enum OwnerType {
    /** Today's only real user - would replace, or sit alongside, the plain {@code deliveryRadius} circle on {@code Restaurant}. */
    RESTAURANT,
    /** A dark-store / fulfillment center - not modeled anywhere yet. */
    WAREHOUSE,
    /** A regional distribution hub - not modeled anywhere yet. */
    HUB,
    /** A future third-party delivery partner/provider - zero change to this module or {@link PolygonBoundaryService} when one shows up. */
    VENDOR
}
