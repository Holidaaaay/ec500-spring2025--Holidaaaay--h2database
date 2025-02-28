/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.table;

import java.util.ArrayList;

import org.h2.api.ErrorCode;
import org.h2.command.QueryScope;
import org.h2.command.query.Query;
import org.h2.engine.SessionLocal;
import org.h2.expression.ExpressionVisitor;
import org.h2.expression.Parameter;
import org.h2.index.QueryExpressionIndex;
import org.h2.index.RegularQueryExpressionIndex;
import org.h2.message.DbException;
import org.h2.util.StringUtils;

/**
 * A derived table.
 */
public final class DerivedTable extends QueryExpressionTable {

    private final String querySQL;

    private final Query topQuery;

    private final ArrayList<Parameter> originalParameters;

    /**
     * Create a derived table out of the given query.
     *
     * @param session the session
     * @param name the view name
     * @param columnTemplates column templates, or {@code null}
     * @param query the initialized query
     * @param topQuery the top level query
     */
    public DerivedTable(SessionLocal session, String name, Column[] columnTemplates, Query query, Query topQuery) {
        super(session.getDatabase().getMainSchema(), 0, name);
        setTemporary(true);
        this.topQuery = topQuery;
        query.prepareExpressions();
        try {
            this.querySQL = query.getPlanSQL(DEFAULT_SQL_FLAGS);
            originalParameters = query.getParameters();
            tables = new ArrayList<>(query.getTables());
            setColumns(initColumns(session, columnTemplates, query, true, false));
            viewQuery = query;
        } catch (DbException e) {
            if (e.getErrorCode() == ErrorCode.COLUMN_ALIAS_IS_NOT_SPECIFIED_1) {
                throw e;
            }
            e.addSQL(getCreateSQL());
            throw e;
        }
    }

    @Override
    protected QueryExpressionIndex createIndex(SessionLocal session, int[] masks) {
        return new RegularQueryExpressionIndex(this, querySQL, originalParameters, session, masks);
    }

    @Override
    public boolean isQueryComparable() {
        return super.isQueryComparable()
            && (topQuery == null || topQuery.isEverything(ExpressionVisitor.QUERY_COMPARABLE_VISITOR));
    }

    @Override
    public boolean canDrop() {
        return false;
    }

    @Override
    public TableType getTableType() {
        return null;
    }

    @Override
    public Query getTopQuery() {
        return topQuery;
    }

    @Override
    public String getCreateSQL() {
        return null;
    }

    @Override
    public StringBuilder getSQL(StringBuilder builder, int sqlFlags) {
        return StringUtils.indent(builder.append("(\n"), querySQL, 4, true).append(')');
    }

    @Override
    public QueryScope getQueryScope() {
        return viewQuery.getOuterQueryScope();
    }

}
