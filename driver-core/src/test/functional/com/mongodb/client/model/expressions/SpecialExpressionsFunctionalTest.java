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

import com.mongodb.client.model.expressions.Expression.SwitchComplete;
import com.mongodb.client.model.expressions.Expression.SwitchInitial;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static com.mongodb.client.model.expressions.Expressions.of;
import static com.mongodb.client.model.expressions.Expressions.ofNull;

@SuppressWarnings({"PointlessArithmeticExpression", "Convert2MethodRef"})
class SpecialExpressionsFunctionalTest extends AbstractExpressionsFunctionalTest {

    @Test
    public void literalsTest() {
        // special values
        assertExpression(null, ofNull(), "null");
        // the "missing" value is obtained via getField.
        // the "$$REMOVE" value is intentionally not exposed. It is used internally.
        // the "undefined" value is deprecated.
        // https://www.mongodb.com/docs/manual/reference/operator/aggregation/literal/
        // $literal is intentionally not exposed. It is used internally.
    }

    @Test
    public void applyTest() {
        Function<IntegerExpression, IntegerExpression> decrement = (e) -> e.subtract(of(1));
        // "nested functional" function application:
        assertExpression(
                2 - 1,
                decrement.apply(of(2)),
                "{'$subtract': [2, 1]}");
        // "chained" function application:
        assertExpression(
                2 - 1, // = 0
                of(2).apply(decrement),
                "{'$subtract': [2, 1]}");
        // the parameters are reversed, compared to function.apply
    }


    @Test
    public void switchTest() {
        // https://www.mongodb.com/docs/manual/reference/operator/aggregation/switch/
        // initial
        assertExpression("A", of(0).switchMap(on -> on.caseEq(of(0), of("A"))), "{'$switch': {'branches': [{'case': {'$eq': [0, 0]}, 'then': 'A'}]}}");
        assertExpression("A", of(0).switchMap(on -> on.caseLt(of(1), of("A"))), "{'$switch': {'branches': [{'case': {'$lt': [0, 1]}, 'then': 'A'}]}}");
        assertExpression("A", of(0).switchMap(on -> on.caseLte(of(0), of("A"))), "{'$switch': {'branches': [{'case': {'$lte': [0, 0]}, 'then': 'A'}]}}");
        assertExpression("A", of(0).switchMap(on -> on.caseIs(v -> v.gt(of(-1)), of("A"))), "{'$switch': {'branches': [{'case': {'$gt': [0, -1]}, 'then': 'A'}]}}");
        // partial
        assertExpression("A", of(0).switchMap(on -> on.caseEq(of(9), of("X")).caseEq(of(0), of("A"))), "{'$switch': {'branches': [{'case': {'$eq': [0, 9]}, 'then': 'X'}, {'case': {'$eq': [0, 0]}, 'then': 'A'}]}}");
        assertExpression("A", of(0).switchMap(on -> on.caseEq(of(9), of("X")).caseLt(of(1), of("A"))), "{'$switch': {'branches': [{'case': {'$eq': [0, 9]}, 'then': 'X'}, {'case': {'$lt': [0, 1]}, 'then': 'A'}]}}");
        assertExpression("A", of(0).switchMap(on -> on.caseEq(of(9), of("X")).caseLte(of(0), of("A"))), "{'$switch': {'branches': [{'case': {'$eq': [0, 9]}, 'then': 'X'}, {'case': {'$lte': [0, 0]}, 'then': 'A'}]}}");
        assertExpression("A", of(0).switchMap(on -> on.caseEq(of(9), of("X")).caseIs(v -> v.gt(of(-1)), of("A"))), "{'$switch': {'branches': [{'case': {'$eq': [0, 9]}, 'then': 'X'}, {'case': {'$gt': [0, -1]}, 'then': 'A'}]}}");
        assertExpression("A", of(0).switchMap(on -> on.caseEq(of(9), of("X")).defaults(of("A"))), "{'$switch': {'branches': [{'case': {'$eq': [0, 9]}, 'then': 'X'}], 'default': 'A'}}");


        Function<IntegerExpression, BooleanExpression> isOver10 = v -> v.subtract(10).gt(of(0));
        Function<IntegerExpression, StringExpression> s = v -> v.switchMap((SwitchInitial<IntegerExpression> on) -> on.caseEq(of(0), of("A")).caseLt(of(10), of("B")).caseIs(isOver10, of("C")).defaults(of("D"))).toLower();

        assertExpression("a", of(0).apply(s));
        assertExpression("b", of(9).apply(s));
        assertExpression("b", of(-9).apply(s));
        assertExpression("c", of(11).apply(s));
        assertExpression("d", of(10).apply(s));

        assertExpression("c", of(99).apply(s),
                "{'$toLower': {'$switch': {'branches': [" + "{'case': {'$eq': [99, 0]}, 'then': 'A'}, "
                        + "{'case': {'$lt': [99, 10]}, 'then': 'B'}, "
                        + "{'case': {'$gt': [{'$subtract': [99, 10]}, 0]}, 'then': 'C'}], " + "'default': 'D'}}}");
    }
}
