package com.mongodb.internal.operation;

import com.mongodb.ServerCursor;
import com.mongodb.internal.async.AsyncBatchCursor;
import com.mongodb.internal.async.SingleResultCallback;
import com.mongodb.internal.connection.OperationContext;
import com.mongodb.lang.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonTimestamp;

import java.util.List;

public interface AsyncCoreCursor<T> {
    void close(OperationContext operationContext);
    void next(OperationContext operationContext, SingleResultCallback<List<T>> callback);

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


    @Nullable
    BsonDocument getPostBatchResumeToken();

    @Nullable
    BsonTimestamp getOperationTime();

    boolean isFirstBatchEmpty();

    int getMaxWireVersion();


    /**
     * Implementations of {@link AsyncBatchCursor} are allowed to close themselves, see {@link #close()} for more details.
     *
     * @return {@code true} if {@code this} has been closed or has closed itself.
     */
    boolean isClosed();
}
