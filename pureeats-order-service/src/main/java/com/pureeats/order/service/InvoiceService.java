package com.pureeats.order.service;

import com.pureeats.order.dto.OrderItemAddonResponse;
import com.pureeats.order.dto.OrderItemResponse;
import com.pureeats.order.dto.OrderResponse;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** Renders an {@link OrderResponse} as a simple one-page PDF invoice - the same fields the customer app's order-details bill summary shows, so the two never disagree. */
@Service
public class InvoiceService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font MUTED_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, java.awt.Color.GRAY);

    public byte[] render(OrderResponse order) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("PureEats", TITLE_FONT));
            document.add(new Paragraph("Tax Invoice", HEADING_FONT));
            document.add(spacer());

            document.add(new Paragraph("Order #" + order.uniqueOrderId(), HEADING_FONT));
            document.add(new Paragraph(order.createdAt().format(DATE_FORMAT), MUTED_FONT));
            document.add(new Paragraph(order.restaurant().name(), BODY_FONT));
            document.add(spacer());

            document.add(new Paragraph("Delivery address", HEADING_FONT));
            document.add(new Paragraph(order.address(), BODY_FONT));
            document.add(spacer());

            document.add(itemsTable(order));
            document.add(spacer());

            document.add(billSummaryTable(order));
            document.add(spacer());

            document.add(new Paragraph("Payment method: " + order.paymentMode(), BODY_FONT));
            if (order.coupon() != null) {
                document.add(new Paragraph("Coupon applied: " + order.coupon().name() + " (" + order.coupon().code() + ")", BODY_FONT));
            }

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate invoice PDF for order " + order.id(), e);
        }
    }

    private PdfPTable itemsTable(OrderResponse order) {
        PdfPTable table = new PdfPTable(new float[]{4, 1, 1.2f, 1.2f});
        table.setWidthPercentage(100);
        addHeaderCell(table, "Item");
        addHeaderCell(table, "Qty");
        addHeaderCell(table, "Price");
        addHeaderCell(table, "Amount");

        for (OrderItemResponse item : order.items()) {
            BigDecimal addonTotal = item.addons().stream().map(OrderItemAddonResponse::addonPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal lineTotal = item.price().add(addonTotal).multiply(BigDecimal.valueOf(item.quantity()));
            String name = item.name() + addonSuffix(item);
            addBodyCell(table, name);
            addBodyCell(table, String.valueOf(item.quantity()));
            addBodyCell(table, formatCurrency(item.price().add(addonTotal)));
            addBodyCell(table, formatCurrency(lineTotal));
        }
        return table;
    }

    private String addonSuffix(OrderItemResponse item) {
        if (item.addons().isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" (");
        for (int i = 0; i < item.addons().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(item.addons().get(i).addonName());
        }
        return sb.append(')').toString();
    }

    private PdfPTable billSummaryTable(OrderResponse order) {
        PdfPTable table = new PdfPTable(new float[]{3, 1});
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);

        addSummaryRow(table, "Item total", order.total());
        if (order.discountAmount() != null && order.discountAmount().signum() > 0) {
            addSummaryRow(table, "Discount", order.discountAmount().negate());
        }
        addSummaryRow(table, "Tax", order.tax());
        addSummaryRow(table, "Restaurant charge", order.restaurantCharge());
        addSummaryRow(table, "Delivery charge", order.deliveryCharge());
        if (order.platformFee() != null && order.platformFee().signum() > 0) {
            addSummaryRow(table, "Platform fee", order.platformFee());
        }
        if (order.driverTipAmount() != null && order.driverTipAmount().signum() > 0) {
            addSummaryRow(table, "Delivery tip", order.driverTipAmount());
        }

        PdfPCell grandLabel = new PdfPCell(new Paragraph("Grand total", HEADING_FONT));
        grandLabel.setBorder(PdfPCell.TOP);
        table.addCell(grandLabel);
        PdfPCell grandValue = new PdfPCell(new Paragraph(formatCurrency(order.payable()), HEADING_FONT));
        grandValue.setBorder(PdfPCell.TOP);
        grandValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(grandValue);
        return table;
    }

    private void addSummaryRow(PdfPTable table, String label, BigDecimal amount) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, BODY_FONT));
        labelCell.setBorder(PdfPCell.NO_BORDER);
        table.addCell(labelCell);
        PdfPCell valueCell = new PdfPCell(new Paragraph(formatCurrency(amount), BODY_FONT));
        valueCell.setBorder(PdfPCell.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, HEADING_FONT));
        cell.setBackgroundColor(new java.awt.Color(245, 245, 245));
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text) {
        table.addCell(new PdfPCell(new Paragraph(text, BODY_FONT)));
    }

    private Paragraph spacer() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(4);
        return p;
    }

    private String formatCurrency(BigDecimal amount) {
        return "Rs. " + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
