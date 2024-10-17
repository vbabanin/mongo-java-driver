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

package org.bson;

import org.bson.types.Binary;

import java.util.Arrays;

import static org.bson.assertions.Assertions.assertNotNull;

/**
 * Represents a vector of 8-bit signed integers, where each element in the vector is a byte.
 * <p>
 * The {@link Int8Vector} is used to store and retrieve data efficiently using the BSON Binary Subtype 9 format.
 *
 * @mongodb.server.release 6.0
 * @see Vector#int8Vector(byte[])
 * @see BsonBinary#BsonBinary(Vector)
 * @see BsonBinary#asVector()
 * @see Binary#Binary(Vector)
 * @see Binary#asVector()
 * @since BINARY_VECTOR
 */
public final class Int8Vector extends Vector {

    private byte[] vectorData;

    Int8Vector(final byte[] vectorData) {
        super(Dtype.INT8);
        this.vectorData = assertNotNull(vectorData);
    }

    /**
     * Retrieve the underlying byte array representing this {@link Int8Vector} vector, where each byte represents
     * an element of a vector.
     * <p>
     * NOTE: The underlying byte array is not copied; changes to the returned array will be reflected in this instance.
     *
     * @return the underlying byte array representing this {@link Int8Vector} vector.
     */
    public byte[] getVectorArray() {
        return assertNotNull(vectorData);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Int8Vector)) {
            return false;
        }

        Int8Vector that = (Int8Vector) o;
        return Arrays.equals(vectorData, that.vectorData);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vectorData);
    }

    @Override
    public String toString() {
        return "Int8Vector{"
                + "vectorData=" + Arrays.toString(vectorData)
                + ", vectorType=" + getDataType()
                + '}';
    }
}