package pl.touk.flink.ignite.table;

import org.apache.flink.connector.jdbc.dialect.JdbcDialect;
import org.apache.flink.connector.jdbc.internal.options.JdbcConnectorOptions;
import org.apache.flink.connector.jdbc.table.JdbcRowDataInputFormat;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.connector.ChangelogMode;
import org.apache.flink.table.connector.source.DynamicTableSource;
import org.apache.flink.table.connector.source.InputFormatProvider;
import org.apache.flink.table.connector.source.ScanTableSource;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.RowType;

import java.time.LocalDate;
import java.time.ZoneId;

public class IgniteDynamicTableSource implements ScanTableSource {

    private final JdbcConnectorOptions options;
    private final JdbcDatePartitionReadOptions readOptions;
    private final ResolvedSchema tableSchema;

    public IgniteDynamicTableSource(JdbcConnectorOptions options, JdbcDatePartitionReadOptions readOptions, ResolvedSchema tableSchema) {
        this.options = options;
        this.readOptions = readOptions;
        this.tableSchema = tableSchema;
    }

    @Override
    public ChangelogMode getChangelogMode() {
        return ChangelogMode.insertOnly();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ScanRuntimeProvider getScanRuntimeProvider(ScanContext runtimeProviderContext) {

        final JdbcDialect dialect = options.getDialect();

        String query = dialect.getSelectFromStatement(
                options.getTableName(), tableSchema.getColumnNames().toArray(new String[0]), new String[0]);

        DataType rowDataType = tableSchema.toPhysicalRowDataType();
        final RowType rowType = (RowType) rowDataType.getLogicalType();

        final JdbcRowDataInputFormat.Builder builder = JdbcRowDataInputFormat.builder()
                .setDrivername(options.getDriverName())
                .setDBUrl(options.getDbURL())
                .setUsername(options.getUsername().orElse(null))
                .setPassword(options.getPassword().orElse(null))
                .setQuery(query)
                .setRowConverter(dialect.getRowConverter(rowType))
                .setRowDataTypeInfo(runtimeProviderContext.createTypeInformation(rowDataType));

        if (readOptions != null) {
            LocalDate lowerBound = readOptions.getPartitionLowerBound();
            LocalDate upperBound = readOptions.getPartitionUpperBound();
            ZoneId timezone = readOptions.getTimezone();
            builder.setParametersProvider(
                    new JdbcTimestampBetweenParametersProvider(timezone, lowerBound, upperBound)
            );
            query += " WHERE " +
                    dialect.quoteIdentifier(readOptions.getPartitionColumnName()) +
                    " BETWEEN ? AND ?";
        }

        builder.setQuery(query);
        return InputFormatProvider.of(builder.build());

    }

    @Override
    public DynamicTableSource copy() {
        return new IgniteDynamicTableSource(options, readOptions, tableSchema);
    }

    @Override
    public String asSummaryString() {
        return "Ignite Table Source";
    }

}
