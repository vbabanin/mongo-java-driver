///*
// * Copyright 2008-present MongoDB, Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *   http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.mongodb.internal.operation;
//
//import com.mongodb.MongoChangeStreamException;
//import com.mongodb.MongoException;
//import com.mongodb.MongoOperationTimeoutException;
//import com.mongodb.client.cursor.TimeoutMode;
//import com.mongodb.internal.TimeoutContext;
//import com.mongodb.internal.binding.ConnectionSource;
import com.mongodb.internal.connection.OperationContext;
//import com.mongodb.internal.binding.ReadBinding;
import com.mongodb.internal.connection.OperationContext;
//import com.mongodb.internal.connection.Connection;
//import com.mongodb.lang.Nullable;
//import org.bson.BsonDocument;
//import org.bson.BsonTimestamp;
//import org.bson.BsonValue;
//import org.bson.RawBsonDocument;
//import org.bson.codecs.Decoder;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.function.Consumer;
//import java.util.function.Function;
//
//import static com.mongodb.assertions.Assertions.assertNotNull;
//import static com.mongodb.internal.operation.ChangeStreamBatchCursorHelper.isResumableError;
//import static com.mongodb.internal.operation.SyncOperationHelper.withReadConnectionSource;
//
///**
// * A change stream cursor that wraps {@link CommandBatchCursor} with automatic resumption capabilities in the event
// * of timeouts or transient errors.
// * <p>
// * Upon encountering a resumable error during {@code hasNext()}, {@code next()}, or {@code tryNext()} calls, the {@link ChangeStreamBatchCursorImpl}
// * attempts to establish a new change stream on the server.
// * </p>
// * If an error occurring during any of these method calls is not resumable, it is immediately propagated to the caller, and the {@link ChangeStreamBatchCursorImpl}
// * is closed and invalidated on the server. Server errors that occur during this invalidation process are not propagated to the caller.
// * <p>
// * A {@link MongoOperationTimeoutException} does not invalidate the {@link ChangeStreamBatchCursorImpl}, but is immediately propagated to the caller.
// * Subsequent method call will attempt to resume operation by establishing a new change stream on the server, without doing {@code getMore}
// * request first.
// * </p>
// */
//final class ChangeStreamBatchCursorImpl<T> extends CommandBatchCursor<RawBsonDocument> {
//    private final ReadBinding binding;
//    private final ChangeStreamOperation<T> changeStreamOperation;
//    private final int maxWireVersion;
//    private final TimeoutContext timeoutContext;
//    private BsonDocument resumeToken;
//    private final AtomicBoolean closed;
//
//    /**
//     * This flag is used to manage change stream resumption logic after a timeout error.
//     * Indicates whether the last {@code hasNext()}, {@code next()}, or {@code tryNext()} call resulted in a {@link MongoOperationTimeoutException}.
//     * If {@code true}, indicates a timeout occurred, prompting an attempt to resume the change stream on the subsequent call.
//     */
//    private boolean lastOperationTimedOut;
//
//    ChangeStreamBatchCursorImpl(
//            final TimeoutMode timeoutMode,
//            final BsonDocument commandCursorDocument,
//            final int batchSize, final long maxTimeMS,
//            final Decoder<T> decoder,
//            @Nullable final BsonValue comment,
//            final ConnectionSource connectionSource,
//            final Connection connection,
//            final ChangeStreamOperation<T> changeStreamOperation,
//            final CommandBatchCursor<RawBsonDocument> wrapped,
//            final ReadBinding binding,
//            @Nullable final BsonDocument resumeToken,
//            final int maxWireVersion) {
//        super(timeoutMode, commandCursorDocument, batchSize, maxTimeMS, decoder, comment, connectionSource, connection);
//
//        this.timeoutContext = binding.getOperationContext().getTimeoutContext();
//        this.changeStreamOperation = changeStreamOperation;
//        this.binding = binding.retain();
//        this.resumeToken = resumeToken;
//        this.maxWireVersion = maxWireVersion;
//        closed = new AtomicBoolean();
//        lastOperationTimedOut = false;
//    }
//
//    @Override
//    public boolean hasNext() {
//        return resumeableOperation(commandBatchCursor -> {
//            try {
//                return commandBatchCursor.hasNext();
//            } finally {
//                cachePostBatchResumeToken(commandBatchCursor);
//            }
//        });
//    }
//
//    @Override
//    public List<T> next() {
//        return resumeableOperation(commandBatchCursor -> {
//            try {
//                List<RawBsonDocument> next = commandBatchCursor.next();
//                return convertAndProduceLastId(next, changeStreamOperation.getDecoder(),
//                        lastId -> resumeToken = lastId);
//            } finally {
//                cachePostBatchResumeToken(commandBatchCursor);
//            }
//        });
//    }
//
//    @Override
//    public List<T> tryNext() {
//        return resumeableOperation(commandBatchCursor -> {
//            try {
//                List<RawBsonDocument> tryNext = commandBatchCursor.tryNext();
//                return tryNext == null ? null
//                        : convertAndProduceLastId(tryNext, changeStreamOperation.getDecoder(), lastId -> resumeToken = lastId);
//            } finally {
//                cachePostBatchResumeToken(commandBatchCursor);
//            }
//        });
//    }
//
//    @Override
//    public void close() {
//        if (!closed.getAndSet(true)) {
//            super.operationContext = operationContext.withNewlyStartedTimeout(); // maybe won't be needed as CommandBatchCursor will reset it.
//            super.close();
//            binding.release();
//        }
//    }
//
//    @Override
//    public void remove() {
//        throw new UnsupportedOperationException("Not implemented!");
//    }
//
//
//    @Override
//    public BsonTimestamp getOperationTime() {
//        return changeStreamOperation.getStartAtOperationTime();
//    }
//
//
//    @Override
//    public int getMaxWireVersion() {
//        return maxWireVersion;
//    }
//
//    private void cachePostBatchResumeToken(final AggregateResponseBatchCursor<RawBsonDocument> commandBatchCursor) {
//        if (commandBatchCursor.getPostBatchResumeToken() != null) {
//            resumeToken = commandBatchCursor.getPostBatchResumeToken();
//        }
//    }
//
//    /**
//     * @param lastIdConsumer Is {@linkplain Consumer#accept(Object) called} iff {@code rawDocuments} is successfully converted
//     *                       and the returned {@link List} is neither {@code null} nor {@linkplain List#isEmpty() empty}.
//     */
//    static <T> List<T> convertAndProduceLastId(final List<RawBsonDocument> rawDocuments,
//                                               final Decoder<T> decoder,
//                                               final Consumer<BsonDocument> lastIdConsumer) {
//        List<T> results = new ArrayList<>();
//        for (RawBsonDocument rawDocument : assertNotNull(rawDocuments)) {
//            if (!rawDocument.containsKey("_id")) {
//                throw new MongoChangeStreamException("Cannot provide resume functionality when the resume token is missing.");
//            }
//            results.add(rawDocument.decode(decoder));
//        }
//        if (!rawDocuments.isEmpty()) {
//            lastIdConsumer.accept(rawDocuments.get(rawDocuments.size() - 1).getDocument("_id"));
//        }
//        return results;
//    }
//
//    <R> R resumeableOperation(final Function<AggregateResponseBatchCursor<RawBsonDocument>, R> function) {
//        timeoutContext.resetTimeoutIfPresent();
//        try {
//            R result = execute(function);
//            lastOperationTimedOut = false;
//            return result;
//        } catch (Throwable exception) {
//            lastOperationTimedOut = isTimeoutException(exception);
//            throw exception;
//        }
//    }
//
//    private <R> R execute(final Function<AggregateResponseBatchCursor<RawBsonDocument>, R> function) {
//        boolean shouldBeResumed = hasPreviousNextTimedOut();
//        while (true) {
//            if (shouldBeResumed) {
//                resumeChangeStream();
//            }
//            try {
//                return function.apply(wrapped);
//            } catch (Throwable t) {
//                if (!isResumableError(t, maxWireVersion)) {
//                    throw MongoException.fromThrowableNonNull(t);
//                }
//                shouldBeResumed = true;
//            }
//        }
//    }
//
//    private void resumeChangeStream() {
//        wrapped.close();
//
//        withReadConnectionSource(binding, source -> {
//            changeStreamOperation.setChangeStreamOptionsForResume(resumeToken, source.getServerDescription().getMaxWireVersion());
//            return null;
//        });
//        wrapped = ((ChangeStreamBatchCursorImpl<T>) changeStreamOperation.execute(binding)).getWrapped();
//        binding.release(); // release the new change stream batch cursor's reference to the binding
//    }
//
//    private boolean hasPreviousNextTimedOut() {
//        return lastOperationTimedOut && !closed.get();
//    }
//
//    private static boolean isTimeoutException(final Throwable exception) {
//        return exception instanceof MongoOperationTimeoutException;
//    }
//}
