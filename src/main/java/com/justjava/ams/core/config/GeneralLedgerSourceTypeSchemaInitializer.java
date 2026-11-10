package com.justjava.ams.core.config;

import com.justjava.ams.accountant.entity.GeneralLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeneralLedgerSourceTypeSchemaInitializer implements ApplicationRunner {
    private static final String CONSTRAINT_NAME = "general_ledger_source_type_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // Hibernate ddl-auto=update does not expand existing enum check constraints.
        String definition;
        try {
            definition = jdbcTemplate.query(
                    "select pg_get_constraintdef(oid) from pg_constraint "
                            + "where conrelid = 'general_ledger'::regclass and conname = ?",
                    resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                    CONSTRAINT_NAME);
        } catch (DataAccessException exception) {
            log.error("Could not inspect the General Ledger source-type constraint; startup aborted", exception);
            throw new IllegalStateException(
                    "Could not inspect the General Ledger source-type constraint; application startup aborted",
                    exception);
        }
        if (definition == null) {
            log.warn("PostgreSQL constraint {} was not found; no source-type update is required", CONSTRAINT_NAME);
            return;
        }
        if (definition.contains("'PAYROLL_JOURNAL'")) {
            return;
        }

        String allowedValues = Arrays.stream(GeneralLedger.SourceType.values())
                .map(sourceType -> "'" + sourceType.name() + "'")
                .collect(Collectors.joining(", "));
        String alterConstraint = "alter table general_ledger drop constraint if exists " + CONSTRAINT_NAME
                + ", add constraint " + CONSTRAINT_NAME + " check (source_type in (" + allowedValues + "))";
        try {
            jdbcTemplate.execute(alterConstraint);
        } catch (DataAccessException exception) {
            log.error("Could not update the General Ledger source-type constraint; startup aborted", exception);
            throw new IllegalStateException(
                    "Could not update the General Ledger source-type constraint; application startup aborted",
                    exception);
        }
        log.info("Updated the General Ledger source-type constraint for the current source types");
    }
}
