/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.client;

import com.mongodb.ClusterFixture;
import com.mongodb.client.unified.UnifiedSyncTest;
import com.mongodb.lang.Nullable;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assume.assumeFalse;

// See https://github.com/mongodb/specifications/tree/master/source/client-side-operation-timeout/tests
@RunWith(Parameterized.class)
public class ClientSideOperationTimeoutTest extends UnifiedSyncTest {
    private final String testDescription;
    public ClientSideOperationTimeoutTest(final String fileDescription, final String testDescription,
            final String schemaVersion, @Nullable final BsonArray runOnRequirements, final BsonArray entities,
            final BsonArray initialData, final BsonDocument definition) {
        super(schemaVersion, runOnRequirements, entities, initialData, definition);
        this.testDescription = testDescription;
        checkSkipCSOTTest(fileDescription, testDescription);

        assumeFalse("TODO (CSOT) - JAVA-5104", fileDescription.equals("timeoutMS behaves correctly for non-tailable cursors")
                &&  testDescription.equals("timeoutMS applied to find if timeoutMode is iteration"));
    }

    @Parameterized.Parameters(name = "{0}: {1}")
    public static Collection<Object[]> data() throws URISyntaxException, IOException {
        return getTestData("unified-test-format/client-side-operation-timeout");
    }

    @Test
    @Override
    public void shouldPassAllOutcomes() {
        try {
            super.shouldPassAllOutcomes();
        } catch (AssertionError e) {
            if (racyTestAssertion(testDescription, e)) {
                // Ignore failure - racy test often no time to do the getMore
                return;
            }
            throw e;
        }
    }

    public static boolean racyTestAssertion(final String testDescription, final AssertionError e) {
        return RACY_GET_MORE_TESTS.contains(testDescription) && e.getMessage().startsWith("Number of events must be the same");
    }

    public static void checkSkipCSOTTest(final String fileDescription, final String testDescription) {
        if (ClusterFixture.isServerlessTest()) {

            // It is not possible to create capped collections on serverless instances.
            assumeFalse(fileDescription.equals("timeoutMS behaves correctly for tailable awaitData cursors"));
            assumeFalse(fileDescription.equals("timeoutMS behaves correctly for tailable non-awaitData cursors"));

            /* Drivers MUST NOT execute a killCursors command because the pinned connection is no longer under a load balancer. */
            assumeFalse(testDescription.equals("timeoutMS is refreshed for close"));
        }

        assumeFalse("No maxTimeMS parameter for createIndex() method",
                testDescription.contains("maxTimeMS is ignored if timeoutMS is set - createIndex on collection"));
        assumeFalse("TODO (CSOT) CRUD Failure",
                testDescription.contains("socketTimeoutMS is ignored if timeoutMS is set - deleteMany on collection"));

        assumeFalse("No run cursor command", fileDescription.startsWith("runCursorCommand"));
        assumeFalse("No special handling of runCommand", testDescription.contains("runCommand on database"));

        assumeFalse("No count command helper", testDescription.endsWith("count on collection"));

        assumeFalse("No operation based overrides", fileDescription.equals("timeoutMS can be overridden for an operation"));
        assumeFalse("No operation session based overrides",
                fileDescription.equals("timeoutMS can be overridden for individual session operations"));
        assumeFalse("No operation session based overrides",
                fileDescription.equals("operations ignore deprected timeout options if timeoutMS is set")
                        && (testDescription.equals("abortTransaction ignores socketTimeoutMS if timeoutMS is set")
                        || testDescription.equals("commitTransaction ignores socketTimeoutMS if timeoutMS is set")
                        || testDescription.equals("commitTransaction ignores maxCommitTimeMS if timeoutMS is set")
                ));
        assumeFalse("No operation based overrides", fileDescription.equals("timeoutMS behaves correctly when closing cursors")
                && testDescription.equals("timeoutMS can be overridden for close"));

        assumeFalse("TODO (CSOT) - JAVA-5259 No client.withTimeout", testDescription.endsWith("on client"));
    }

    private static final List<String> RACY_GET_MORE_TESTS = asList(
            "remaining timeoutMS applied to getMore if timeoutMode is cursor_lifetime",
            "remaining timeoutMS applied to getMore if timeoutMode is unset");
}