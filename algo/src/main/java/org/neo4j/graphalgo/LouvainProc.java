package org.neo4j.graphalgo;

import org.neo4j.graphalgo.api.*;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.ProcedureConfiguration;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraph;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.graphalgo.core.write.Exporter;
import org.neo4j.graphalgo.core.write.IntArrayTranslator;
import org.neo4j.graphalgo.impl.louvain.LouvainAlgorithm;
import org.neo4j.graphalgo.impl.louvain.ParallelLouvain;
import org.neo4j.graphalgo.impl.louvain.WeightedLouvain;
import org.neo4j.graphalgo.results.LouvainResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import java.util.Map;
import java.util.stream.Stream;

/**
 * modularity based community detection algorithm
 *
 * @author mknblch
 */
public class LouvainProc {

    public static final String CONFIG_CLUSTER_PROPERTY = "writeProperty";
    public static final String DEFAULT_CLUSTER_PROPERTY = "community";
    public static final int DEFAULT_ITERATIONS = 20;

    @Context
    public GraphDatabaseAPI api;

    @Context
    public Log log;

    @Context
    public KernelTransaction transaction;

    @Procedure(value = "algo.louvain", mode = Mode.WRITE)
    @Description("CALL algo.louvain(label:String, relationship:String, " +
            "{weightProperty:'weight', defaultValue:1.0, write: true, writeProperty:'community', concurrency:4}) " +
            "YIELD nodes, communityCount, iterations, loadMillis, computeMillis, writeMillis")
    public Stream<LouvainResult> louvain(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config)
                .overrideNodeLabelOrQuery(label)
                .overrideRelationshipTypeOrQuery(relationship);

        LouvainResult.Builder builder = LouvainResult.builder();

        final HeavyGraph graph;
        try (ProgressTimer timer = builder.timeLoad()) {
            graph = graph(configuration);
        }

        builder.withNodeCount(graph.nodeCount());

        final LouvainAlgorithm louvain = louvain(graph, configuration);

        // evaluation
        try (ProgressTimer timer = builder.timeEval()) {
            louvain.compute();
            builder.withIterations(louvain.getIterations())
                    .withCommunityCount(louvain.getCommunityCount());
        }

        if (configuration.isWriteFlag()) {
            // write back
            builder.timeWrite(() ->
                    write(graph, louvain.getCommunityIds(), configuration));
        }

        return Stream.of(builder.build());
    }

    @Procedure(value = "algo.louvain.stream")
    @Description("CALL algo.louvain.stream(label:String, relationship:String, " +
            "{weightProperty:'propertyName', defaultValue:1.0, concurrency:4) " +
            "YIELD nodeId, community - yields a setId to each node id")
    public Stream<WeightedLouvain.Result> louvainStream(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        final ProcedureConfiguration configuration = ProcedureConfiguration.create(config)
                .overrideNodeLabelOrQuery(label)
                .overrideRelationshipTypeOrQuery(relationship);

        // evaluation
        return louvain(graph(configuration), configuration)
                .compute()
                .resultStream();

    }

    public HeavyGraph graph(ProcedureConfiguration config) {

        final GraphLoader loader = new GraphLoader(api, Pools.DEFAULT)
                .withOptionalLabel(config.getNodeLabelOrQuery())
                .withOptionalRelationshipType(config.getRelationshipOrQuery())
                .withDirection(Direction.BOTH);

        if (config.hasWeightProperty()) {
            return (HeavyGraph) loader
                    .withOptionalRelationshipWeightsFromProperty(
                            config.getWeightProperty(),
                            config.getWeightPropertyDefaultValue(1.0))
                    .load(HeavyGraphFactory.class);
        }

        return (HeavyGraph) loader
                .withoutRelationshipWeights()
                .withoutNodeWeights()
                .withoutNodeProperties()
                .load(HeavyGraphFactory.class);
    }

    public LouvainAlgorithm louvain(HeavyGraph graph, ProcedureConfiguration config) {
        if (config.hasWeightProperty()) {
            return new WeightedLouvain(graph, graph, graph, Pools.DEFAULT, config.getConcurrency(), config.getIterations(DEFAULT_ITERATIONS))
                    .withProgressLogger(ProgressLogger.wrap(log, "WeightedLouvain"))
                    .withTerminationFlag(TerminationFlag.wrap(transaction));
        }
        return new ParallelLouvain(graph, graph, graph, Pools.DEFAULT, config.getConcurrency(), config.getIterations(DEFAULT_ITERATIONS))
                .withProgressLogger(ProgressLogger.wrap(log, "Louvain"))
                .withTerminationFlag(TerminationFlag.wrap(transaction));
    }

    private void write(Graph graph, int[] communities, ProcedureConfiguration configuration) {
        log.debug("Writing results");
        Exporter.of(api, graph)
                .withLog(log)
                .parallel(Pools.DEFAULT, configuration.getConcurrency(), TerminationFlag.wrap(transaction))
                .build()
                .write(
                        configuration.get(CONFIG_CLUSTER_PROPERTY, DEFAULT_CLUSTER_PROPERTY),
                        communities,
                        IntArrayTranslator.INSTANCE
                );
    }
}
