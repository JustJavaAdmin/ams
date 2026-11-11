package com.justjava.ams.einvoicing.nrs.aggregator;

import com.fasterxml.jackson.databind.JsonNode;
import com.justjava.ams.einvoicing.nrs.aggregator.dto.AggregatorRequests;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nrs/aggregator")
@RequiredArgsConstructor
public class RemitaAggregatorController {
    private final RemitaAggregatorClient client;

    @PostMapping("/merchants/link")
    public JsonNode linkMerchant(@Valid @RequestBody AggregatorRequests.MerchantLink request) {
        return client.linkMerchant(request);
    }

    @PostMapping("/invoices/process")
    public JsonNode processInvoice(@Valid @RequestBody AggregatorRequests.ProcessInvoice request) {
        return client.processInvoice(request);
    }

    @PostMapping("/invoices/lookup")
    public JsonNode lookupInvoice(@Valid @RequestBody AggregatorRequests.InvoiceLookup request) {
        return client.lookupInvoice(request);
    }

    @PostMapping("/payments/notify")
    public JsonNode notifyPayment(@Valid @RequestBody AggregatorRequests.PaymentNotification request) {
        return client.notifyPayment(request);
    }

    @PutMapping("/merchants")
    public JsonNode updateMerchant(@Valid @RequestBody AggregatorRequests.MerchantUpdate request) {
        return client.updateMerchant(request);
    }

    @PutMapping("/service-mode")
    public JsonNode updateServiceMode(@Valid @RequestBody AggregatorRequests.ServiceModeUpdate request) {
        return client.updateServiceMode(request);
    }
}
