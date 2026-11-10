package com.justjava.ams.core.config;

import com.justjava.ams.accountant.entity.GeneralLedger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneralLedgerSourceTypeSchemaInitializerTest {
    @Test
    void updatesOutdatedConstraintWithOneStatement() {
        JdbcTemplateStub jdbcTemplate = new JdbcTemplateStub("CHECK (source_type <> 'MANUAL_JOURNAL')");
        GeneralLedgerSourceTypeSchemaInitializer initializer =
                new GeneralLedgerSourceTypeSchemaInitializer(jdbcTemplate);

        initializer.run(new DefaultApplicationArguments());

        String allowedValues = Arrays.stream(GeneralLedger.SourceType.values())
                .map(sourceType -> "'" + sourceType.name() + "'")
                .collect(Collectors.joining(", "));
        assertThat(jdbcTemplate.executedStatements).containsExactly(
                "alter table general_ledger drop constraint if exists general_ledger_source_type_check"
                        + ", add constraint general_ledger_source_type_check check (source_type in ("
                        + allowedValues + "))");
    }

    @Test
    void alreadyUpdatedConstraintSkipsAlter() {
        JdbcTemplateStub jdbcTemplate =
                new JdbcTemplateStub("CHECK (source_type IN ('PAYROLL_JOURNAL'))");
        GeneralLedgerSourceTypeSchemaInitializer initializer =
                new GeneralLedgerSourceTypeSchemaInitializer(jdbcTemplate);

        initializer.run(new DefaultApplicationArguments());

        assertThat(jdbcTemplate.executedStatements).isEmpty();
    }

    @Test
    void missingConstraintContinuesWithoutAlteringSchema() {
        JdbcTemplateStub jdbcTemplate = new JdbcTemplateStub(null);
        GeneralLedgerSourceTypeSchemaInitializer initializer =
                new GeneralLedgerSourceTypeSchemaInitializer(jdbcTemplate);

        initializer.run(new DefaultApplicationArguments());

        assertThat(jdbcTemplate.executedStatements).isEmpty();
    }

    @Test
    void databaseFailureDuringAlterFailsStartupWithClearMessage() {
        JdbcTemplateStub jdbcTemplate = new JdbcTemplateStub("CHECK (source_type <> 'MANUAL_JOURNAL')");
        jdbcTemplate.executeFailure =
                new DataAccessResourceFailureException("simulated permission denial or lock timeout");
        GeneralLedgerSourceTypeSchemaInitializer initializer =
                new GeneralLedgerSourceTypeSchemaInitializer(jdbcTemplate);

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not update the General Ledger source-type constraint")
                .hasMessageContaining("application startup aborted")
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    private static final class JdbcTemplateStub extends JdbcTemplate {
        private String constraintDefinition;
        private RuntimeException executeFailure;
        private final List<String> executedStatements = new ArrayList<>();

        private JdbcTemplateStub(String constraintDefinition) {
            this.constraintDefinition = constraintDefinition;
        }

        @Override
        public <T> T query(String sql, ResultSetExtractor<T> resultSetExtractor, Object... args) {
            ResultSet resultSet = mock(ResultSet.class);
            try {
                when(resultSet.next()).thenReturn(constraintDefinition != null);
                if (constraintDefinition != null) {
                    when(resultSet.getString(1)).thenReturn(constraintDefinition);
                }
                return resultSetExtractor.extractData(resultSet);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void execute(String sql) {
            executedStatements.add(sql);
            if (executeFailure != null) {
                throw executeFailure;
            }
        }
    }
}
