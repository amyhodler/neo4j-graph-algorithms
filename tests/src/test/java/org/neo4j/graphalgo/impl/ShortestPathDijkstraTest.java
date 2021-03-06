package org.neo4j.graphalgo.impl;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.huge.HugeGraphFactory;
import org.neo4j.graphalgo.core.lightweight.LightGraphFactory;
import org.neo4j.graphalgo.core.neo4jview.GraphViewFactory;
import org.neo4j.graphalgo.core.utils.RawValues;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.kernel.api.exceptions.KernelException;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class ShortestPathDijkstraTest {

    // https://en.wikipedia.org/wiki/Shortest_path_problem#/media/File:Shortest_path_with_direct_weights.svg
    private static final String DB_CYPHER = "" +
            "CREATE (a:Label1 {name:\"a\"})\n" +
            "CREATE (b:Label1 {name:\"b\"})\n" +
            "CREATE (c:Label1 {name:\"c\"})\n" +
            "CREATE (d:Label1 {name:\"d\"})\n" +
            "CREATE (e:Label1 {name:\"e\"})\n" +
            "CREATE (f:Label1 {name:\"f\"})\n" +
            "CREATE\n" +
            "  (a)-[:TYPE1 {cost:4}]->(b),\n" +
            "  (a)-[:TYPE1 {cost:2}]->(c),\n" +
            "  (b)-[:TYPE1 {cost:5}]->(c),\n" +
            "  (b)-[:TYPE1 {cost:10}]->(d),\n" +
            "  (c)-[:TYPE1 {cost:3}]->(e),\n" +
            "  (d)-[:TYPE1 {cost:11}]->(f),\n" +
            "  (e)-[:TYPE1 {cost:4}]->(d),\n" +
            "  (a)-[:TYPE2 {cost:1}]->(d),\n" +
            "  (b)-[:TYPE2 {cost:1}]->(f)\n";

    // https://www.cise.ufl.edu/~sahni/cop3530/slides/lec326.pdf
    // without the additional 14 edge
    private static final String DB_CYPHER2 = "" +
            "CREATE (n1:Label2 {name:\"1\"})\n" +
            "CREATE (n2:Label2 {name:\"2\"})\n" +
            "CREATE (n3:Label2 {name:\"3\"})\n" +
            "CREATE (n4:Label2 {name:\"4\"})\n" +
            "CREATE (n5:Label2 {name:\"5\"})\n" +
            "CREATE (n6:Label2 {name:\"6\"})\n" +
            "CREATE (n7:Label2 {name:\"7\"})\n" +
            "CREATE\n" +
            "  (n1)-[:TYPE2 {cost:6}]->(n2),\n" +
            "  (n1)-[:TYPE2 {cost:2}]->(n3),\n" +
            "  (n1)-[:TYPE2 {cost:16}]->(n4),\n" +
            "  (n2)-[:TYPE2 {cost:4}]->(n5),\n" +
            "  (n2)-[:TYPE2 {cost:5}]->(n4),\n" +
            "  (n3)-[:TYPE2 {cost:7}]->(n2),\n" +
            "  (n3)-[:TYPE2 {cost:3}]->(n5),\n" +
            "  (n3)-[:TYPE2 {cost:8}]->(n6),\n" +
            "  (n4)-[:TYPE2 {cost:7}]->(n3),\n" +
            "  (n5)-[:TYPE2 {cost:4}]->(n4),\n" +
            "  (n5)-[:TYPE2 {cost:10}]->(n7),\n" +
            "  (n6)-[:TYPE2 {cost:1}]->(n7)\n";

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{HeavyGraphFactory.class, "HeavyGraphFactory"},
                new Object[]{LightGraphFactory.class, "LightGraphFactory"},
                new Object[]{GraphViewFactory.class, "GraphViewFactory"},
                new Object[]{HugeGraphFactory.class, "HugeGraphFactory"}
        );
    }

    @ClassRule
    public static final ImpermanentDatabaseRule DB = new ImpermanentDatabaseRule();

    @BeforeClass
    public static void setupGraph() throws KernelException {
        DB.execute(DB_CYPHER).close();
        DB.execute(DB_CYPHER2).close();
    }

    private Class<? extends GraphFactory> graphImpl;



    public ShortestPathDijkstraTest(
            Class<? extends GraphFactory> graphImpl,
            String nameIgnoredOnlyForTestName) {
        this.graphImpl = graphImpl;
    }

    @Test
    public void test1() throws Exception {
        final Label label = Label.label("Label1");
        RelationshipType type = RelationshipType.withName("TYPE1");

        ShortestPath expected = expected(label, type,
                "name", "a",
                "name", "c",
                "name", "e",
                "name", "d",
                "name", "f");
        long[] nodeIds = expected.nodeIds;

        final Graph graph = new GraphLoader(DB)
                .withLabel(label)
                .withRelationshipType("TYPE1")
                .withRelationshipWeightsFromProperty("cost", Double.MAX_VALUE)
                .withDirection(Direction.OUTGOING)
                .load(graphImpl);

        final ShortestPathDijkstra shortestPathDijkstra = new ShortestPathDijkstra(graph);
        shortestPathDijkstra.compute(nodeIds[0], nodeIds[nodeIds.length - 1], Direction.OUTGOING);
        final long[] path = Arrays.stream(shortestPathDijkstra.getFinalPath().toArray()).mapToLong(graph::toOriginalNodeId).toArray();

        assertEquals(expected.weight, shortestPathDijkstra.getTotalCost(), 0.1);
        assertArrayEquals(nodeIds, path);
    }

    @Test
    public void test2() throws Exception {
        final Label label = Label.label("Label2");
        RelationshipType type = RelationshipType.withName("TYPE2");
        ShortestPath expected = expected(label, type,
                "name", "1",
                "name", "3",
                "name", "6",
                "name", "7");
        long[] nodeIds = expected.nodeIds;

        final Graph graph = new GraphLoader(DB)
                .withLabel(label)
                .withRelationshipType("TYPE2")
                .withRelationshipWeightsFromProperty("cost", Double.MAX_VALUE)
                .withDirection(Direction.OUTGOING)
                .load(graphImpl);

        final ShortestPathDijkstra shortestPathDijkstra = new ShortestPathDijkstra(graph);
        shortestPathDijkstra.compute(nodeIds[0], nodeIds[nodeIds.length - 1], Direction.OUTGOING);
        final long[] path = Arrays.stream(shortestPathDijkstra.getFinalPath().toArray()).mapToLong(graph::toOriginalNodeId).toArray();

        assertEquals(expected.weight, shortestPathDijkstra.getTotalCost(), 0.1);
        assertArrayEquals(nodeIds, path);
    }

    @Test
    public void testResultStream() throws Exception {
        final Label label = Label.label("Label1");
        RelationshipType type = RelationshipType.withName("TYPE1");
        ShortestPath expected = expected(label, type,
                "name", "a",
                "name", "c",
                "name", "e",
                "name", "d",
                "name", "f");
        final long head = expected.nodeIds[0], tail = expected.nodeIds[expected.nodeIds.length - 1];

        final Graph graph = new GraphLoader(DB)
                .withLabel(label)
                .withRelationshipType("TYPE1")
                .withRelationshipWeightsFromProperty("cost", Double.MAX_VALUE)
                .withDirection(Direction.OUTGOING)
                .load(graphImpl);

        final ShortestPathDijkstra shortestPathDijkstra = new ShortestPathDijkstra(graph);
        Stream<ShortestPathDijkstra.Result> resultStream = shortestPathDijkstra
                .compute(head, tail, Direction.OUTGOING)
                .resultStream();

        assertEquals(expected.weight, shortestPathDijkstra.getTotalCost(), 0.1);
        assertEquals(expected.nodeIds.length, resultStream.count());
    }

    private static ShortestPath expected(
            Label label,
            RelationshipType type,
            String... kvPairs) {
        return DB.executeAndCommit(db -> {
            double weight = 0.0;
            Node prev = null;
            long[] nodeIds = new long[kvPairs.length / 2];
            for (int i = 0; i < nodeIds.length; i++) {
                Node current = db.findNode(label, kvPairs[2*i], kvPairs[2*i + 1]);
                long id = current.getId();
                nodeIds[i] = id;
                if (prev != null) {
                    for (Relationship rel : prev.getRelationships(type, Direction.OUTGOING)) {
                        if (rel.getEndNodeId() == id) {
                            double cost = RawValues.extractValue(
                                    rel.getProperty("cost"),
                                    0.0);
                            weight += cost;
                        }
                    }
                }
                prev = current;
            }

            return new ShortestPath(nodeIds, weight);
        });
    }

    private static final class ShortestPath {
        private final long[] nodeIds;
        private final double weight;

        private ShortestPath(long[] nodeIds, double weight) {
            this.nodeIds = nodeIds;
            this.weight = weight;
        }
    }
}
