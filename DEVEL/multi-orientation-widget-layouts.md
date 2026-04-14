# Multi-Orientation Widget Layout Implementation Plan

## Overview

This plan implements support for orientation-specific widget layouts using a two-tiered system:
- **Tier 1 (Stock Layouts)**: Anchor-based layouts in JSON that adapt to different screen sizes
- **Tier 2 (User Layouts)**: Absolute-position layouts saved when users customize in edit mode

Once a user saves a layout in edit mode, it overrides the stock layout for that orientation with fixed pixel positions.

## Architecture Summary

**Current State:**
- Single widget layout stored in SharedPreferences (`"widget_layout"`)
- Absolute pixel positions (x, y, w, h)
- Same layout used for all orientations

**Target State:**
- Stock layouts in `assets/default_layouts/*.json` with anchor-based positioning
- User layouts in SharedPreferences with orientation keys: `"layouts/{orientation}/widget_*"`
- Automatic reload when orientation changes
- "Reset to Default" button in edit mode to restore stock layouts

## File Structure

```
sys/android/forkfront/lib/
├── src/com/tbd/forkfront/
│   ├── LayoutConfiguration.java          [NEW]
│   ├── StockLayoutEvaluator.java         [NEW]
│   ├── WidgetLayout.java                  [MODIFY]
│   ├── NH_State.java                      [MODIFY]
│   └── ... (existing files)
├── res/
│   ├── layout/
│   │   └── mainwindow.xml                 [MODIFY - add Reset button]
│   └── values/
│       └── strings.xml                    [MODIFY - add string resources]
└── assets/
    └── default_layouts/                   [NEW DIRECTORY]
        ├── portrait.json                  [NEW]
        └── landscape.json                 [NEW]
```

---

## Phase 1: Layout Configuration Detection

**Goal:** Create a utility class to detect current layout configuration (portrait, landscape, tablet_portrait, etc.)

**New File:** `sys/android/forkfront/lib/src/com/tbd/forkfront/LayoutConfiguration.java`

**Implementation:**

```java
package com.tbd.forkfront;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Determines the current layout configuration key based on device characteristics.
 * Used to select the appropriate widget layout (stock or user-customized).
 */
public class LayoutConfiguration {
    
    /**
     * Get the layout key for the current device configuration.
     * 
     * @param context Android context
     * @return Layout key string (e.g., "portrait", "landscape", "tablet_portrait")
     */
    public static String getCurrentLayoutKey(Context context) {
        Configuration config = context.getResources().getConfiguration();
        int orientation = config.orientation;
        int screenLayout = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        
        // Determine if device is a tablet (large or xlarge screen)
        boolean isTablet = screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        
        // For now, just distinguish phone vs tablet, portrait vs landscape
        // Future: could add foldable detection, multi-window, etc.
        if (isTablet) {
            return isLandscape ? "tablet_landscape" : "tablet_portrait";
        } else {
            return isLandscape ? "landscape" : "portrait";
        }
    }
    
    /**
     * Check if a given orientation/configuration has a stock layout defined.
     * 
     * @param context Android context
     * @param layoutKey Layout configuration key
     * @return true if stock layout exists in assets
     */
    public static boolean hasStockLayout(Context context, String layoutKey) {
        try {
            String[] files = context.getAssets().list("default_layouts");
            if (files != null) {
                for (String file : files) {
                    if (file.equals(layoutKey + ".json")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("LayoutConfiguration", "Error checking for stock layout", e);
        }
        return false;
    }
}
```

**Testing:**
- Create a simple test activity that logs the layout key on different orientations
- Verify it returns "portrait" in portrait mode, "landscape" in landscape mode
- Test on both phone and tablet if available

---

## Phase 2: Stock Layout Format & Evaluator

**Goal:** Define JSON format for stock layouts and create evaluator to convert anchors to absolute positions

### Step 2a: Define JSON Format

**New Directory:** `sys/android/forkfront/lib/assets/default_layouts/`

**New File:** `sys/android/forkfront/lib/assets/default_layouts/portrait.json`

```json
{
  "version": 1,
  "widgets": [
    {
      "type": "status",
      "anchor": {
        "horizontal": "LEFT",
        "vertical": "TOP",
        "marginLeft": "0dp",
        "marginTop": "0dp"
      },
      "size": {
        "width": "MATCH_PARENT",
        "height": "60dp"
      },
      "properties": {
        "opacity": 191,
        "fontSize": 15
      }
    },
    {
      "type": "message",
      "anchor": {
        "horizontal": "LEFT",
        "vertical": "TOP",
        "marginLeft": "0dp",
        "marginTop": "60dp"
      },
      "size": {
        "width": "MATCH_PARENT",
        "height": "80dp"
      },
      "properties": {
        "opacity": 191,
        "fontSize": 15,
        "rows": 3
      }
    },
    {
      "type": "contextual",
      "anchor": {
        "horizontal": "LEFT",
        "vertical": "TOP",
        "marginLeft": "0dp",
        "marginTop": "140dp"
      },
      "size": {
        "width": "MATCH_PARENT",
        "height": "50dp"
      },
      "properties": {
        "horizontal": true,
        "opacity": 191
      }
    },
    {
      "type": "dpad",
      "anchor": {
        "horizontal": "LEFT",
        "vertical": "BOTTOM",
        "marginLeft": "20dp",
        "marginBottom": "20dp"
      },
      "size": {
        "width": "180dp",
        "height": "180dp"
      },
      "properties": {
        "opacity": 191
      }
    },
    {
      "type": "button",
      "label": "Search",
      "command": "s",
      "anchor": {
        "horizontal": "LEFT",
        "vertical": "BOTTOM",
        "marginLeft": "220dp",
        "marginBottom": "20dp"
      },
      "size": {
        "width": "100dp",
        "height": "60dp"
      },
      "properties": {
        "opacity": 191
      }
    }
  ]
}
```

**New File:** `sys/android/forkfront/lib/assets/default_layouts/landscape.json`

```json
{
  "version": 1,
  "widgets": [
    {
      "type": "status",
      "anchor": {
        "horizontal": "LEFT",
        "vertical": "TOP",
        "marginLeft": "0dp",
        "marginTop": "0dp"
      },
      "size": {
        "width": "MATCH_PARENT",
        "height": "50dp"
      },
      "properties": {
        "opacity": 191,
        "fontSize": 14
      }
    },
    {
      "type": "message",
      "anchor": {
        "horizontal": "LEFT",
        "vertical": "TOP",
        "marginLeft": "0dp",
        "marginTop": "50dp"
      },
      "size": {
        "width": "MATCH_PARENT",
        "height": "60dp"
      },
      "properties": {
        "opacity": 191,
        "fontSize": 14,
        "rows": 2
      }
    },
    {
      "type": "contextual",
      "anchor": {
        "horizontal": "LEFT",
        "vertical": "TOP",
        "marginLeft": "0dp",
        "marginTop": "110dp"
      },
      "size": {
        "width": "MATCH_PARENT",
        "height": "45dp"
      },
      "properties": {
        "horizontal": true,
        "opacity": 191
      }
    },
    {
      "type": "dpad",
      "anchor": {
        "horizontal": "LEFT",
        "vertical": "BOTTOM",
        "marginLeft": "20dp",
        "marginBottom": "20dp"
      },
      "size": {
        "width": "150dp",
        "height": "150dp"
      },
      "properties": {
        "opacity": 191
      }
    },
    {
      "type": "button",
      "label": "Search",
      "command": "s",
      "anchor": {
        "horizontal": "LEFT",
        "vertical": "BOTTOM",
        "marginLeft": "190dp",
        "marginBottom": "20dp"
      },
      "size": {
        "width": "90dp",
        "height": "55dp"
      },
      "properties": {
        "opacity": 191
      }
    }
  ]
}
```

**Notes:**
- `MATCH_PARENT` means full screen width/height
- All margin and size values in DP (density-independent pixels)
- `horizontal` anchor: LEFT, RIGHT, CENTER
- `vertical` anchor: TOP, BOTTOM, CENTER
- Properties map directly to `ControlWidget.WidgetData` fields

### Step 2b: Create Stock Layout Evaluator

**New File:** `sys/android/forkfront/lib/src/com/tbd/forkfront/StockLayoutEvaluator.java`

```java
package com.tbd.forkfront;

import android.content.Context;
import android.util.DisplayMetrics;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates stock widget layouts from JSON into absolute screen positions.
 * Converts anchor-based positioning (LEFT/RIGHT/CENTER + margins in DP)
 * to pixel coordinates based on actual screen metrics.
 */
public class StockLayoutEvaluator {
    
    private final Context mContext;
    private final DisplayMetrics mMetrics;
    private final float mDensity;
    
    public StockLayoutEvaluator(Context context) {
        mContext = context;
        mMetrics = context.getResources().getDisplayMetrics();
        mDensity = mMetrics.density;
    }
    
    /**
     * Load and evaluate a stock layout JSON file.
     * 
     * @param layoutKey Layout configuration key (e.g., "portrait")
     * @return List of WidgetData with absolute pixel positions, or null on error
     */
    public List<ControlWidget.WidgetData> evaluateStockLayout(String layoutKey) {
        try {
            // Load JSON from assets
            InputStream is = mContext.getAssets().open("default_layouts/" + layoutKey + ".json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            
            JSONObject root = new JSONObject(json);
            JSONArray widgets = root.getJSONArray("widgets");
            
            List<ControlWidget.WidgetData> result = new ArrayList<>();
            
            for (int i = 0; i < widgets.length(); i++) {
                JSONObject widgetDef = widgets.getJSONObject(i);
                ControlWidget.WidgetData data = evaluateWidget(widgetDef);
                if (data != null) {
                    result.add(data);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            android.util.Log.e("StockLayoutEvaluator", "Failed to load stock layout: " + layoutKey, e);
            return null;
        }
    }
    
    /**
     * Evaluate a single widget definition to absolute positions.
     */
    private ControlWidget.WidgetData evaluateWidget(JSONObject def) throws Exception {
        ControlWidget.WidgetData data = new ControlWidget.WidgetData();
        
        // Basic properties
        data.type = def.getString("type");
        data.label = def.optString("label", "");
        data.command = def.optString("command", "");
        
        // Evaluate size first (needed for position calculation)
        JSONObject sizeObj = def.getJSONObject("size");
        data.w = evaluateSize(sizeObj.getString("width"), true);
        data.h = evaluateSize(sizeObj.getString("height"), false);
        
        // Evaluate position from anchor
        JSONObject anchorObj = def.getJSONObject("anchor");
        String hAnchor = anchorObj.getString("horizontal");
        String vAnchor = anchorObj.getString("vertical");
        
        int marginLeft = dpToPx(anchorObj.optString("marginLeft", "0dp"));
        int marginTop = dpToPx(anchorObj.optString("marginTop", "0dp"));
        int marginRight = dpToPx(anchorObj.optString("marginRight", "0dp"));
        int marginBottom = dpToPx(anchorObj.optString("marginBottom", "0dp"));
        
        // Calculate x position
        switch (hAnchor) {
            case "LEFT":
                data.x = marginLeft;
                break;
            case "RIGHT":
                data.x = mMetrics.widthPixels - data.w - marginRight;
                break;
            case "CENTER":
                data.x = (mMetrics.widthPixels - data.w) / 2 + marginLeft - marginRight;
                break;
            default:
                android.util.Log.w("StockLayoutEvaluator", "Unknown horizontal anchor: " + hAnchor);
                data.x = 0;
        }
        
        // Calculate y position (account for status bar on TOP anchors)
        int statusBarHeight = getStatusBarHeight();
        
        switch (vAnchor) {
            case "TOP":
                data.y = marginTop + statusBarHeight;
                break;
            case "BOTTOM":
                data.y = mMetrics.heightPixels - data.h - marginBottom;
                break;
            case "CENTER":
                data.y = (mMetrics.heightPixels - data.h) / 2 + marginTop - marginBottom;
                break;
            default:
                android.util.Log.w("StockLayoutEvaluator", "Unknown vertical anchor: " + vAnchor);
                data.y = 0;
        }
        
        // Copy properties if present
        if (def.has("properties")) {
            JSONObject props = def.getJSONObject("properties");
            data.opacity = props.optInt("opacity", 191);
            data.fontSize = props.optInt("fontSize", 15);
            data.horizontal = props.optBoolean("horizontal", true);
            data.rows = props.optInt("rows", 3);
            data.columns = props.optInt("columns", 3);
            data.category = props.optString("category", null);
            if (data.category != null && data.category.isEmpty()) {
                data.category = null;
            }
        }
        
        return data;
    }
    
    /**
     * Evaluate a size specification to pixels.
     * 
     * @param spec Size specification: "MATCH_PARENT", "XXdp", or "XX%" (percentage)
     * @param isWidth true for width, false for height
     * @return Size in pixels
     */
    private int evaluateSize(String spec, boolean isWidth) {
        if ("MATCH_PARENT".equals(spec)) {
            return isWidth ? mMetrics.widthPixels : mMetrics.heightPixels;
        } else if (spec.endsWith("dp")) {
            return dpToPx(spec);
        } else if (spec.endsWith("%")) {
            // Percentage of screen dimension
            int percent = Integer.parseInt(spec.substring(0, spec.length() - 1));
            int screenSize = isWidth ? mMetrics.widthPixels : mMetrics.heightPixels;
            return (screenSize * percent) / 100;
        }
        
        android.util.Log.w("StockLayoutEvaluator", "Unknown size spec: " + spec);
        return dpToPx("200dp"); // fallback
    }
    
    /**
     * Convert DP string to pixels.
     */
    private int dpToPx(String dpStr) {
        String numStr = dpStr.replace("dp", "").trim();
        int dp = Integer.parseInt(numStr);
        return Math.round(dp * mDensity);
    }
    
    /**
     * Get Android status bar height to avoid overlap.
     */
    private int getStatusBarHeight() {
        int resourceId = mContext.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return mContext.getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }
}
```

**Testing:**
- Create a test that loads portrait.json and verifies positions are calculated correctly
- Test with different screen sizes/densities if possible
- Verify MATCH_PARENT results in full screen width
- Verify margins work correctly for each anchor type

---

## Phase 3: Update WidgetLayout for Two-Tiered Loading

**Goal:** Modify `WidgetLayout.java` to support both stock and user layouts with orientation-specific keys

**File:** `sys/android/forkfront/lib/src/com/tbd/forkfront/WidgetLayout.java`

**Changes:**

### 3a: Add orientation-aware save/load methods

**Find the existing `saveLayout()` method (line 110) and replace with:**

```java
public void saveLayout() {
    String layoutKey = LayoutConfiguration.getCurrentLayoutKey(getContext());
    android.content.SharedPreferences prefs = getContext().getSharedPreferences("widget_layout", Context.MODE_PRIVATE);
    android.content.SharedPreferences.Editor editor = prefs.edit();
    
    String prefix = "layouts/" + layoutKey + "/";
    
    editor.putInt(prefix + "widget_count", mWidgets.size());
    for (int i = 0; i < mWidgets.size(); i++) {
        ControlWidget widget = mWidgets.get(i);
        ControlWidget.WidgetData data = widget.getWidgetData();
        editor.putString(prefix + "widget_" + i + "_type", data.type);
        editor.putString(prefix + "widget_" + i + "_label", data.label);
        editor.putString(prefix + "widget_" + i + "_command", data.command);
        editor.putBoolean(prefix + "widget_" + i + "_horizontal", data.horizontal);
        editor.putFloat(prefix + "widget_" + i + "_x", data.x);
        editor.putFloat(prefix + "widget_" + i + "_y", data.y);
        editor.putInt(prefix + "widget_" + i + "_w", data.w);
        editor.putInt(prefix + "widget_" + i + "_h", data.h);
        editor.putInt(prefix + "widget_" + i + "_opacity", data.opacity);
        editor.putInt(prefix + "widget_" + i + "_font_size", data.fontSize);
        editor.putInt(prefix + "widget_" + i + "_rows", data.rows);
        editor.putInt(prefix + "widget_" + i + "_columns", data.columns);
        editor.putString(prefix + "widget_" + i + "_category", data.category);
    }
    editor.apply();
    
    android.util.Log.d("WidgetLayout", "Saved layout for: " + layoutKey + " with " + mWidgets.size() + " widgets");
}
```

**Find the existing `loadLayout()` method (line 135) and replace with:**

```java
public void loadLayout() {
    android.util.Log.d("WidgetLayout", "loadLayout called");
    
    String layoutKey = LayoutConfiguration.getCurrentLayoutKey(getContext());
    android.content.SharedPreferences prefs = getContext().getSharedPreferences("widget_layout", Context.MODE_PRIVATE);
    
    String userLayoutKey = "layouts/" + layoutKey + "/widget_count";
    
    // Check if user has customized this orientation
    if (prefs.contains(userLayoutKey)) {
        android.util.Log.d("WidgetLayout", "Loading user layout for: " + layoutKey);
        loadUserLayout(layoutKey, prefs);
    } else {
        android.util.Log.d("WidgetLayout", "Loading stock layout for: " + layoutKey);
        loadStockLayout(layoutKey);
    }
}
```

### 3b: Add new private methods for user and stock loading

**Add after the existing `createWidget()` method:**

```java
/**
 * Load user's customized layout from SharedPreferences.
 */
private void loadUserLayout(String layoutKey, android.content.SharedPreferences prefs) {
    // Clear existing widgets
    for (ControlWidget w : mWidgets) {
        removeView(w);
    }
    mWidgets.clear();
    
    String prefix = "layouts/" + layoutKey + "/";
    int count = prefs.getInt(prefix + "widget_count", 0);
    android.util.Log.d("WidgetLayout", "Loading " + count + " user widgets from: " + layoutKey);
    
    for (int i = 0; i < count; i++) {
        ControlWidget.WidgetData data = new ControlWidget.WidgetData();
        data.type = prefs.getString(prefix + "widget_" + i + "_type", "");
        data.label = prefs.getString(prefix + "widget_" + i + "_label", "");
        data.command = prefs.getString(prefix + "widget_" + i + "_command", "");
        data.horizontal = prefs.getBoolean(prefix + "widget_" + i + "_horizontal", true);
        data.x = prefs.getFloat(prefix + "widget_" + i + "_x", 0);
        data.y = prefs.getFloat(prefix + "widget_" + i + "_y", 0);
        data.w = prefs.getInt(prefix + "widget_" + i + "_w", 200);
        data.h = prefs.getInt(prefix + "widget_" + i + "_h", 200);
        data.opacity = prefs.getInt(prefix + "widget_" + i + "_opacity", 191);
        data.fontSize = prefs.getInt(prefix + "widget_" + i + "_font_size", 15);
        data.rows = prefs.getInt(prefix + "widget_" + i + "_rows", 3);
        data.columns = prefs.getInt(prefix + "widget_" + i + "_columns", 3);
        data.category = prefs.getString(prefix + "widget_" + i + "_category", null);
        
        ControlWidget widget = createWidget(data);
        if (widget != null) {
            addWidget(widget);
            widget.setWidgetData(data);
            widget.setFontSize(data.fontSize);
        }
    }
}

/**
 * Load stock layout from assets and evaluate to absolute positions.
 */
private void loadStockLayout(String layoutKey) {
    // Clear existing widgets
    for (ControlWidget w : mWidgets) {
        removeView(w);
    }
    mWidgets.clear();
    
    // Check if stock layout exists
    if (!LayoutConfiguration.hasStockLayout(getContext(), layoutKey)) {
        android.util.Log.w("WidgetLayout", "No stock layout found for: " + layoutKey);
        // Fall back to empty layout - user can add widgets manually
        return;
    }
    
    // Evaluate stock layout to absolute positions
    StockLayoutEvaluator evaluator = new StockLayoutEvaluator(getContext());
    List<ControlWidget.WidgetData> stockWidgets = evaluator.evaluateStockLayout(layoutKey);
    
    if (stockWidgets == null) {
        android.util.Log.e("WidgetLayout", "Failed to evaluate stock layout: " + layoutKey);
        return;
    }
    
    android.util.Log.d("WidgetLayout", "Loaded " + stockWidgets.size() + " stock widgets for: " + layoutKey);
    
    // Create widgets from evaluated positions
    for (ControlWidget.WidgetData data : stockWidgets) {
        ControlWidget widget = createWidget(data);
        if (widget != null) {
            addWidget(widget);
            widget.setWidgetData(data);
            widget.setFontSize(data.fontSize);
        }
    }
    
    // DO NOT call saveLayout() here - we want stock layout to remain
    // unevaluated until user explicitly saves in edit mode
}
```

### 3c: Add configuration change handling

**Add this new method:**

```java
/**
 * Called when device configuration changes (e.g., rotation).
 * Reloads the appropriate layout for the new orientation.
 */
public void onConfigurationChanged(android.content.res.Configuration newConfig) {
    android.util.Log.d("WidgetLayout", "onConfigurationChanged");
    
    // Save current layout if in edit mode (preserve unsaved changes)
    if (mEditMode) {
        saveLayout();
    }
    
    // Reload layout for new orientation
    loadLayout();
    
    // Restore edit mode if it was active
    if (mEditMode) {
        setEditMode(true);
    }
}
```

### 3d: Add reset to default method

**Add this new public method:**

```java
/**
 * Reset the current orientation's layout to stock default.
 * Deletes user customizations for the current orientation only.
 */
public void resetToDefault() {
    String layoutKey = LayoutConfiguration.getCurrentLayoutKey(getContext());
    android.content.SharedPreferences prefs = getContext().getSharedPreferences("widget_layout", Context.MODE_PRIVATE);
    android.content.SharedPreferences.Editor editor = prefs.edit();
    
    String prefix = "layouts/" + layoutKey + "/";
    
    // Remove all user customizations for this layout
    int count = prefs.getInt(prefix + "widget_count", 0);
    for (int i = 0; i < count; i++) {
        editor.remove(prefix + "widget_" + i + "_type");
        editor.remove(prefix + "widget_" + i + "_label");
        editor.remove(prefix + "widget_" + i + "_command");
        editor.remove(prefix + "widget_" + i + "_horizontal");
        editor.remove(prefix + "widget_" + i + "_x");
        editor.remove(prefix + "widget_" + i + "_y");
        editor.remove(prefix + "widget_" + i + "_w");
        editor.remove(prefix + "widget_" + i + "_h");
        editor.remove(prefix + "widget_" + i + "_opacity");
        editor.remove(prefix + "widget_" + i + "_font_size");
        editor.remove(prefix + "widget_" + i + "_rows");
        editor.remove(prefix + "widget_" + i + "_columns");
        editor.remove(prefix + "widget_" + i + "_category");
    }
    editor.remove(prefix + "widget_count");
    editor.apply();
    
    android.util.Log.d("WidgetLayout", "Reset to default for: " + layoutKey);
    
    // Reload stock layout
    loadLayout();
}
```

**Testing:**
- Test loadLayout() loads stock layout when no user customization exists
- Test saveLayout() saves with orientation prefix
- Test resetToDefault() removes user layout and loads stock
- Test onConfigurationChanged() reloads correct layout on rotation

---

## Phase 4: Update NH_State for Configuration Changes

**Goal:** Hook WidgetLayout into the configuration change lifecycle and remove old hardcoded initialization

**File:** `sys/android/forkfront/lib/src/com/tbd/forkfront/NH_State.java`

**Changes:**

### 4a: Update onConfigurationChanged

**Find the `onConfigurationChanged()` method (line 327) and update:**

```java
public void onConfigurationChanged(Configuration newConfig)
{
    if(mMode == CmdMode.Keyboard && mKeyboard != null)
    {
        // Since the keyboard refuses to change its layout when the orientation changes
        // we recreate a new keyboard every time
        hideKeyboard();
        showKeyboard();
    }

    // Reload widget layout for new orientation
    if (mWidgetLayout != null) {
        mWidgetLayout.onConfigurationChanged(newConfig);
    }

    // Forward configuration changes to map for adaptive tile scaling
    if(mMap != null)
        mMap.onConfigurationChanged(newConfig);
}
```

### 4b: Remove old hardcoded widget initialization

**Find the `setContext()` method (around line 104) and REMOVE the entire block from line 149-260** that starts with:
```java
// Initialize default widgets if it's the first run of the new system (BEFORE loadLayout)
SharedPreferences ffPrefs = activity.getSharedPreferences("forkfront_ui", Context.MODE_PRIVATE);
...
```

This block manually creates default widgets. Replace it with just the loadLayout call:

```java
if (mWidgetLayout != null) {
    mWidgetLayout.setNHState(this);
    
    // Load layout (will use stock layout if user hasn't customized)
    mWidgetLayout.loadLayout();
}
```

**Note:** The old initialization used a `"initialized_v2"` flag. We no longer need this because stock layouts handle defaults automatically.

**Testing:**
- Test that rotating the device triggers layout reload
- Verify widgets reload correctly after rotation
- Test that edit mode state is preserved across rotation

---

## Phase 5: Migration from Legacy Single-Layout System

**Goal:** Detect and migrate users with the old single-layout format to the new orientation-based system

**File:** `sys/android/forkfront/lib/src/com/tbd/forkfront/WidgetLayout.java`

**Changes:**

### 5a: Add migration logic to loadLayout

**Update the `loadLayout()` method to check for legacy layout:**

```java
public void loadLayout() {
    android.util.Log.d("WidgetLayout", "loadLayout called");
    
    String layoutKey = LayoutConfiguration.getCurrentLayoutKey(getContext());
    android.content.SharedPreferences prefs = getContext().getSharedPreferences("widget_layout", Context.MODE_PRIVATE);
    
    // Check for legacy layout (old "widget_count" key without "layouts/" prefix)
    if (prefs.contains("widget_count") && !prefs.getBoolean("migrated_to_v3", false)) {
        android.util.Log.d("WidgetLayout", "Migrating legacy layout to v3");
        migrateLegacyLayout(prefs);
    }
    
    String userLayoutKey = "layouts/" + layoutKey + "/widget_count";
    
    // Check if user has customized this orientation
    if (prefs.contains(userLayoutKey)) {
        android.util.Log.d("WidgetLayout", "Loading user layout for: " + layoutKey);
        loadUserLayout(layoutKey, prefs);
    } else {
        android.util.Log.d("WidgetLayout", "Loading stock layout for: " + layoutKey);
        loadStockLayout(layoutKey);
    }
}
```

### 5b: Add migration method

**Add this private method:**

```java
/**
 * Migrate legacy single-layout format to new orientation-based format.
 * Copies the old layout to "layouts/portrait/" and sets migration flag.
 */
private void migrateLegacyLayout(android.content.SharedPreferences prefs) {
    android.content.SharedPreferences.Editor editor = prefs.edit();
    
    int count = prefs.getInt("widget_count", 0);
    if (count == 0) {
        // No legacy layout to migrate
        editor.putBoolean("migrated_to_v3", true);
        editor.apply();
        return;
    }
    
    android.util.Log.d("WidgetLayout", "Migrating " + count + " widgets from legacy format");
    
    // Copy to portrait layout (assume user set up in portrait)
    String prefix = "layouts/portrait/";
    editor.putInt(prefix + "widget_count", count);
    
    for (int i = 0; i < count; i++) {
        // Copy each widget property
        String type = prefs.getString("widget_" + i + "_type", "");
        String label = prefs.getString("widget_" + i + "_label", "");
        String command = prefs.getString("widget_" + i + "_command", "");
        boolean horizontal = prefs.getBoolean("widget_" + i + "_horizontal", true);
        float x = prefs.getFloat("widget_" + i + "_x", 0);
        float y = prefs.getFloat("widget_" + i + "_y", 0);
        int w = prefs.getInt("widget_" + i + "_w", 200);
        int h = prefs.getInt("widget_" + i + "_h", 200);
        int opacity = prefs.getInt("widget_" + i + "_opacity", 191);
        int fontSize = prefs.getInt("widget_" + i + "_font_size", 15);
        int rows = prefs.getInt("widget_" + i + "_rows", 3);
        int columns = prefs.getInt("widget_" + i + "_columns", 3);
        String category = prefs.getString("widget_" + i + "_category", null);
        
        editor.putString(prefix + "widget_" + i + "_type", type);
        editor.putString(prefix + "widget_" + i + "_label", label);
        editor.putString(prefix + "widget_" + i + "_command", command);
        editor.putBoolean(prefix + "widget_" + i + "_horizontal", horizontal);
        editor.putFloat(prefix + "widget_" + i + "_x", x);
        editor.putFloat(prefix + "widget_" + i + "_y", y);
        editor.putInt(prefix + "widget_" + i + "_w", w);
        editor.putInt(prefix + "widget_" + i + "_h", h);
        editor.putInt(prefix + "widget_" + i + "_opacity", opacity);
        editor.putInt(prefix + "widget_" + i + "_font_size", fontSize);
        editor.putInt(prefix + "widget_" + i + "_rows", rows);
        editor.putInt(prefix + "widget_" + i + "_columns", columns);
        editor.putString(prefix + "widget_" + i + "_category", category);
        
        // Remove old keys
        editor.remove("widget_" + i + "_type");
        editor.remove("widget_" + i + "_label");
        editor.remove("widget_" + i + "_command");
        editor.remove("widget_" + i + "_horizontal");
        editor.remove("widget_" + i + "_x");
        editor.remove("widget_" + i + "_y");
        editor.remove("widget_" + i + "_w");
        editor.remove("widget_" + i + "_h");
        editor.remove("widget_" + i + "_opacity");
        editor.remove("widget_" + i + "_font_size");
        editor.remove("widget_" + i + "_rows");
        editor.remove("widget_" + i + "_columns");
        editor.remove("widget_" + i + "_category");
    }
    
    // Remove old count key and set migration flag
    editor.remove("widget_count");
    editor.putBoolean("migrated_to_v3", true);
    editor.apply();
    
    android.util.Log.d("WidgetLayout", "Legacy layout migrated to layouts/portrait/");
}
```

**Testing:**
- Create a test scenario with old "widget_count" format
- Verify migration copies to "layouts/portrait/"
- Verify old keys are removed
- Verify migration only happens once (check flag)

---

## Phase 6: Add "Reset to Default" Button in Edit Mode

**Goal:** Add a UI button in edit mode to reset the current orientation's layout to stock

**Files to modify:**
- `sys/android/forkfront/lib/res/layout/mainwindow.xml`
- `sys/android/forkfront/lib/res/values/strings.xml`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NH_State.java`

### 6a: Add button to layout

**File:** `sys/android/forkfront/lib/res/layout/mainwindow.xml`

**Find the emergency settings LinearLayout (around line 86) and add a new button:**

```xml
<LinearLayout
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="top|right"
    android:layout_marginTop="100dp"
    android:layout_marginRight="16dp"
    android:orientation="horizontal">

    <ImageButton
        android:id="@+id/btn_add_widget"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:src="@android:drawable/ic_menu_add"
        android:contentDescription="@string/add_widget"
        android:elevation="10dp" />

    <ImageButton
        android:id="@+id/btn_reset_layout"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_marginLeft="8dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:src="@android:drawable/ic_menu_revert"
        android:contentDescription="@string/reset_layout"
        android:visibility="gone"
        android:elevation="10dp" />

    <ImageButton
        android:id="@+id/btn_save_layout"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_marginLeft="8dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:src="@android:drawable/ic_menu_save"
        android:contentDescription="@string/save_layout"
        android:visibility="gone"
        android:elevation="10dp" />

    <ImageButton
        android:id="@+id/emergency_settings"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_marginLeft="8dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:src="@android:drawable/ic_menu_preferences"
        android:contentDescription="@string/settings"
        android:elevation="10dp" />
</LinearLayout>
```

### 6b: Add string resources

**File:** `sys/android/forkfront/lib/res/values/strings.xml`

**Add these string entries:**

```xml
<string name="add_widget">Add Widget</string>
<string name="reset_layout">Reset Layout to Default</string>
<string name="save_layout">Save Layout</string>
<string name="settings">Settings</string>
```

### 6c: Wire up button in NH_State

**File:** `sys/android/forkfront/lib/src/com/tbd/forkfront/NH_State.java`

**Find the `setContext()` method where buttons are wired up (around line 220-290) and add reset button handling:**

```java
// Wire up Reset Layout button (in setContext after other button setup)
View btnReset = activity.findViewById(R.id.btn_reset_layout);
if (btnReset != null) {
    btnReset.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mWidgetLayout != null) {
                // Show confirmation dialog
                new androidx.appcompat.app.AlertDialog.Builder(mActivity)
                    .setTitle("Reset Layout")
                    .setMessage("Reset this orientation's layout to default? Your customizations will be lost.")
                    .setPositiveButton("Reset", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            mWidgetLayout.resetToDefault();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        }
    });
}
```

**Update the `setEditMode()` method to show/hide the reset button:**

```java
public void setEditMode(boolean enabled)
{
    if (mWidgetLayout != null) {
        mWidgetLayout.setEditMode(enabled);
    }
    if (mActivity != null) {
        View btnAdd = mActivity.findViewById(R.id.btn_add_widget);
        if (btnAdd != null) {
            btnAdd.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        View btnReset = mActivity.findViewById(R.id.btn_reset_layout);
        if (btnReset != null) {
            btnReset.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        View btnSave = mActivity.findViewById(R.id.btn_save_layout);
        if (btnSave != null) {
            btnSave.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        View btnSettings = mActivity.findViewById(R.id.emergency_settings);
        if (btnSettings != null) {
            btnSettings.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
    }
    // Clear the edit_mode preference when disabling edit mode
    if (!enabled) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mApp).edit();
        editor.putBoolean("edit_mode", false);
        editor.apply();
    }
}
```

**Testing:**
- Enter edit mode and verify reset button appears
- Click reset button and verify confirmation dialog shows
- Confirm reset and verify layout reloads to stock
- Test that other orientations are unaffected by reset

---

## Phase 7: Testing & Validation

**Goal:** Comprehensive testing of the entire multi-orientation system

### Test Scenarios

1. **Fresh Install (No Previous Layout)**
   - Install app on clean device
   - Launch in portrait → verify stock portrait layout loads
   - Rotate to landscape → verify stock landscape layout loads
   - Verify widgets render at correct positions

2. **User Customization**
   - Enter edit mode in portrait
   - Move/resize widgets
   - Save layout
   - Exit edit mode → verify changes persist
   - Rotate to landscape → verify separate stock layout loads
   - Customize landscape layout and save
   - Rotate back to portrait → verify portrait customizations intact

3. **Reset to Default**
   - With customized layout, enter edit mode
   - Click "Reset to Default"
   - Confirm dialog
   - Verify stock layout loads
   - Verify other orientation still has customizations

4. **Migration from Legacy**
   - Create device with old "widget_count" format in SharedPreferences
   - Launch app
   - Verify migration occurs
   - Verify widgets appear in portrait
   - Verify landscape uses stock layout
   - Verify no duplicate widgets or corruption

5. **Orientation Change During Edit Mode**
   - Enter edit mode
   - Make changes (don't save)
   - Rotate device
   - Verify changes are auto-saved
   - Verify new orientation's layout loads
   - Verify edit mode remains active

6. **Different Screen Sizes**
   - Test on small phone (e.g., 1080x1920)
   - Test on large phone (e.g., 1440x2560)
   - Test on tablet if available
   - Verify stock layouts adapt correctly via anchor evaluation
   - Verify widget positions are reasonable on all devices

7. **Edge Cases**
   - Missing stock layout JSON → verify graceful fallback
   - Corrupted SharedPreferences → verify doesn't crash
   - Rapid orientation changes → verify no race conditions
   - Empty stock layout → verify user can add widgets manually

### Validation Checklist

- [ ] Stock layouts load on first launch
- [ ] User customizations save per-orientation
- [ ] Orientation changes reload correct layout
- [ ] Reset to default works correctly
- [ ] Migration from legacy format succeeds
- [ ] Edit mode persists across rotation
- [ ] No crashes or corruption
- [ ] Layouts look good on different screen sizes
- [ ] Performance is acceptable (no lag on rotation)

---

## Future Enhancements (Not in this plan)

These can be added later without major architectural changes:

1. **Tablet-Specific Layouts**
   - Add `tablet_portrait.json` and `tablet_landscape.json`
   - Update `LayoutConfiguration` to detect tablets

2. **Foldable Support**
   - Detect inner/outer screen
   - Support different layouts for folded/unfolded states

3. **Export/Import Layouts**
   - Allow users to export customizations as JSON
   - Import layouts from other users or backups

4. **Layout Presets**
   - Ship multiple stock layout variations (minimal, advanced, etc.)
   - Let users choose which stock layout to use as base

5. **Visual Layout Editor**
   - Grid/snap-to-grid in edit mode
   - Alignment guides
   - Widget templates

---

## Notes for Implementation

- **Build incrementally**: Each phase can be developed and tested independently
- **Test frequently**: Use `adb logcat` to monitor log output from WidgetLayout and NH_State
- **Backward compatibility**: The migration logic ensures existing users won't lose layouts
- **Stock layouts are suggestions**: Users can always fully customize or start from scratch
- **Keep it simple**: Avoid over-engineering—the two-tiered system is intentionally straightforward

## Implementation Order

If implementing in separate sessions:

1. **Phase 1** (LayoutConfiguration) - Foundation, can be done standalone
2. **Phase 2** (Stock layouts + evaluator) - Can be developed and tested independently
3. **Phase 3** (WidgetLayout changes) - Core functionality, depends on 1 & 2
4. **Phase 4** (NH_State integration) - Small changes, depends on 3
5. **Phase 5** (Migration) - Important but can be done after 3-4 work
6. **Phase 6** (UI button) - Polish, can be done anytime after 3
7. **Phase 7** (Testing) - Should be done continuously, final pass at end

Each phase is designed to be completable in one focused development session.
