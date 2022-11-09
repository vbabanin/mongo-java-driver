/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.client.model.expressions;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;

import java.util.function.BinaryOperator;
import java.util.function.Function;

final class MqlExpression<T extends Expression>
        implements Expression, BooleanExpression, IntegerExpression, NumberExpression,
        StringExpression, DateExpression, DocumentExpression, ArrayExpression<T> {

    private final Function<CodecRegistry, BsonValue> fn;

    MqlExpression(final Function<CodecRegistry, BsonValue> fn) {
        this.fn = fn;
    }

    /**
     * Exposes the evaluated BsonValue so that expressions may be used in
     * aggregations. Non-public, as it is intended to be used only by the
     * {@link MqlExpressionCodec}.
     */
    BsonValue toBsonValue(final CodecRegistry codecRegistry) {
        return fn.apply(codecRegistry);
    }

    private Function<CodecRegistry, BsonValue> astDoc(final String name, final BsonDocument value) {
        return (cr) -> new BsonDocument(name, value);
    }

    private Function<CodecRegistry, BsonValue> ast(final String name) {
        return (cr) -> new BsonDocument(name, this.toBsonValue(cr));
    }

    private Function<CodecRegistry, BsonValue> ast(final String name, final Expression param1) {
        return (cr) -> {
            BsonArray value = new BsonArray();
            value.add(this.toBsonValue(cr));
            value.add(extractBsonValue(cr, param1));
            return new BsonDocument(name, value);
        };
    }

    private Function<CodecRegistry, BsonValue> ast(final String name, final Expression param1, final Expression param2) {
        return (cr) -> {
            BsonArray value = new BsonArray();
            value.add(this.toBsonValue(cr));
            value.add(extractBsonValue(cr, param1));
            value.add(extractBsonValue(cr, param2));
            return new BsonDocument(name, value);
        };
    }

    /**
     * Takes an expression and converts it to a BsonValue. MqlExpression will be
     * the only implementation of Expression and all subclasses, so this will
     * not mis-cast an expression as anything else.
     */
    private static BsonValue extractBsonValue(final CodecRegistry cr, final Expression expression) {
        return ((MqlExpression<?>) expression).toBsonValue(cr);
    }

    /**
     * Converts an MqlExpression to any subtype of Expression. Users must not
     * extend Expression or its subtypes, so MqlExpression will implement any R.
     */
    @SuppressWarnings("unchecked")
    <R extends Expression> R assertImplementsAllExpressions() {
        return (R) this;
    }

    private static <R extends Expression> R newMqlExpression(final Function<CodecRegistry, BsonValue> ast) {
        return new MqlExpression<>(ast).assertImplementsAllExpressions();
    }

    private <R extends Expression> R variable(final String variable) {
        return newMqlExpression((cr) -> new BsonString(variable));
    }

    /** @see BooleanExpression */

    @Override
    public BooleanExpression not() {
        return new MqlExpression<>(ast("$not"));
    }

    @Override
    public BooleanExpression or(final BooleanExpression or) {
        return new MqlExpression<>(ast("$or", or));
    }

    @Override
    public BooleanExpression and(final BooleanExpression and) {
        return new MqlExpression<>(ast("$and", and));
    }

    @Override
    public <R extends Expression> R cond(final R left, final R right) {
        return newMqlExpression(ast("$cond", left, right));
    }


    /** @see Expression */

    @Override
    public BooleanExpression eq(final Expression eq) {
        return new MqlExpression<>(ast("$eq", eq));
    }

    @Override
    public BooleanExpression ne(final Expression ne) {
        return new MqlExpression<>(ast("$ne", ne));
    }

    @Override
    public BooleanExpression gt(final Expression gt) {
        return new MqlExpression<>(ast("$gt", gt));
    }

    @Override
    public BooleanExpression gte(final Expression gte) {
        return new MqlExpression<>(ast("$gte", gte));
    }

    @Override
    public BooleanExpression lt(final Expression lt) {
        return new MqlExpression<>(ast("$lt", lt));
    }

    @Override
    public BooleanExpression lte(final Expression lte) {
        return new MqlExpression<>(ast("$lte", lte));
    }

    /** @see ArrayExpression */

    @Override
    public <R extends Expression> ArrayExpression<R> map(final Function<? super T, ? extends R> in) {
        T varThis = variable("$$this");
        return new MqlExpression<>((cr) -> astDoc("$map", new BsonDocument()
                .append("input", this.toBsonValue(cr))
                .append("in", extractBsonValue(cr, in.apply(varThis)))).apply(cr));
    }

    @Override
    public ArrayExpression<T> filter(final Function<? super T, ? extends BooleanExpression> cond) {
        T varThis = variable("$$this");
        return new MqlExpression<T>((cr) -> astDoc("$filter", new BsonDocument()
                .append("input", this.toBsonValue(cr))
                .append("cond", extractBsonValue(cr, cond.apply(varThis)))).apply(cr));
    }

    @Override
    public T reduce(final T initialValue, final BinaryOperator<T> in) {
        T varThis = variable("$$this");
        T varValue = variable("$$value");
        return newMqlExpression((cr) -> astDoc("$reduce", new BsonDocument()
                .append("input", this.toBsonValue(cr))
                .append("initialValue", extractBsonValue(cr, initialValue))
                .append("in", extractBsonValue(cr, in.apply(varThis, varValue)))).apply(cr));
    }


    /** @see StringExpression */

    @Override
    public StringExpression toLower() {
        return new MqlExpression<>(ast("$toLower"));
    }

    @Override
    public StringExpression toUpper() {
        return new MqlExpression<>(ast("$toUpper"));
    }

    @Override
    public StringExpression concat(final StringExpression concat) {
        return new MqlExpression<>(ast("$concat", concat));
    }

    @Override
    public IntegerExpression strLen() {
        return new MqlExpression<>(ast("$strLenCP"));
    }

    @Override
    public IntegerExpression strLenBytes() {
        return new MqlExpression<>(ast("$strLenBytes"));
    }

    @Override
    public StringExpression substr(final IntegerExpression start, final IntegerExpression length) {
        return new MqlExpression<>(ast("$substrCP", start, length));
    }

    @Override
    public StringExpression substrBytes(final IntegerExpression start, final IntegerExpression length) {
        return new MqlExpression<>(ast("$substrBytes", start, length));
    }
}