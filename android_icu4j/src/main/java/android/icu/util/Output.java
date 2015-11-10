/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 *******************************************************************************
 * Copyright (C) 2011-2012, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package android.icu.util;

/**
 * Simple struct-like class for output parameters.
 * @param <T> The type of the parameter.
 */
public class Output<T> {
    /**
     * The value field
     */
    public T value;

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return value == null ? "null" : value.toString();
    }

    /**
     * Constructs an empty <code>Output</code>
     */
    public Output() {
        
    }

    /**
     * Constructs an <code>Output</code> withe the given value.
     * @param value the initial value
     */
    public Output(T value) {
        this.value = value;
    }
}
