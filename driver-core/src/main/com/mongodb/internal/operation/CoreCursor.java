package com.mongodb.internal.operation;

import com.mongodb.ServerAddress;
import com.mongodb.ServerCursor;
import com.mongodb.internal.connection.OperationContext;
import com.mongodb.lang.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonTimestamp;

import java.util.List;

interface CoreCursor<T> {
    void close(OperationContext operationContext);

    boolean hasNext(OperationContext operationContext);

    List<T> next(OperationContext operationContext);

    /**
     * A special {@code next()} case that returns the next batch if available or null.
     *
     * <p>Tailable cursors are an example where this is useful. A call to {@code tryNext()} may return null, but in the future calling
     * {@code tryNext()} would return a new batch if a document had been added to the capped collection.</p>
     *
     * @return the next batch if available or null.
     * @mongodb.driver.manual reference/glossary/#term-tailable-cursor Tailable Cursor
     */
    @Nullable
    List<T> tryNext(OperationContext operationContext);


    int available();

    /**
     * Sets the batch size to use when requesting the next batch.  This is the number of documents to request in the next batch.
     *
     * @param batchSize the non-negative batch size.  0 means to use the server default.
     */
    void setBatchSize(int batchSize);

    /**
     * Gets the batch size to use when requesting the next batch.  This is the number of documents to request in the next batch.
     *
     * @return the non-negative batch size.  0 means to use the server default.
     */
    int getBatchSize();

    @Nullable
    ServerCursor getServerCursor();

    ServerAddress getServerAddress();

    @Nullable
    BsonDocument getPostBatchResumeToken();

    @Nullable
    BsonTimestamp getOperationTime();

    boolean isFirstBatchEmpty();

    int getMaxWireVersion();
}

