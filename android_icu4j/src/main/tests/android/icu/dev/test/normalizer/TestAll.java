/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 *******************************************************************************
 * Copyright (C) 1996-2010, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package android.icu.dev.test.normalizer;

import android.icu.dev.test.TestFmwk.TestGroup;
import org.junit.runner.RunWith;
import android.icu.junit.IcuTestGroupRunner;

/**
 * Top level test used to run normalizer tests as a batch.
 */
@RunWith(IcuTestGroupRunner.class)
public class TestAll extends TestGroup {

    public static void main(String[] args) {
        new TestAll().run(args);
    }

    public TestAll() {
        super(new String[] {
            "BasicTest",
            "ConformanceTest",
            "TestDeprecatedNormalizerAPI",
            "TestCanonicalIterator",
            "NormalizationMonkeyTest",
            "NormalizerRegressionTests",
            "UTS46Test"
        });
    }

    public static final String CLASS_TARGET_NAME = "Normalizer";
}
