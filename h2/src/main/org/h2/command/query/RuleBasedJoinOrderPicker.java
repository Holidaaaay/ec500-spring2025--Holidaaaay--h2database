package org.h2.command.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.h2.engine.SessionLocal;
import org.h2.expression.Expression;
import org.h2.expression.ExpressionColumn;
import org.h2.table.TableFilter;


/**
 * Determines the best join order by following rules rather than considering every possible permutation.
 */
public class RuleBasedJoinOrderPicker {
    final SessionLocal session;
    final TableFilter[] filters;

    public RuleBasedJoinOrderPicker(SessionLocal session, TableFilter[] filters) {
        this.session = session;
        this.filters = filters;
    }

    public TableFilter[] bestOrder() {
        System.out.println("join tables applying rule based join order.");
        /** store tables which are not yet scheduled in joining  */
        List<TableFilter> remaining = new ArrayList<>(Arrays.asList(filters));

        /** store the final ordered tables */
        List<TableFilter> ordered = new ArrayList<>();

        /** Map table name to its corresponding TableFilter */
        Map<String, TableFilter> nameToFilter = new HashMap<>();
        for (TableFilter f : filters) {
            nameToFilter.put(f.getTable().getName(), f);
        }

        // Build the join graph from condition expressions
        Map<String, Set<String>> joinGraph = new HashMap<>();
        Expression cond = filters[0].getFullCondition(); // Same for all filters
        buildJoinGraph(cond, joinGraph);

        // Choose the table with the smallest row count as the starting point
        TableFilter start = null;
        long minCount = Long.MAX_VALUE;
        for (TableFilter f : remaining) {
            long count = f.getTable().getRowCountApproximation(session);
            if (count < minCount) {
                minCount = count;
                start = f;
            }
        }
        ordered.add(start);
        remaining.remove(start);

        // Repeatedly find the next joinable table with the smallest row count
        while (!remaining.isEmpty()) {
            // set of ordered tables' names
            Set<String> usedNames = new HashSet<>();
            for (TableFilter f : ordered) {
                usedNames.add(f.getTable().getName());
            }
            List<TableFilter> candidates = new ArrayList<>();
            for (TableFilter f : remaining) {
                String candidateName = f.getTable().getName();
                Set<String> neighbors = joinGraph.getOrDefault(candidateName, Collections.emptySet());
                for (String used : usedNames) {
                    if (neighbors.contains(used)) {
                        candidates.add(f);
                        break;
                    }
                }
            }
            // Pick candidate with smallest row count
            TableFilter next = null;
            long bestCount = Long.MAX_VALUE;
            for (TableFilter c : candidates) {
                long count = c.getTable().getRowCountApproximation(session);
                if (count < bestCount) {
                    bestCount = count;
                    next = c;
                }
            }

            ordered.add(next);
            remaining.remove(next);
        }

        return ordered.toArray(new TableFilter[0]);
    }

    /**
     * builds a join graph from the given condition expression.
     * for example:
     * source SQL:
     *      SELECT * FROM A
     *      JOIN B ON A.id = B.id
     *      JOIN C ON B.id = C.id
     *
     * corresponding joinGraph:
     *      A -> { B },
     *      B -> { A, C },
     *      C -> { B }
     *
     * @param conditions
     * @param graph a map where each table name points to the set of joinable table names
     */
    private void buildJoinGraph(Expression expr, Map<String, Set<String>> graph) {
        if (expr == null) return;
        // if the condition is a.col = b.col
        if (expr instanceof org.h2.expression.condition.Comparison) {
            org.h2.expression.condition.Comparison cmp = (org.h2.expression.condition.Comparison) expr;
            if (cmp.getSubexpressionCount() == 2) {
                Expression left = cmp.getSubexpression(0);
                Expression right = cmp.getSubexpression(1);
                if (left instanceof ExpressionColumn && right instanceof ExpressionColumn) {
                    String leftTable = ((ExpressionColumn) left).getTableName();
                    String rightTable = ((ExpressionColumn) right).getTableName();
                    if (leftTable != null && rightTable != null && !leftTable.equals(rightTable)) {
                        graph.computeIfAbsent(leftTable, k -> new HashSet<>()).add(rightTable);
                        graph.computeIfAbsent(rightTable, k -> new HashSet<>()).add(leftTable);
                    }
                }
            }
        }
}