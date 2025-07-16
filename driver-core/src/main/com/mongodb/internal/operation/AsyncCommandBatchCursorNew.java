package com.mongodb.internal.operation;

import com.mongodb.client.cursor.TimeoutMode;
import com.mongodb.internal.async.AsyncAggregateResponseBatchCursor;
import com.mongodb.internal.async.SingleResultCallback;
import com.mongodb.internal.connection.OperationContext;
import com.mongodb.lang.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonTimestamp;

import java.util.List;

public class AsyncCommandBatchCursorNew<T> implements AsyncAggregateResponseBatchCursor<T> {

    private final TimeoutMode timeoutMode;
    private OperationContext operationContext;

    private AsyncCoreCursor<T> wrapped;

    AsyncCommandBatchCursorNew(
            final TimeoutMode timeoutMode,
            final OperationContext operationContext,
            final AsyncCoreCursor<T> wrapped) {
        this.operationContext = operationContext;
        this.timeoutMode = timeoutMode;
        this.wrapped = wrapped;
    }

    @Override
    public void next(final SingleResultCallback<List<T>> callback) {
        resetTimeout();
        wrapped.next(operationContext, callback);
    }

    @Override
    public void setBatchSize(final int batchSize) {
        wrapped.setBatchSize(batchSize);
    }

    @Override
    public int getBatchSize() {
        return wrapped.getBatchSize();
    }

    @Override
    public boolean isClosed() {
        return wrapped.isClosed();
    }

    @Override
    public void close() {
        operationContext = operationContext.withNewlyStartedTimeout();
        wrapped.close(operationContext);
    }

    @Nullable
    @Override
    public BsonDocument getPostBatchResumeToken() {
        return wrapped.getPostBatchResumeToken();
    }

    @Nullable
    @Override
    public BsonTimestamp getOperationTime() {
        return wrapped.getOperationTime();
    }

    @Override
    public boolean isFirstBatchEmpty() {
        return wrapped.isFirstBatchEmpty();
    }

    @Override
    public int getMaxWireVersion() {
        return wrapped.getMaxWireVersion();
    }

    private void resetTimeout() {
        if (timeoutMode == TimeoutMode.ITERATION) {
            operationContext = operationContext.withNewlyStartedTimeout();
        }
    }

    AsyncCoreCursor<T> getWrapped() {
        return wrapped;
    }
}

