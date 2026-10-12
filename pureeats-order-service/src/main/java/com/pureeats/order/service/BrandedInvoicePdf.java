package com.pureeats.order.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pureeats.order.dto.OrderItemAddonResponse;
import com.pureeats.order.dto.OrderItemResponse;
import com.pureeats.order.dto.OrderResponse;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The "Branded" invoice (Settings -> General -> Invoice -> Invoice style): a coloured "Summary and Receipt" - order id
 * and time, customer and delivery partner, the restaurant and the delivery address in side-by-side boxes, an item
 * summary table, the bill breakdown with the grand total highlighted, payment method, and the admin-configured terms
 * & conditions (with the restaurant's and the platform's licence numbers when set).
 */
final class BrandedInvoicePdf {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final Color NAVY = new Color(0x0B, 0x3B, 0x7A);
    private static final Color GREEN = new Color(0x16, 0xA3, 0x4A);
    private static final Color TINT = new Color(0xE8, 0xF3, 0xFC);
    private static final Color GREEN_TINT = new Color(0xE3, 0xF5, 0xE6);
    private static final Color BORDER = new Color(0x9C, 0xCF, 0xEA);
    private static final Color INK = new Color(0x1F, 0x29, 0x37);

    private static final Font BRAND = new Font(Font.HELVETICA, 26, Font.BOLD, NAVY);
    private static final Font BRAND_GREEN = new Font(Font.HELVETICA, 26, Font.BOLD, GREEN);
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 13, Font.NORMAL, INK);
    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD, NAVY);
    private static final Font LABEL = new Font(Font.HELVETICA, 10, Font.BOLD, NAVY);
    private static final Font VALUE = new Font(Font.HELVETICA, 11, Font.NORMAL, INK);
    private static final Font SECTION = new Font(Font.HELVETICA, 12, Font.BOLD, NAVY);
    private static final Font TABLE_HEAD = new Font(Font.HELVETICA, 10, Font.BOLD, INK);
    private static final Font TOTAL = new Font(Font.HELVETICA, 13, Font.BOLD, NAVY);
    private static final Font TERMS = new Font(Font.HELVETICA, 8, Font.NORMAL, NAVY);
    private static final Font TERMS_BOLD = new Font(Font.HELVETICA, 10, Font.BOLD, NAVY);

    private BrandedInvoicePdf() {
    }

    /**
     * @param restaurantAddress    may be null
     * @param restaurantLicence    the restaurant's FSSAI / licence number (Restaurant.certificate), may be null
     * @param platformLicence      the platform's FSSAI number (Settings -> Invoice), may be blank
     * @param terms                one term per line (Settings -> Invoice -> Terms & conditions)
     */
    static byte[] render(OrderResponse order, String restaurantAddress, String restaurantLicence, String platformLicence, List<String> terms) {
        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(header());
            document.add(twoBoxes(box("Order ID:", order.uniqueOrderId()), box("Order Time:", order.createdAt() != null ? order.createdAt().format(DATE) : "")));
            document.add(twoBoxes(box("Customer Name:", order.customer() != null ? order.customer().name() : ""),
                    box("Delivery Partner:", order.deliveryGuyName() != null ? order.deliveryGuyName() : (order.deliveryType() != null && order.deliveryType() == 1 ? "Self pickup" : "-"))));
            document.add(twoBoxes(restaurantBox(order, restaurantAddress), deliveryBox(order)));
            document.add(itemsSection(order));
            document.add(billSection(order));
            document.add(termsSection(order, restaurantLicence, platformLicence, terms));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate invoice PDF for order " + order.id(), e);
        }
    }

    private static PdfPTable header() throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{1, 1});
        t.setWidthPercentage(100);
        t.setSpacingAfter(8);
        Phrase brand = new Phrase();
        brand.add(new Chunk("Pure", BRAND));
        brand.add(new Chunk("Eats", BRAND_GREEN));
        Paragraph left = new Paragraph(brand);
        left.setAlignment(Element.ALIGN_CENTER);
        Paragraph sub = new Paragraph("Food Order", SUBTITLE);
        sub.setAlignment(Element.ALIGN_CENTER);
        PdfPCell l = cell(TINT);
        l.addElement(left);
        l.addElement(sub);
        l.setBorderWidthRight(0);
        t.addCell(l);
        PdfPCell r = cell(TINT);
        Paragraph title = new Paragraph("Summary and Receipt", TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        r.addElement(title);
        r.setVerticalAlignment(Element.ALIGN_MIDDLE);
        r.setBorderWidthLeft(0);
        t.addCell(r);
        return t;
    }

    private static PdfPCell box(String label, String value) {
        PdfPCell c = cell(TINT);
        c.addElement(new Paragraph(label, LABEL));
        c.addElement(new Paragraph(value == null ? "" : value, VALUE));
        return c;
    }

    private static PdfPCell restaurantBox(OrderResponse order, String restaurantAddress) {
        PdfPCell c = cell(Color.WHITE);
        c.addElement(new Paragraph("RESTAURANT (Profile)", SECTION));
        c.addElement(new Paragraph("Name:", LABEL));
        c.addElement(new Paragraph(order.restaurant() != null ? order.restaurant().name() : "", VALUE));
        if (restaurantAddress != null && !restaurantAddress.isBlank()) {
            c.addElement(new Paragraph("Address:", LABEL));
            c.addElement(new Paragraph(restaurantAddress, VALUE));
        }
        return c;
    }

    private static PdfPCell deliveryBox(OrderResponse order) {
        PdfPCell c = cell(Color.WHITE);
        boolean pickup = order.deliveryType() != null && order.deliveryType() == 1;
        c.addElement(new Paragraph(pickup ? "PICKUP" : "DELIVERY (Address)", SECTION));
        StringBuilder address = new StringBuilder();
        if (!pickup && order.addressTag() != null) address.append(order.addressTag()).append(" - ");
        address.append(order.address() != null ? order.address() : "");
        c.addElement(new Paragraph(address.toString(), VALUE));
        if (!pickup && order.addressLandmark() != null) c.addElement(new Paragraph("Landmark: " + order.addressLandmark(), VALUE));
        return c;
    }

    private static PdfPTable twoBoxes(PdfPCell left, PdfPCell right) {
        PdfPTable t = new PdfPTable(new float[]{1, 1});
        t.setWidthPercentage(100);
        t.setSpacingAfter(6);
        PdfPCell gapL = left;
        gapL.setPaddingRight(8);
        t.addCell(gapL);
        t.addCell(right);
        return t;
    }

    private static PdfPTable itemsSection(OrderResponse order) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{4, 1, 1.6f, 1.6f});
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setSpacingAfter(8);
        PdfPCell title = cell(TINT);
        title.setColspan(4);
        title.addElement(new Paragraph("ITEM SUMMARY TABLE", SECTION));
        t.addCell(title);
        for (String h : new String[]{"Item Name", "Qty", "Unit Price (Rs.)", "Total Price (Rs.)"}) {
            PdfPCell c = cell(GREEN_TINT);
            Paragraph p = new Paragraph(h, TABLE_HEAD);
            p.setAlignment(h.equals("Item Name") ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
            c.addElement(p);
            t.addCell(c);
        }
        for (OrderItemResponse item : order.items()) {
            BigDecimal addons = item.addons().stream().map(OrderItemAddonResponse::addonPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal unit = item.price().add(addons);
            String name = item.name() + (item.addons().isEmpty() ? "" : " (" + String.join(", ", item.addons().stream().map(OrderItemAddonResponse::addonName).toList()) + ")");
            t.addCell(text(name, Element.ALIGN_LEFT));
            t.addCell(text(String.valueOf(item.quantity()), Element.ALIGN_CENTER));
            t.addCell(text(money(unit), Element.ALIGN_RIGHT));
            t.addCell(text(money(unit.multiply(BigDecimal.valueOf(item.quantity()))), Element.ALIGN_RIGHT));
        }
        return t;
    }

    private static PdfPTable billSection(OrderResponse order) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{3, 1});
        t.setWidthPercentage(100);
        t.setSpacingAfter(8);
        PdfPCell title = cell(TINT);
        title.setColspan(2);
        title.addElement(new Paragraph("BILL BREAKDOWN", SECTION));
        t.addCell(title);
        BigDecimal itemTotal = order.items().stream()
                .map(i -> i.price().add(i.addons().stream().map(OrderItemAddonResponse::addonPrice).reduce(BigDecimal.ZERO, BigDecimal::add))
                        .multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        row(t, "Item total", itemTotal);
        if (positive(order.discountAmount())) row(t, "Discount" + (order.coupon() != null ? " (" + order.coupon().code() + ")" : ""), order.discountAmount().negate());
        row(t, "Tax", order.tax());
        row(t, "Restaurant charge", order.restaurantCharge());
        if (order.deliveryType() == null || order.deliveryType() != 1) row(t, "Delivery charge", order.deliveryCharge());
        if (positive(order.platformFee())) row(t, "Platform fee", order.platformFee());
        if (positive(order.driverTipAmount())) row(t, "Delivery partner tip", order.driverTipAmount());

        PdfPCell gl = cell(TINT);
        gl.addElement(new Paragraph("Grand total (incl. GST)", TOTAL));
        gl.setBorderWidthRight(0);
        t.addCell(gl);
        PdfPCell gv = cell(TINT);
        Paragraph v = new Paragraph("Rs. " + money(order.payable()), TOTAL);
        v.setAlignment(Element.ALIGN_RIGHT);
        gv.addElement(v);
        gv.setBorderWidthLeft(0);
        t.addCell(gv);

        PdfPCell pay = cell(Color.WHITE);
        pay.setColspan(2);
        Phrase p = new Phrase();
        p.add(new Chunk("Payment Method: ", LABEL));
        p.add(new Chunk(order.paymentMode() != null ? order.paymentMode() : "", VALUE));
        pay.addElement(new Paragraph(p));
        t.addCell(pay);
        return t;
    }

    private static PdfPTable termsSection(OrderResponse order, String restaurantLicence, String platformLicence, List<String> terms) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = cell(Color.WHITE);
        c.addElement(new Paragraph("Terms & Conditions", TERMS_BOLD));
        int n = 1;
        for (String term : terms) {
            if (term.isBlank()) continue;
            c.addElement(new Paragraph(n++ + ". " + term.trim(), TERMS));
        }
        String restaurantName = order.restaurant() != null ? order.restaurant().name() : "The restaurant";
        boolean hasRestaurant = restaurantLicence != null && !restaurantLicence.isBlank();
        boolean hasPlatform = platformLicence != null && !platformLicence.isBlank();
        if (hasRestaurant || hasPlatform) {
            String line = (hasRestaurant ? restaurantName + " holds FSSAI Lic. No. " + restaurantLicence.trim() + ". " : "")
                    + (hasPlatform ? "PureEats is FSSAI certified as per Lic. No. " + platformLicence.trim() + "." : "");
            c.addElement(new Paragraph(n + ". FSSAI Compliance: " + line.trim(), TERMS));
        }
        t.addCell(c);
        return t;
    }

    private static void row(PdfPTable t, String label, BigDecimal amount) {
        PdfPCell l = new PdfPCell(new Paragraph(label, VALUE));
        l.setBorder(PdfPCell.NO_BORDER);
        l.setPaddingLeft(8);
        l.setPaddingBottom(3);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Paragraph("Rs. " + money(amount), VALUE));
        v.setBorder(PdfPCell.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPaddingRight(8);
        v.setPaddingBottom(3);
        t.addCell(v);
    }

    private static PdfPCell text(String value, int align) {
        PdfPCell c = new PdfPCell(new Paragraph(value, VALUE));
        c.setHorizontalAlignment(align);
        c.setBorderColor(BORDER);
        c.setPadding(6);
        return c;
    }

    private static PdfPCell cell(Color background) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(background);
        c.setBorderColor(BORDER);
        c.setPadding(8);
        return c;
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static String money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
