/* GENERATED SOURCE. DO NOT MODIFY. */
/*
*******************************************************************************
*   Copyright (C) 2009-2012, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*/

package android.icu.impl;

import java.util.MissingResourceException;

import android.icu.util.UResourceBundle;
import android.icu.util.VersionInfo;

/** @hide Only a subset of ICU is exposed in Android
* @hide All android.icu classes are currently hidden
 */
public final class ICUDataVersion {
    private static final String U_ICU_VERSION_BUNDLE = "icuver";
    
    private static final String U_ICU_DATA_KEY = "DataVersion";
    
    /**
     * This function retrieves the data version from icuver and returns a VersionInfo object with that version information.
     *
     * @return Current icu data version
     */
    public static VersionInfo getDataVersion() {
        UResourceBundle icudatares = null;
        try {
            icudatares = UResourceBundle.getBundleInstance(ICUResourceBundle.ICU_BASE_NAME, ICUDataVersion.U_ICU_VERSION_BUNDLE, ICUResourceBundle.ICU_DATA_CLASS_LOADER);
            icudatares = icudatares.get(ICUDataVersion.U_ICU_DATA_KEY);
        } catch (MissingResourceException ex) {
            return null;
        }
        
        return  VersionInfo.getInstance(icudatares.getString());
    }
}
