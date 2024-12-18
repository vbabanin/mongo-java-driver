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

package com.mongodb.client.unified;

import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

public final class UnifiedRetryableReadsTest extends UnifiedSyncTest {
    @Override
    protected void skips(final String fileDescription, final String testDescription) {
        doSkips(fileDescription, testDescription);
    }

    public static void doSkips(final String fileDescription, @SuppressWarnings("unused") final String testDescription) {
        // Skipped because driver removed the deprecated count methods
        assumeFalse(fileDescription.equals("count"));
        assumeFalse(fileDescription.equals("count-serverErrors"));
        // Skipped because the driver never had these methods
        assumeFalse(fileDescription.equals("listDatabaseObjects"));
        assumeFalse(fileDescription.equals("listDatabaseObjects-serverErrors"));
        assumeFalse(fileDescription.equals("listCollectionObjects"));
        assumeFalse(fileDescription.equals("listCollectionObjects-serverErrors"));

        // JAVA-5224
        assumeFalse(fileDescription.equals("ReadConcernMajorityNotAvailableYet is a retryable read")
                && testDescription.equals("Find succeeds on second attempt after ReadConcernMajorityNotAvailableYet"),
                "JAVA-5224");
    }

    private static Collection<Arguments> data() throws URISyntaxException, IOException {
        return getTestData("unified-test-format/retryable-reads");
    }
}
