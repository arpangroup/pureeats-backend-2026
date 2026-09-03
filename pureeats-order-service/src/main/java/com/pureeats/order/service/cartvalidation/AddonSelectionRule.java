package com.pureeats.order.service.cartvalidation;

import com.pureeats.catalog.repository.AddonCategoryItemRepository;
import com.pureeats.catalog.repository.AddonRepository;
import com.pureeats.domain.entity.Addon;
import com.pureeats.domain.entity.AddonCategoryItem;
import com.pureeats.domain.entity.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Blocks an addon selection that was never actually offered on that item - nothing today stops a request naming an addon from a completely different item/restaurant. */
@Component
@RequiredArgsConstructor
public class AddonSelectionRule implements CartValidationRule {

    private final AddonRepository addonRepository;
    private final AddonCategoryItemRepository addonCategoryItemRepository;

    @Override
    public List<CartIssue> evaluate(CartValidationContext context) {
        List<CartIssue> issues = new ArrayList<>();
        for (CartLine line : context.lines()) {
            if (line.selectedAddonIds() == null || line.selectedAddonIds().isEmpty()) {
                continue;
            }
            Item item = context.itemFor(line);
            if (item == null) {
                continue; // already flagged by ItemAvailabilityRule
            }
            Set<Long> allowedCategoryIds = addonCategoryItemRepository.findByItemId(item.getId()).stream()
                    .map(AddonCategoryItem::getAddonCategoryId)
                    .collect(Collectors.toSet());
            for (Long addonId : line.selectedAddonIds()) {
                Addon addon = addonRepository.findById(addonId).orElse(null);
                boolean allowed = addon != null && allowedCategoryIds.contains(addon.getAddonCategoryId().longValue());
                if (!allowed) {
                    issues.add(new CartIssue(line.itemId(),
                            (addon != null ? "\"" + addon.getName() + "\"" : "The selected addon") + " is not available for this item"));
                }
            }
        }
        return issues;
    }
}
