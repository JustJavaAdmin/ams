package com.justjava.ams.accountant.dto;

import java.util.List;

public record PostedJournalPage(
        List<PostedJournalSummary> journals,
        int page,
        int pageSize,
        int totalPages,
        long totalElements) {
}
