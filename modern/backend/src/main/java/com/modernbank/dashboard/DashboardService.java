package com.modernbank.dashboard;

import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    static final int RECENT_LIMIT = 10;

    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    public DashboardSummary summary() {
        return new DashboardSummary(
                repository.countCustomers(),
                repository.countAccounts(),
                repository.totalActiveBalance(),
                repository.recentTransactions(RECENT_LIMIT));
    }
}
