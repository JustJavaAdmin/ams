package com.justjava.ams.accountant.dto;

import java.util.List;

public record PostedJournalDetail(
        PostedJournalSummary journal,
        List<PostedJournalLine> lines) {
}
