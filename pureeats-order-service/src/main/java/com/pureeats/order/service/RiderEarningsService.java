package com.pureeats.order.service;

import com.pureeats.catalog.repository.RestaurantRepository;
import com.pureeats.domain.common.exception.BadRequestException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.*;
import com.pureeats.domain.enums.CommissionBasis;
import com.pureeats.order.dto.*;
import com.pureeats.order.repository.*;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A delivery partner's money, end to end: per-trip earnings (TripDetail, written on delivery by
 * DeliveryOrderService#creditRiderAndSettle), where they stand right now (earned but not yet paid
 * out, COD cash they're holding), settlements an admin records, and day/week/month analytics.
 * <p>
 * Settlement model: on delivery the rider's commission is credited to their wallet and any COD
 * cash they collected is added to their DeliveryCollection (and held on the trip, {@code cashOnHold}).
 * The two are settled SEPARATELY and never netted: the rider hands over the full COD cash, and the
 * platform pays the full earnings. {@link #settle} can do either or both: collecting COD zeroes each
 * trip's cash on hold and clears it from the DeliveryCollection; paying earnings marks the trips
 * settled and debits the paid-out amount from the wallet (so the wallet balance is always "earned,
 * not yet paid"). Each settlement is recorded as a {@link RiderSettlement}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderEarningsService {

    private static final Pattern ORDER_REF = Pattern.compile("order #(\\S+)");
    private static final Pattern SETTLEMENT_REF = Pattern.compile("Settlement #(\\d+)");
    private static final Pattern META_RATE = Pattern.compile("\"commissionRate\":([0-9.]+)");
    private static final Pattern META_BASIS = Pattern.compile("\"commissionBasis\":\"([A-Z_]+)\"");
    private static final Pattern META_BASE = Pattern.compile("\"commissionBase\":([0-9.]+)");
    private static final Pattern META_TIP = Pattern.compile("\"tip\":([0-9.]+)");

    private final TripDetailRepository tripDetailRepository;
    private final RiderSettlementRepository riderSettlementRepository;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    private final DeliveryCollectionRepository deliveryCollectionRepository;
    private final DeliveryCollectionLogRepository deliveryCollectionLogRepository;
    private final WalletService walletService;
    private final OrderStatusService orderStatusService;
    private final OrderPricingService orderPricingService;

    /** Fallback only (tests / settings unavailable) - the live basis comes from Settings, see {@link #basis()}. */
    @Value("${pureeats.commission.basis:DELIVERY_CHARGE_ONLY}")
    private CommissionBasis commissionBasis;

    /** Settings -> Delivery Application -> Earnings -> Delivery partner earns from. */
    private CommissionBasis basis() {
        CommissionBasis fromSettings = orderPricingService.riderCommissionBasis();
        return fromSettings != null ? fromSettings : commissionBasis;
    }

    // ---- summary / earnings / settlements -----------------------------------------------------

    @Transactional(readOnly = true)
    public RiderEarningsSummaryResponse summary(Long riderUserId) {
        List<TripDetail> trips = trips(riderUserId);
        List<TripDetail> pending = trips.stream().filter(t -> !isSettled(t)).toList();
        // Earnings live in the wallet: credited at delivery, leaving it only through a withdrawal or an admin payout.
        BigDecimal walletBalance = walletBalance(riderUserId);
        BigDecimal pendingWithdrawals = pendingWithdrawals(riderUserId);
        BigDecimal available = walletBalance.subtract(pendingWithdrawals).max(BigDecimal.ZERO);
        BigDecimal pendingEarnings = walletBalance;
        List<TripDetail> cashTrips = codTripsHoldingCash(trips);
        BigDecimal cashInHand = sum(cashTrips, TripDetail::getCashOnHold);
        BigDecimal net = pendingEarnings.subtract(cashInHand);
        BigDecimal lifetime = sum(trips, TripDetail::getRiderEarning);
        RiderSettlementResponse last = riderSettlementRepository.findByRiderUserIdOrderByCreatedAtDesc(riderUserId).stream()
                .findFirst().map(this::toSettlementResponse).orElse(null);
        Set<TripDetail> open = new java.util.LinkedHashSet<>(pending);
        open.addAll(cashTrips);
        return new RiderEarningsSummaryResponse(lifetime, trips.size(), pendingEarnings, cashInHand, net, direction(net),
                pending.size(), lifetime.subtract(pendingEarnings).max(BigDecimal.ZERO), last, open.size(), orderValue(open), cashTrips.size(),
                (int) pending.stream().filter(this::recordedOnOtherBasis).count(), walletBalance, pendingWithdrawals, available,
                payoutTo(riderProfile(riderUserId)));
    }

    /** Every delivered trip, newest first. {@code onlyPending} limits it to trips not yet settled. */
    @Transactional(readOnly = true)
    public List<RiderEarningResponse> earnings(Long riderUserId, boolean onlyPending) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        return trips(riderUserId).stream()
                .filter(t -> !onlyPending || !isSettled(t))
                .sorted(Comparator.comparing(TripDetail::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> toEarningResponse(t, rider))
                .toList();
    }

    @Transactional(readOnly = true)
    public RiderEarningResponse earningForOrder(Long riderUserId, Long orderId) {
        TripDetail trip = tripDetailRepository.findByOrderId(orderId.intValue())
                .filter(t -> t.getRiderId() != null && t.getRiderId().longValue() == riderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No earning found for this order"));
        return toEarningResponse(trip, riderProfile(riderUserId));
    }

    @Transactional(readOnly = true)
    public List<RiderSettlementResponse> settlements(Long riderUserId) {
        return riderSettlementRepository.findByRiderUserIdOrderByCreatedAtDesc(riderUserId).stream()
                .map(this::toSettlementResponse).toList();
    }

    @Transactional(readOnly = true)
    public RiderSettlementResponse settlement(Long riderUserId, Long settlementId) {
        return riderSettlementRepository.findById(settlementId)
                .filter(s -> s.getRiderUserId().equals(riderUserId))
                .map(this::toSettlementResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found"));
    }

    /** Admin: settle every trip the rider currently has pending - see the class doc for what this moves. */
    @Transactional
    public RiderSettlementResponse settle(Long adminUserId, Long riderUserId, SettleRiderRequest request) {
        riderProfile(riderUserId);
        boolean collectCod = request == null || request.collectsCod();
        boolean payEarnings = request == null || request.paysEarnings();
        List<TripDetail> all = trips(riderUserId);
        // Paying out = transferring what's in the wallet (minus withdrawal requests still waiting - those are paid via the queue).
        BigDecimal payout = payEarnings ? walletBalance(riderUserId).subtract(pendingWithdrawals(riderUserId)).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        List<TripDetail> pending = payout.signum() > 0 ? all.stream().filter(t -> !isSettled(t)).toList() : List.of();
        List<TripDetail> cashTrips = collectCod ? codTripsHoldingCash(all) : List.of();
        if (payout.signum() == 0 && cashTrips.isEmpty()) {
            throw new BadRequestException(!collectCod ? "No earnings are pending for this delivery partner"
                    : !payEarnings ? "This delivery partner holds no COD cash" : "This delivery partner has nothing pending to settle");
        }
        // Never netted: the full COD cash is collected and the wallet earnings are paid out in full.
        BigDecimal earnings = payout.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cod = sum(cashTrips, TripDetail::getCashOnHold);
        BigDecimal net = earnings.subtract(cod);
        LocalDateTime now = LocalDateTime.now();
        Set<TripDetail> touched = new java.util.LinkedHashSet<>(pending);
        touched.addAll(cashTrips);

        RiderSettlement settlement = new RiderSettlement();
        settlement.setRiderUserId(riderUserId);
        settlement.setEarningsAmount(earnings);
        settlement.setCodAmount(cod);
        settlement.setNetAmount(net);
        settlement.setDirection(cod.signum() > 0 && earnings.signum() > 0 ? DIRECTION_BOTH
                : cod.signum() > 0 ? RiderSettlement.DIRECTION_COLLECTED_FROM_RIDER : RiderSettlement.DIRECTION_PAID_TO_RIDER);
        settlement.setTripCount(touched.size());
        settlement.setTransactionMode(blankToNull(request != null ? request.transactionMode() : null));
        settlement.setTransactionReference(blankToNull(request != null ? request.transactionReference() : null));
        settlement.setNote(blankToNull(request != null ? request.note() : null));
        settlement.setSettledBy(adminUserId);
        settlement.setCreatedAt(now);
        settlement.setStatus(RiderSettlement.STATUS_PAID);
        settlement.setPaidAt(now);
        settlement = riderSettlementRepository.save(settlement);

        markTripsPaid(pending, earnings, settlement.getId(), now);
        for (TripDetail trip : cashTrips) {
            trip.setCashOnHold(BigDecimal.ZERO);
            trip.setUpdatedAt(now);
        }
        tripDetailRepository.saveAll(touched);

        if (earnings.signum() > 0) {
            walletService.debit(riderUserId, earnings, "Settlement #" + settlement.getId() + " - earnings paid out for " + pending.size() + " trip(s)");
        }
        if (cod.signum() > 0) {
            clearCashCollection(riderUserId, cod, settlement.getId());
        }
        log.info("Admin {} settled rider {}: {} trip(s), earnings {}, COD {}, net {} ({})",
                adminUserId, riderUserId, pending.size(), earnings, cod, net, settlement.getDirection());
        return toSettlementResponse(settlement);
    }

    // ---- wallet ledger with links ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RiderWalletTransactionResponse> walletTransactions(Long riderUserId) {
        return walletService.getTransactions(riderUserId).stream().map(t -> {
            String meta = t.meta() != null ? t.meta() : "";
            String type = WalletService.TX_TYPE_DEPOSIT.equals(t.type()) ? "credit" : "debit";
            Matcher settlement = SETTLEMENT_REF.matcher(meta);
            if (settlement.find()) {
                return new RiderWalletTransactionResponse(t.id(), type, t.amount(), t.meta(), t.createdAt(), "SETTLEMENT", null, null,
                        Long.valueOf(settlement.group(1)));
            }
            Matcher order = ORDER_REF.matcher(meta);
            if (order.find()) {
                String unique = order.group(1);
                Long orderId = orderRepository.findFirstByUniqueOrderId(unique).map(Order::getId).orElse(null);
                return new RiderWalletTransactionResponse(t.id(), type, t.amount(), t.meta(), t.createdAt(), "EARNING", orderId, unique, null);
            }
            return new RiderWalletTransactionResponse(t.id(), type, t.amount(), t.meta(), t.createdAt(), "ADJUSTMENT", null, null, null);
        }).toList();
    }

    // ---- analytics -------------------------------------------------------------------------------

    /** Longest custom range accepted - a little over two years, plenty for a rider's history. */
    private static final int MAX_CUSTOM_RANGE_DAYS = 800;

    /**
     * {@code period}: DAY (today vs yesterday, trend = last 7 days), WEEK (this vs last week, last 8
     * weeks - weeks start Monday), MONTH (this vs last month, last 6 months) or CUSTOM ({@code from}..
     * {@code to} inclusive vs the equally long range just before it; the chart is daily up to 31 days,
     * weekly up to ~4 months, monthly beyond). Breakdowns (hours, weekdays, payment, restaurants)
     * always cover the selected period itself.
     */
    @Transactional(readOnly = true)
    public RiderEarningsAnalyticsResponse analytics(Long riderUserId, String period, LocalDate from, LocalDate to) {
        String p = period == null ? "DAY" : period.trim().toUpperCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        List<TripDetail> trips = trips(riderUserId).stream().filter(t -> t.getCreatedAt() != null).toList();

        LocalDate currentFrom;
        LocalDate currentTo;
        LocalDate previousFrom;
        LocalDate previousTo;
        String bucketSize;
        List<RiderEarningsAnalyticsResponse.Bucket> buckets;
        switch (p) {
            case "DAY" -> {
                currentFrom = today;
                currentTo = today;
                previousFrom = today.minusDays(1);
                previousTo = previousFrom;
                bucketSize = "DAY";
                buckets = series(trips, today.minusDays(6), today, "DAY");
            }
            case "WEEK" -> {
                currentFrom = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                currentTo = currentFrom.plusDays(6);
                previousFrom = currentFrom.minusWeeks(1);
                previousTo = currentFrom.minusDays(1);
                bucketSize = "WEEK";
                buckets = series(trips, currentFrom.minusWeeks(7), currentTo, "WEEK");
            }
            case "MONTH" -> {
                currentFrom = today.withDayOfMonth(1);
                currentTo = currentFrom.plusMonths(1).minusDays(1);
                previousFrom = currentFrom.minusMonths(1);
                previousTo = currentFrom.minusDays(1);
                bucketSize = "MONTH";
                buckets = series(trips, currentFrom.minusMonths(5), currentTo, "MONTH");
            }
            case "CUSTOM" -> {
                if (from == null || to == null) throw new BadRequestException("from and to are required for a custom range");
                if (to.isBefore(from)) throw new BadRequestException("to must be on or after from");
                long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
                if (days > MAX_CUSTOM_RANGE_DAYS) throw new BadRequestException("Choose a range of at most " + MAX_CUSTOM_RANGE_DAYS + " days");
                currentFrom = from;
                currentTo = to;
                previousTo = from.minusDays(1);
                previousFrom = previousTo.minusDays(days - 1);
                bucketSize = days <= 31 ? "DAY" : days <= 124 ? "WEEK" : "MONTH";
                buckets = series(trips, from, to, bucketSize);
            }
            default -> throw new BadRequestException("period must be DAY, WEEK, MONTH or CUSTOM");
        }

        List<TripDetail> inPeriod = inRange(trips, currentFrom, currentTo);
        RiderEarningsAnalyticsResponse.Totals current = totals(inPeriod);
        RiderEarningsAnalyticsResponse.Totals previous = totals(inRange(trips, previousFrom, previousTo));
        BigDecimal change = previous.earnings().signum() == 0 ? null
                : current.earnings().subtract(previous.earnings()).multiply(BigDecimal.valueOf(100))
                .divide(previous.earnings(), 1, RoundingMode.HALF_UP);
        RiderEarningsAnalyticsResponse.Bucket best = buckets.stream()
                .filter(b -> b.earnings().signum() > 0)
                .max(Comparator.comparing(RiderEarningsAnalyticsResponse.Bucket::earnings))
                .orElse(null);

        return new RiderEarningsAnalyticsResponse(p, currentFrom, currentTo, current, previousFrom, previousTo, previous, change,
                bucketSize, buckets, best, byHour(inPeriod), byWeekday(inPeriod, currentFrom), paymentSplit(inPeriod), topRestaurants(inPeriod));
    }

    /** Consecutive buckets of {@code size} covering from..to, each clipped to the range. Week buckets start on Monday. */
    private List<RiderEarningsAnalyticsResponse.Bucket> series(List<TripDetail> trips, LocalDate from, LocalDate to, String size) {
        List<RiderEarningsAnalyticsResponse.Bucket> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern(java.time.temporal.ChronoUnit.DAYS.between(from, to) < 7 ? "EEE" : "d MMM");
        DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("d MMM");
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern(from.getYear() == to.getYear() ? "MMM" : "MMM yy");
        LocalDate cursor = switch (size) {
            case "WEEK" -> from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "MONTH" -> from.withDayOfMonth(1);
            default -> from;
        };
        while (!cursor.isAfter(to)) {
            LocalDate next = switch (size) {
                case "WEEK" -> cursor.plusWeeks(1);
                case "MONTH" -> cursor.plusMonths(1);
                default -> cursor.plusDays(1);
            };
            LocalDate bFrom = cursor.isBefore(from) ? from : cursor;
            LocalDate bTo = next.minusDays(1).isAfter(to) ? to : next.minusDays(1);
            String label = switch (size) {
                case "WEEK" -> bFrom.format(weekFmt);
                case "MONTH" -> cursor.format(monthFmt);
                default -> bFrom.equals(today) ? "Today" : bFrom.format(dayFmt);
            };
            out.add(bucket(trips, label, bFrom, bTo));
            cursor = next;
        }
        return out;
    }

    private static List<RiderEarningsAnalyticsResponse.HourBucket> byHour(List<TripDetail> trips) {
        List<RiderEarningsAnalyticsResponse.HourBucket> out = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            int hour = h;
            List<TripDetail> in = trips.stream().filter(t -> t.getCreatedAt().getHour() == hour).toList();
            out.add(new RiderEarningsAnalyticsResponse.HourBucket(hour, sum(in, TripDetail::getRiderEarning), in.size()));
        }
        return out;
    }

    private static List<RiderEarningsAnalyticsResponse.Bucket> byWeekday(List<TripDetail> trips, LocalDate periodFrom) {
        List<RiderEarningsAnalyticsResponse.Bucket> out = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            List<TripDetail> in = trips.stream().filter(t -> t.getCreatedAt().getDayOfWeek() == day).toList();
            LocalDate sample = periodFrom.with(TemporalAdjusters.nextOrSame(day));
            out.add(new RiderEarningsAnalyticsResponse.Bucket(day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH),
                    sample, sample, sum(in, TripDetail::getRiderEarning), in.size()));
        }
        return out;
    }

    private static RiderEarningsAnalyticsResponse.PaymentSplit paymentSplit(List<TripDetail> trips) {
        List<TripDetail> cod = trips.stream().filter(t -> t.getCashCollectedFromCustomer() != null && t.getCashCollectedFromCustomer().signum() > 0).toList();
        return new RiderEarningsAnalyticsResponse.PaymentSplit(cod.size(), sum(cod, TripDetail::getCashCollectedFromCustomer), trips.size() - cod.size());
    }

    private List<RiderEarningsAnalyticsResponse.RestaurantShare> topRestaurants(List<TripDetail> trips) {
        Map<Integer, List<TripDetail>> byRestaurant = new HashMap<>();
        for (TripDetail t : trips) {
            if (t.getRestaurantId() != null) byRestaurant.computeIfAbsent(t.getRestaurantId(), k -> new ArrayList<>()).add(t);
        }
        return byRestaurant.entrySet().stream()
                .map(e -> new RiderEarningsAnalyticsResponse.RestaurantShare(e.getKey().longValue(),
                        restaurantRepository.findById(e.getKey().longValue()).map(Restaurant::getName).orElse("Restaurant"),
                        e.getValue().size(), sum(e.getValue(), TripDetail::getRiderEarning)))
                .sorted(Comparator.comparing(RiderEarningsAnalyticsResponse.RestaurantShare::earnings).reversed())
                .limit(5)
                .toList();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private RiderEarningsAnalyticsResponse.Bucket bucket(List<TripDetail> trips, String label, LocalDate from, LocalDate to) {
        List<TripDetail> in = inRange(trips, from, to);
        return new RiderEarningsAnalyticsResponse.Bucket(label, from, to, sum(in, TripDetail::getRiderEarning), in.size());
    }

    private static List<TripDetail> inRange(List<TripDetail> trips, LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return trips.stream().filter(t -> !t.getCreatedAt().isBefore(start) && t.getCreatedAt().isBefore(end)).toList();
    }

    private static RiderEarningsAnalyticsResponse.Totals totals(List<TripDetail> trips) {
        BigDecimal earnings = sum(trips, TripDetail::getRiderEarning);
        BigDecimal distance = sum(trips, TripDetail::getDistanceTravelled);
        BigDecimal avg = trips.isEmpty() ? BigDecimal.ZERO : earnings.divide(BigDecimal.valueOf(trips.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgDistance = trips.isEmpty() ? BigDecimal.ZERO : distance.divide(BigDecimal.valueOf(trips.size()), 2, RoundingMode.HALF_UP);
        int activeDays = (int) trips.stream().map(t -> t.getCreatedAt().toLocalDate()).distinct().count();
        return new RiderEarningsAnalyticsResponse.Totals(earnings, trips.size(), avg, distance, avgDistance,
                sum(trips, TripDetail::getCashCollectedFromCustomer), activeDays);
    }

    /**
     * COD cash the rider is holding: only trips whose order is a COD order that is actually DELIVERED. Trips
     * whose order was later returned/cancelled, or that have no real order behind them, don't count.
     */
    /** Result of {@link #recalculatePendingEarnings}. */
    public record RecalculationResult(int trips, BigDecimal before, BigDecimal after) {
    }

    /**
     * Re-records the earning of every UNPAID trip that was recorded on a different basis than today's setting -
     * e.g. delivered while the commission applied to the order total (100% of a ₹930 order) and now it applies to the
     * delivery charge (100% of ₹40). Uses the rate snapshotted on each trip, keeps the tip, and adjusts the wallet by
     * the difference with a note per order. Paid (settled) trips are never touched.
     */
    @Transactional
    public RecalculationResult recalculatePendingEarnings(Long adminUserId, Long riderUserId) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        CommissionBasis target = basis();
        List<TripDetail> trips = trips(riderUserId).stream().filter(t -> !isSettled(t)).filter(this::recordedOnOtherBasis).toList();
        BigDecimal before = BigDecimal.ZERO;
        BigDecimal after = BigDecimal.ZERO;
        for (TripDetail trip : trips) {
            Order order = orderRepository.findById(trip.getOrderId().longValue()).orElse(null);
            if (order == null) continue;
            String meta = trip.getMeta() != null ? trip.getMeta() : "";
            BigDecimal rate = match(META_RATE, meta).map(BigDecimal::new).orElseGet(() -> orderPricingService.riderCommissionRate(rider));
            BigDecimal tip = match(META_TIP, meta).map(BigDecimal::new).orElse(BigDecimal.ZERO);
            BigDecimal base = target == CommissionBasis.DELIVERY_CHARGE_ONLY
                    ? Objects.requireNonNullElse(order.getDeliveryCharge(), BigDecimal.ZERO) : Objects.requireNonNullElse(order.getTotal(), BigDecimal.ZERO);
            BigDecimal commission = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal oldEarning = Objects.requireNonNullElse(trip.getRiderEarning(), BigDecimal.ZERO);
            BigDecimal newEarning = commission.add(tip);
            BigDecimal diff = newEarning.subtract(oldEarning);
            String note = "Earning corrected for order #" + order.getUniqueOrderId() + " (" + label(target) + " basis): "
                    + oldEarning.setScale(2, RoundingMode.HALF_UP) + " -> " + newEarning.setScale(2, RoundingMode.HALF_UP);
            if (diff.signum() > 0) walletService.credit(riderUserId, diff, note);
            if (diff.signum() < 0) walletService.debit(riderUserId, diff.negate(), note);
            trip.setRiderEarning(newEarning);
            trip.setMeta("{\"commissionRate\":" + rate.toPlainString() + ",\"commissionBasis\":\"" + target.name() + "\""
                    + ",\"commissionBase\":" + base.toPlainString() + ",\"tip\":" + tip.toPlainString() + "}");
            trip.setUpdatedAt(LocalDateTime.now());
            tripDetailRepository.save(trip);
            before = before.add(oldEarning);
            after = after.add(newEarning);
        }
        log.info("Admin {} recalculated {} unpaid earning(s) for rider {}: {} -> {}", adminUserId, trips.size(), riderUserId, before, after);
        return new RecalculationResult(trips.size(), before.setScale(2, RoundingMode.HALF_UP), after.setScale(2, RoundingMode.HALF_UP));
    }

    /** True when the trip's snapshot names a basis other than today's (trips with no snapshot are left alone). */
    private boolean recordedOnOtherBasis(TripDetail trip) {
        String recorded = match(META_BASIS, trip.getMeta() != null ? trip.getMeta() : "").orElse(null);
        return recorded != null && !recorded.equals(basis().name());
    }

    private static String label(CommissionBasis basis) {
        return basis == CommissionBasis.DELIVERY_CHARGE_ONLY ? "delivery charge" : "order total";
    }

    // ---- wallet withdrawals ---------------------------------------------------------------------

    /** The partner asks to withdraw from their wallet; the admin pays it (bank/UPI on file) and marks it paid. */
    @Transactional
    public RiderSettlementResponse requestWithdrawal(Long riderUserId, BigDecimal amount) {
        DeliveryGuyDetail rider = riderProfile(riderUserId);
        if (amount == null || amount.signum() <= 0) throw new BadRequestException("Enter an amount to withdraw.");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (payoutTo(rider) == null) throw new BadRequestException("Add your bank account or UPI ID before withdrawing.");
        BigDecimal available = walletBalance(riderUserId).subtract(pendingWithdrawals(riderUserId));
        if (amount.compareTo(available) > 0) {
            throw new BadRequestException("You can withdraw up to " + available.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString() + ".");
        }
        LocalDateTime now = LocalDateTime.now();
        RiderSettlement request = new RiderSettlement();
        request.setRiderUserId(riderUserId);
        request.setEarningsAmount(amount);
        request.setCodAmount(BigDecimal.ZERO);
        request.setNetAmount(amount);
        request.setDirection(RiderSettlement.DIRECTION_PAID_TO_RIDER);
        request.setTripCount(0);
        request.setTransactionMode(rider.getPayoutMethod() != null && rider.getPayoutMethod().equals("BANK") ? "BANK_TRANSFER" : "UPI");
        request.setNote("Withdrawal request");
        request.setStatus(RiderSettlement.STATUS_REQUESTED);
        request.setRequestedAt(now);
        request.setCreatedAt(now);
        log.info("Rider {} requested a withdrawal of {}", riderUserId, amount);
        return toSettlementResponse(riderSettlementRepository.save(request));
    }

    /** Admin queue: withdrawal requests waiting to be paid, oldest first. */
    @Transactional(readOnly = true)
    public List<RiderSettlementResponse> withdrawalRequests(String status) {
        return riderSettlementRepository.findByStatusOrderByCreatedAtAsc(status != null ? status : RiderSettlement.STATUS_REQUESTED).stream()
                .map(this::toSettlementResponse).toList();
    }

    /** Admin paid the request (bank/UPI transfer): the wallet is debited and the oldest unpaid trips are marked paid. */
    @Transactional
    public RiderSettlementResponse payWithdrawal(Long adminUserId, Long requestId, String transactionMode, String transactionReference) {
        RiderSettlement request = riderSettlementRepository.findById(requestId).orElseThrow(() -> new ResourceNotFoundException("Withdrawal request not found"));
        if (!RiderSettlement.STATUS_REQUESTED.equals(request.getStatus())) throw new BadRequestException("This request was already " + request.getStatus().toLowerCase() + ".");
        BigDecimal balance = walletBalance(request.getRiderUserId());
        if (request.getEarningsAmount().compareTo(balance) > 0) {
            throw new BadRequestException("The wallet only holds " + balance.setScale(2, RoundingMode.HALF_UP).toPlainString() + " now - reject this request instead.");
        }
        LocalDateTime now = LocalDateTime.now();
        request.setStatus(RiderSettlement.STATUS_PAID);
        request.setPaidAt(now);
        request.setSettledBy(adminUserId);
        if (transactionMode != null && !transactionMode.isBlank()) request.setTransactionMode(transactionMode.trim());
        request.setTransactionReference(blankToNull(transactionReference));
        riderSettlementRepository.save(request);
        walletService.debit(request.getRiderUserId(), request.getEarningsAmount(), "Settlement #" + request.getId() + " - withdrawal paid out");
        List<TripDetail> unpaid = trips(request.getRiderUserId()).stream().filter(t -> !isSettled(t)).toList();
        int marked = markTripsPaid(unpaid, request.getEarningsAmount(), request.getId(), now);
        request.setTripCount(marked);
        riderSettlementRepository.save(request);
        log.info("Admin {} paid withdrawal #{} of {} to rider {}", adminUserId, requestId, request.getEarningsAmount(), request.getRiderUserId());
        return toSettlementResponse(request);
    }

    @Transactional
    public RiderSettlementResponse rejectWithdrawal(Long adminUserId, Long requestId, String reason) {
        RiderSettlement request = riderSettlementRepository.findById(requestId).orElseThrow(() -> new ResourceNotFoundException("Withdrawal request not found"));
        if (!RiderSettlement.STATUS_REQUESTED.equals(request.getStatus())) throw new BadRequestException("This request was already " + request.getStatus().toLowerCase() + ".");
        request.setStatus(RiderSettlement.STATUS_REJECTED);
        request.setSettledBy(adminUserId);
        request.setNote(blankToNull(reason) != null ? "Rejected: " + reason.trim() : "Rejected");
        log.info("Admin {} rejected withdrawal #{} for rider {}", adminUserId, requestId, request.getRiderUserId());
        return toSettlementResponse(riderSettlementRepository.save(request));
    }

    private BigDecimal walletBalance(Long riderUserId) {
        BigDecimal b = walletService.getBalance(riderUserId).balance();
        return b != null ? b.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    private BigDecimal pendingWithdrawals(Long riderUserId) {
        return riderSettlementRepository.findByRiderUserIdAndStatus(riderUserId, RiderSettlement.STATUS_REQUESTED).stream()
                .map(RiderSettlement::getEarningsAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** Oldest unpaid trips covered by a payout of {@code amount} are marked paid (so the earnings list shows Paid / Pending). */
    private int markTripsPaid(List<TripDetail> unpaid, BigDecimal amount, Long settlementId, LocalDateTime now) {
        List<TripDetail> oldestFirst = unpaid.stream()
                .sorted(Comparator.comparing(TripDetail::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
        BigDecimal left = amount;
        int marked = 0;
        for (TripDetail trip : oldestFirst) {
            BigDecimal earning = Objects.requireNonNullElse(trip.getRiderEarning(), BigDecimal.ZERO);
            if (earning.compareTo(left) > 0) break;
            left = left.subtract(earning);
            trip.setIsSettlementDone(1);
            trip.setSettlementId(settlementId);
            trip.setSettledAt(now);
            trip.setUpdatedAt(now);
            tripDetailRepository.save(trip);
            marked++;
        }
        return marked;
    }

    private static String payoutTo(DeliveryGuyDetail d) {
        if ("UPI".equals(d.getPayoutMethod()) && d.getUpiId() != null) return "UPI " + d.getUpiId();
        if ("BANK".equals(d.getPayoutMethod()) && d.getBankAccountNumber() != null) {
            return (d.getBankAccountHolder() != null ? d.getBankAccountHolder() + " · " : "") + "A/c " + d.getBankAccountNumber()
                    + (d.getBankIfsc() != null ? " · " + d.getBankIfsc() : "");
        }
        return null;
    }

    /** "Collected COD cash and paid earnings" in one settlement. */
    public static final String DIRECTION_BOTH = "BOTH";

    /** Trips of delivered COD orders whose cash the rider still holds. */
    private List<TripDetail> codTripsHoldingCash(List<TripDetail> trips) {
        List<TripDetail> holding = trips.stream().filter(t -> t.getCashOnHold() != null && t.getCashOnHold().signum() > 0).toList();
        List<Long> orderIds = holding.stream().map(TripDetail::getOrderId).filter(Objects::nonNull).map(Integer::longValue).distinct().toList();
        if (orderIds.isEmpty()) return List.of();
        Integer deliveredId = orderStatusService.idFor(com.pureeats.domain.enums.OrderStatusCode.DELIVERED);
        Set<Integer> deliveredCod = new HashSet<>();
        for (Order o : orderRepository.findAllById(orderIds)) {
            if ("COD".equals(o.getPaymentMode()) && Objects.equals(o.getOrderstatusId(), deliveredId)) {
                deliveredCod.add(o.getId().intValue());
            }
        }
        return holding.stream().filter(t -> t.getOrderId() != null && deliveredCod.contains(t.getOrderId())).toList();
    }

    /** What the customers paid for these trips' orders. */
    private BigDecimal orderValue(java.util.Collection<TripDetail> trips) {
        List<Long> orderIds = trips.stream().map(TripDetail::getOrderId).filter(Objects::nonNull).map(Integer::longValue).distinct().toList();
        if (orderIds.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (Order o : orderRepository.findAllById(orderIds)) {
            if (o.getPayable() != null) total = total.add(o.getPayable());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private List<TripDetail> trips(Long riderUserId) {
        return tripDetailRepository.findByRiderId(riderUserId.intValue());
    }

    private static boolean isSettled(TripDetail t) {
        return t.getIsSettlementDone() != null && t.getIsSettlementDone() == 1;
    }

    private static BigDecimal sum(List<TripDetail> trips, Function<TripDetail, BigDecimal> field) {
        return trips.stream().map(field).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static String direction(BigDecimal net) {
        int sign = net.signum();
        return sign > 0 ? RiderSettlement.DIRECTION_PAID_TO_RIDER : sign < 0 ? RiderSettlement.DIRECTION_COLLECTED_FROM_RIDER : RiderSettlement.DIRECTION_EVEN;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void clearCashCollection(Long riderUserId, BigDecimal cod, Long settlementId) {
        deliveryCollectionRepository.findByUserId(riderUserId.intValue()).ifPresent(collection -> {
            BigDecimal remaining = collection.getAmount().subtract(cod);
            collection.setAmount(remaining.signum() < 0 ? BigDecimal.ZERO : remaining);
            collection.setUpdatedAt(LocalDateTime.now());
            deliveryCollectionRepository.save(collection);

            DeliveryCollectionLog entry = new DeliveryCollectionLog();
            entry.setDeliveryCollectionId(collection.getId().intValue());
            entry.setAmount(cod.negate());
            entry.setType("SETTLEMENT");
            entry.setMessage("COD cash settled in settlement #" + settlementId);
            entry.setCreatedAt(LocalDateTime.now());
            entry.setUpdatedAt(LocalDateTime.now());
            deliveryCollectionLogRepository.save(entry);
        });
    }

    private RiderEarningResponse toEarningResponse(TripDetail trip, DeliveryGuyDetail rider) {
        Order order = orderRepository.findById(trip.getOrderId().longValue()).orElse(null);
        Restaurant restaurant = trip.getRestaurantId() != null
                ? restaurantRepository.findById(trip.getRestaurantId().longValue()).orElse(null) : null;

        String meta = trip.getMeta() != null ? trip.getMeta() : "";
        BigDecimal rate = match(META_RATE, meta).map(BigDecimal::new).orElse(null);
        String basis = match(META_BASIS, meta).orElse(null);
        BigDecimal base = match(META_BASE, meta).map(BigDecimal::new).orElse(null);
        boolean rateIsCurrent = rate == null;
        if (rateIsCurrent) {
            rate = orderPricingService.riderCommissionRate(rider);
            basis = basis().name();
            if (order != null) {
                base = basis() == CommissionBasis.DELIVERY_CHARGE_ONLY ? order.getDeliveryCharge() : order.getTotal();
            }
        }

        return new RiderEarningResponse(
                trip.getId(), trip.getOrderId().longValue(),
                order != null ? order.getUniqueOrderId() : String.valueOf(trip.getOrderId()),
                restaurant != null ? restaurant.getName() : "Restaurant",
                order != null ? order.getAddress() : null,
                trip.getCreatedAt(), trip.getDistanceTravelled(),
                order != null ? order.getPayable() : null,
                order != null ? order.getDeliveryCharge() : null,
                order != null ? order.getPaymentMode() : null,
                rate, basis, base, rateIsCurrent,
                trip.getRiderEarning(), match(META_TIP, meta).map(BigDecimal::new).orElse(BigDecimal.ZERO), trip.getCashCollectedFromCustomer(),
                isSettled(trip), trip.getSettlementId(), trip.getSettledAt());
    }

    private static Optional<String> match(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private RiderSettlementResponse toSettlementResponse(RiderSettlement s) {
        DeliveryGuyDetail rider = null;
        try {
            rider = riderProfile(s.getRiderUserId());
        } catch (RuntimeException ignored) {
            // partner profile removed - the record still shows
        }
        return new RiderSettlementResponse(s.getId(), s.getRiderUserId(), s.getEarningsAmount(), s.getCodAmount(), s.getNetAmount(),
                s.getDirection(), s.getTripCount() != null ? s.getTripCount() : 0, s.getTransactionMode(), s.getTransactionReference(),
                s.getNote(), s.getSettledBy(), s.getCreatedAt(), s.getStatus() != null ? s.getStatus() : RiderSettlement.STATUS_PAID,
                s.getRequestedAt(), s.getPaidAt() != null ? s.getPaidAt() : (s.isPaid() ? s.getCreatedAt() : null),
                rider != null ? rider.getName() : null, rider != null ? payoutTo(rider) : null);
    }

    private DeliveryGuyDetail riderProfile(Long riderUserId) {
        User user = userRepository.findById(riderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + riderUserId));
        if (user.getDeliveryGuyDetailId() == null) {
            throw new BadRequestException("This user is not a delivery partner");
        }
        return deliveryGuyDetailRepository.findById(user.getDeliveryGuyDetailId().longValue())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner profile not found"));
    }
}
