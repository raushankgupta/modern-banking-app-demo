package com.modernbank.dashboard;

import java.math.BigDecimal;
import java.util.List;

/** Headline figures and recent activity for the dashboard. */
public record DashboardSummary(
        int customerCount,
        int accountCount,
        BigDecimal totalActiveBalance,
        List<RecentTransaction> recentTransactions) {
}
