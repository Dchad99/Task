package com.dcdev.pt.config;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records every statement that reaches the JDBC driver: its SQL text and the
 * values bound to its parameters. Lets tests assert on <em>how</em> input
 * reached the database (bound, never inlined into the SQL), and on batching,
 * rather than only on the result.
 *
 * <p>Registered on the proxied {@code DataSource} in
 * {@link TestConfigToCountSqlQueries} and reset before every test by
 * {@link IntegrationTestBase}. Database Rider loads fixtures through the same
 * DataSource after that reset, so a test that inspects statements calls
 * {@link #reset()} again right before the action it checks.
 */
public class SqlStatementRecorder implements QueryExecutionListener {

    /**
     * One JDBC execution. {@code parameterSets} has one entry per row of a batch
     * (a single entry for a non-batched statement), each holding the bound values
     * in the order they were set.
     */
    public record RecordedStatement(String sql, List<List<Object>> parameterSets) {

        public boolean isInsert() {
            return sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("insert");
        }

        public boolean touchesTable(String table) {
            return sql.toLowerCase(Locale.ROOT).contains(table.toLowerCase(Locale.ROOT));
        }

        /** All bound values of this execution, across every batch entry. */
        public List<Object> parameters() {
            return parameterSets.stream().flatMap(List::stream).toList();
        }
    }

    private final List<RecordedStatement> statements = new CopyOnWriteArrayList<>();

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // Recording after execution is enough: only what actually ran matters.
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        for (QueryInfo queryInfo : queryInfoList) {
            List<List<Object>> parameterSets = new ArrayList<>();
            for (List<ParameterSetOperation> operations : queryInfo.getParametersList()) {
                List<Object> values = new ArrayList<>();
                for (ParameterSetOperation operation : operations) {
                    values.add(boundValue(operation));
                }
                parameterSets.add(values);
            }
            statements.add(new RecordedStatement(queryInfo.getQuery(), parameterSets));
        }
    }

    public void reset() {
        statements.clear();
    }

    public List<RecordedStatement> statements() {
        return new ArrayList<>(statements);
    }

    /** {@code setXxx(parameterIndex, value, ...)}: the value is the second argument; setNull binds null. */
    private static Object boundValue(ParameterSetOperation operation) {
        if (ParameterSetOperation.isSetNullParameterOperation(operation)) {
            return null;
        }
        Object[] args = operation.getArgs();
        return args != null && args.length > 1 ? args[1] : null;
    }
}
