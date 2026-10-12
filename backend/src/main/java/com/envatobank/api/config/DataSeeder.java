package com.envatobank.api.config;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.envatobank.api.account.Account;
import com.envatobank.api.account.AccountRepository;
import com.envatobank.api.account.AccountStatus;
import com.envatobank.api.account.AccountType;
import com.envatobank.api.account.CurrencyCode;
import com.envatobank.api.audit.AuditService;
import com.envatobank.api.crypto.CryptoService;
import com.envatobank.api.customer.Customer;
import com.envatobank.api.customer.CustomerRepository;
import com.envatobank.api.customer.CustomerStatus;
import com.envatobank.api.customer.Role;
import com.envatobank.api.ledger.LedgerService;

/**
 * Development/demo data. Idempotent: every record is only created when the
 * matching customer does not already exist, so it is safe to restart.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final long AYSE_TRY_OPENING_MINOR = 1_000_000L; // 10.000,00 TRY
    private static final long AYSE_USD_OPENING_MINOR = 100_000L;   // 1.000,00 USD
    private static final long MEHMET_TRY_OPENING_MINOR = 500_000L; // 5.000,00 TRY

    private final SeedProperties seedProperties;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CryptoService crypto;
    private final PasswordEncoder passwordEncoder;
    private final LedgerService ledgerService;
    private final AuditService auditService;

    public DataSeeder(SeedProperties seedProperties, CustomerRepository customers, AccountRepository accounts,
            CryptoService crypto, PasswordEncoder passwordEncoder, LedgerService ledgerService,
            AuditService auditService) {
        this.seedProperties = seedProperties;
        this.customers = customers;
        this.accounts = accounts;
        this.crypto = crypto;
        this.passwordEncoder = passwordEncoder;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedProperties.enabled()) {
            log.info("Data seeding disabled (app.seed.enabled=false).");
            return;
        }
        if (customers.findByEmailHash(crypto.blindIndex("admin@envatobank.local")).isPresent()) {
            log.info("Seed data already present; skipping.");
            return;
        }
        log.info("Seeding demo data...");

        findOrCreateCustomer("admin@envatobank.local", "Ada", "Yonetici", "10000000001", "+905000000001",
                Role.ADMIN, "Admin!2345");
        findOrCreateCustomer("employee@envatobank.local", "Efe", "Memur", "10000000002", "+905000000002",
                Role.EMPLOYEE, "Employ!2345");

        Customer bank = findOrCreateCustomer("bank.internal@envatobank.local", "Envato", "Bank",
                "19999999999", "+900000000000", Role.EMPLOYEE, randomToken());
        Account bankTry = ensureAccount(bank, CurrencyCode.TRY, AccountType.CHECKING);
        Account bankUsd = ensureAccount(bank, CurrencyCode.USD, AccountType.CHECKING);

        Customer ayse = findOrCreateCustomer("ayse@example.com", "Ayse", "Yilmaz", "20000000001",
                "+905111111111", Role.CUSTOMER, "Demo!2345");
        Account ayseTry = ensureAccount(ayse, CurrencyCode.TRY, AccountType.CHECKING);
        Account ayseUsd = ensureAccount(ayse, CurrencyCode.USD, AccountType.SAVINGS);
        ledgerService.postTransfer(UUID.randomUUID(), null, bankTry, ayseTry, AYSE_TRY_OPENING_MINOR,
                "Initial deposit (seed)");
        ledgerService.postTransfer(UUID.randomUUID(), null, bankUsd, ayseUsd, AYSE_USD_OPENING_MINOR,
                "Initial deposit (seed)");

        Customer mehmet = findOrCreateCustomer("mehmet@example.com", "Mehmet", "Demir", "20000000002",
                "+905222222222", Role.CUSTOMER, "Demo!2345");
        Account mehmetTry = ensureAccount(mehmet, CurrencyCode.TRY, AccountType.CHECKING);
        ledgerService.postTransfer(UUID.randomUUID(), null, bankTry, mehmetTry, MEHMET_TRY_OPENING_MINOR,
                "Initial deposit (seed)");

        auditService.record("SEED_INITIALIZED", null, "SYSTEM", "System", "seed", Map.of(
                "customers", 5,
                "admin", "admin@envatobank.local",
                "employee", "employee@envatobank.local"));

        log.info("Seed complete. Demo logins: admin@envatobank.local / employee@envatobank.local / "
                + "ayse@example.com / mehmet@example.com");
    }

    private Customer findOrCreateCustomer(String email, String firstName, String lastName, String nationalId,
            String phone, Role role, String rawPassword) {
        String emailHash = crypto.blindIndex(email);
        return customers.findByEmailHash(emailHash).orElseGet(() -> {
            Instant now = Instant.now();
            Customer customer = new Customer();
            customer.setCustomerNo(generateCustomerNo());
            customer.setFirstName(firstName);
            customer.setLastName(lastName);
            customer.setEmailEncrypted(crypto.encrypt(email));
            customer.setEmailHash(emailHash);
            customer.setNationalIdEncrypted(crypto.encrypt(nationalId));
            customer.setNationalIdHash(crypto.blindIndex(nationalId));
            customer.setPhoneEncrypted(crypto.encrypt(phone));
            customer.setPasswordHash(passwordEncoder.encode(rawPassword));
            customer.setRole(role);
            customer.setStatus(CustomerStatus.ACTIVE);
            customer.setCreatedAt(now);
            customer.setUpdatedAt(now);
            return customers.save(customer);
        });
    }

    private Account ensureAccount(Customer owner, CurrencyCode currency, AccountType type) {
        List<Account> existing = accounts.findByCustomerIdOrderByCreatedAtAsc(owner.getId());
        return existing.stream()
                .filter(a -> a.getCurrency() == currency && a.getType() == type)
                .min(Comparator.comparing(Account::getCreatedAt))
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    Account account = new Account();
                    account.setAccountNo(generateAccountNo());
                    account.setCustomerId(owner.getId());
                    account.setCurrency(currency);
                    account.setType(type);
                    account.setStatus(AccountStatus.ACTIVE);
                    account.setBalanceMinor(0L);
                    account.setCreatedAt(now);
                    account.setUpdatedAt(now);
                    return accounts.save(account);
                });
    }

    private String generateCustomerNo() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("C");
            for (int i = 0; i < 10; i++) {
                sb.append(ThreadLocalRandom.current().nextInt(10));
            }
            String candidate = sb.toString();
            if (customers.findByCustomerNo(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a customer number");
    }

    private String generateAccountNo() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("TR");
            for (int i = 0; i < 24; i++) {
                sb.append(ThreadLocalRandom.current().nextInt(10));
            }
            String candidate = sb.toString();
            if (!accounts.existsByAccountNo(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate an account number");
    }

    private String randomToken() {
        return "seed-" + UUID.randomUUID();
    }
}
