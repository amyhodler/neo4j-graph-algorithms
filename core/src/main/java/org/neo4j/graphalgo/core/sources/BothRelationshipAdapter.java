package org.neo4j.graphalgo.core.sources;

import org.neo4j.graphalgo.api.*;
import org.neo4j.graphalgo.core.utils.container.RelationshipContainer;

/**
 * Adapter class for RelationshipContainer -> BothRelationIterator
 *
 * @author mknblch
 */
public class BothRelationshipAdapter implements BothRelationshipIterator {

    private final RelationshipContainer container;

    public BothRelationshipAdapter(RelationshipContainer container) {
        this.container = container;
    }

    @Override
    public void forEachRelationship(int nodeId, RelationshipConsumer consumer) {
        container.forEach(nodeId, consumer);
    }
}
