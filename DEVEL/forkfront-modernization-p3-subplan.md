# Phase 3 Sub-plan: Rendering & Graphics Evolution

This sub-plan focuses on modernizing the map rendering system, migrating from synchronous UI-thread drawing to threaded rendering, implementing edge-to-edge display support, and improving tile scaling for modern devices.

## 1. Goal
Transform the `NHW_Map` rendering architecture from a legacy View-based synchronous drawing model to a modern, hardware-accelerated threaded rendering system that supports edge-to-edge displays, high-DPI screens, and foldable devices.

## 2. Current State Analysis

### 2.1 Rendering Architecture
*   **Current Implementation**: `NHW_Map.UI` extends `View` and renders in `onDraw(Canvas)` on the UI thread.
*   **Game Engine Thread**: NetHack engine runs on a separate thread (`NetHackIO.mThread`) and updates tile data via `printTile()`, then calls `invalidateTile()` to trigger redraws.
*   **Rendering Methods**:
    *   `drawTiles()`: Renders tile-based graphics from `Tileset` bitmaps
    *   `drawAscii()`: Renders text-mode ASCII graphics
    *   `drawCursor()`: Overlay for cursor position
    *   `drawBorder()`: Map border decoration
*   **Touch Handling**: Complex zoom/pan gesture system with pinch-to-zoom and drag panning
*   **Performance**: All rendering blocks UI thread; no double-buffering; potential jank during complex scenes

### 2.2 Display & Layout
*   **Window Insets**: No current implementation - app does not handle notches, punch-holes, or gesture navigation bars
*   **Layout**: Map added to `map_frame` FrameLayout (line 17-20 in `mainwindow.xml`)
*   **Status Bar**: `NHW_Status` sits above map; `mLockTopMargin` tracks its height for zoom calculations

### 2.3 Tile Scaling
*   **Current Approach**: Manual zoom via `mScale` and `mScaleCount` with exponential zoom (`ZOOM_BASE = 1.005`)
*   **Density Handling**: Basic `mDisplayDensity` factor applied to min/max tile sizes
*   **Constants**:
    *   `MIN_TILE_SIZE_FACTOR = 5` (5dp minimum tile size)
    *   `MAX_TILE_SIZE_FACTOR = 100` (100dp maximum tile size)
    *   `SELF_RADIUS_FACTOR = 25` (25dp touch target radius)
*   **Issues**: No adaptation for xxxhdpi (640dpi), foldables, or tablets; zoom limits may be too restrictive on large displays

## 3. Key Refactoring Steps

### 3.1 Migrate to SurfaceView for Threaded Rendering

#### 3.1.1 Rationale: SurfaceView vs TextureView
*   **SurfaceView Advantages**:
    *   Dedicated rendering surface with true double-buffering
    *   Can render on background thread without blocking UI
    *   Lower memory overhead than TextureView
    *   Better suited for continuous rendering (game loops)
*   **TextureView Advantages**:
    *   Behaves like a normal View (can animate, transform, fade)
    *   Easier integration with View hierarchy
*   **Recommendation**: Use **SurfaceView** - NetHack map rendering is continuous, high-frequency, and benefits from dedicated surface and thread separation. The map doesn't need View-level transformations.

#### 3.1.2 Implementation Strategy
1.  **Change UI class inheritance**: `private class UI extends SurfaceView implements SurfaceHolder.Callback`
2.  **Add SurfaceHolder lifecycle**:
    ```java
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mRenderingThread = new Thread(new RenderLoop());
        mIsRendering = true;
        mRenderingThread.start();
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Handle size changes, update mCanvasRect
    }
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mIsRendering = false;
        mRenderingThread.join(); // Wait for thread to finish
    }
    ```
3.  **Implement RenderLoop**:
    ```java
    private class RenderLoop implements Runnable {
        @Override
        public void run() {
            while (mIsRendering) {
                Canvas canvas = null;
                try {
                    canvas = mSurfaceHolder.lockCanvas();
                    if (canvas != null) {
                        synchronized (mTileLock) {
                            drawBorder(canvas);
                            if (isTTY())
                                drawAscii(canvas);
                            else
                                drawTiles(canvas);
                        }
                    }
                } finally {
                    if (canvas != null) {
                        mSurfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
                // Frame rate throttling (optional)
            }
        }
    }
    ```
4.  **Add thread synchronization**: Protect `mTiles[][]` access with locks since `printTile()` is called from NetHackIO thread and rendering happens on render thread
5.  **Remove invalidate() calls**: Replace with dirty-region tracking or continuous rendering
6.  **Update constructor**: Call `getHolder().addCallback(this)` and `setZOrderOnTop(false)`

#### 3.1.3 Challenges & Mitigation
*   **Challenge**: Touch events still arrive on UI thread but rendering is on background thread
    *   **Mitigation**: Keep touch handling on UI thread, use thread-safe mechanisms (AtomicBoolean, synchronized blocks) for state shared with render thread
*   **Challenge**: Increased complexity with thread lifecycle management
    *   **Mitigation**: Carefully handle surface destroyed/recreated during configuration changes; ensure render thread stops gracefully
*   **Challenge**: Testing is harder with asynchronous rendering
    *   **Mitigation**: Add instrumentation to verify rendering thread behavior; keep existing smoke tests functional

### 3.2 Implement WindowInsets for Edge-to-Edge Support

#### 3.2.1 System UI & Edge-to-Edge Setup
1.  **Update ForkFront.java Activity setup**:
    ```java
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        // Configure system bars
        WindowInsetsControllerCompat controller = 
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        
        setContentView(R.layout.mainwindow);
    }
    ```

2.  **Add WindowInsets dependencies** (already present in Phase 2):
    *   `androidx.core:core:1.12.0` (provides `WindowCompat`, `WindowInsetsCompat`)

#### 3.2.2 Handle Insets in Layout
1.  **Update mainwindow.xml root**:
    ```xml
    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:id="@+id/base_frame"
        android:fitsSystemWindows="true"
        ...>
    ```

2.  **Apply insets programmatically in NHW_Map**:
    ```java
    ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
        Insets systemBars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() | 
            WindowInsetsCompat.Type.displayCutout());
        
        // Adjust map view offset to avoid cutouts/bars
        mSystemInsetsTop = systemBars.top;
        mSystemInsetsBottom = systemBars.bottom;
        mSystemInsetsLeft = systemBars.left;
        mSystemInsetsRight = systemBars.right;
        
        // Recalculate view bounds and zoom limits
        updateViewBounds();
        
        return insets;
    });
    ```

3.  **Adjust rendering bounds**:
    *   Modify `onMeasure()` and `viewAreaChanged()` to account for system insets
    *   Ensure status bar (`NHW_Status`) remains visible below top insets
    *   Adjust `mLockTopMargin` calculation to include top system inset

#### 3.2.3 Testing Considerations
*   Test on devices with notches (Pixel 3+, modern Samsung devices)
*   Test with gesture navigation vs 3-button navigation
*   Test in landscape orientation on devices with side cutouts
*   Test on foldable devices in both folded and unfolded states

### 3.3 Refactor Tile Scaling for Modern Displays

#### 3.3.1 Density-Independent Scaling
1.  **Update scaling constants** to be density-bucket aware:
    ```java
    // Current: fixed factors
    private static final float MIN_TILE_SIZE_FACTOR = 5;
    private static final float MAX_TILE_SIZE_FACTOR = 100;
    
    // Proposed: density-adaptive
    private float getMinTileSizeDp() {
        // For xxxhdpi (4K phones), allow smaller tiles
        if (mDisplayDensity >= 4.0f) return 8.f;
        if (mDisplayDensity >= 3.0f) return 6.f;
        return 5.f;
    }
    
    private float getMaxTileSizeDp() {
        // For tablets and foldables, allow larger tiles
        if (mScreenSizeClass == Configuration.SCREENLAYOUT_SIZE_XLARGE) return 150.f;
        if (mScreenSizeClass == Configuration.SCREENLAYOUT_SIZE_LARGE) return 120.f;
        return 100.f;
    }
    ```

2.  **Detect screen size class** in `setContext()`:
    ```java
    Configuration config = context.getResources().getConfiguration();
    mScreenSizeClass = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
    ```

#### 3.3.2 Foldable & Multi-Window Support
1.  **Respond to configuration changes**: Ensure `setContext()` is called when device folds/unfolds
2.  **Adjust zoom on configuration change**:
    *   Preserve zoom level preference (`mScaleCount`) across configurations
    *   Recalculate absolute tile sizes when density or screen size changes
    *   Clamp to new min/max bounds if necessary

3.  **Add to AndroidManifest.xml** (in host app):
    ```xml
    <activity android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation">
    ```

#### 3.3.3 Tile Bitmap Scaling Quality
1.  **Review Tileset loading**: Ensure multiple density assets are supported (if available)
2.  **Add bilinear filtering option** for scaled tiles:
    ```java
    // In Tileset.drawTile()
    if (shouldUseBilinearFiltering()) {
        paint.setFilterBitmap(true);
        paint.setAntiAlias(true);
    }
    ```
3.  **Add user preference**: "Smooth tile scaling" toggle in Settings

## 4. Implementation Sequence

### Phase 3.1: SurfaceView Migration (Foundation) (**DONE**)
1.  **Create feature branch**: `phase3-surfaceview`
2.  **Refactor UI class to extend SurfaceView**:
    *   Implement `SurfaceHolder.Callback`
    *   Add render thread and loop
    *   Add thread synchronization for `mTiles` access
3.  **Update invalidate pattern**: Replace with dirty tracking or continuous rendering
4.  **Test basic rendering**: Verify map draws correctly, touch/zoom still works
5.  **Performance validation**: Measure frame rates, ensure no regressions

### Phase 3.2: WindowInsets Integration (**DONE**)
1.  **Update ForkFront Activity**: Enable edge-to-edge mode
2.  **Add insets listener to NHW_Map**: Capture system bars and cutout insets
3.  **Adjust rendering bounds**: Modify drawing to respect safe areas
4.  **Update status bar positioning**: Ensure proper spacing with top insets
5.  **Test across device types**: Notched phones, gesture nav, foldables

### Phase 3.3: Adaptive Tile Scaling (**DONE**)
1.  **Implement density-adaptive min/max**: Use screen density and size class
2.  **Add configuration change handling**: Respond to folds/unfolds, rotations
3.  **Add bilinear filtering option**: Improve scaling quality on modern displays
4.  **Update Settings UI**: Add "Smooth tile scaling" preference
5.  **Test zoom behavior**: Verify limits are appropriate across device types

### Phase 3.4: Validation & Cleanup
1.  **Run smoke tests**: Ensure all existing tests pass
2.  **Performance profiling**: Validate threaded rendering improves frame times
3.  **Edge case testing**: Rapid zoom, device rotation during gameplay, multi-window mode
4.  **Code cleanup**: Remove deprecated invalidate logic, document threading model
5.  **Update documentation**: Add architecture notes on render thread design

## 5. Success Criteria
*   Map rendering happens on dedicated thread, not blocking UI thread
*   Frame rate is stable 30-60 FPS during active gameplay
*   Map correctly adapts to system bars, notches, and cutouts on all tested devices
*   Tile scaling is appropriate for xxxhdpi phones, tablets, and foldables
*   No visible jank during zoom, pan, or configuration changes
*   All existing smoke tests pass
*   No new memory leaks or threading issues introduced

## 6. Risk Assessment

### High Risk
*   **Thread synchronization bugs**: Race conditions between NetHackIO thread, render thread, and UI thread could cause crashes or visual glitches
    *   **Mitigation**: Thorough code review, extensive testing, use of proven concurrency patterns

### Medium Risk
*   **SurfaceView lifecycle complexity**: Improper handling of surface creation/destruction could cause rendering failures during config changes
    *   **Mitigation**: Follow Android best practices, test configuration changes extensively
*   **Performance regression**: Improper render loop implementation could waste battery or cause stuttering
    *   **Mitigation**: Implement frame rate throttling, profile before/after

### Low Risk
*   **WindowInsets edge cases**: Some devices may have unusual cutout shapes or system bar behaviors
    *   **Mitigation**: Test on variety of devices, allow graceful degradation

## 7. Dependencies & Constraints
*   **JNI Compatibility**: Must maintain `winandroid.c` callback interface (no changes to JNI layer)
*   **Game Engine Thread**: NetHackIO thread model must remain unchanged
*   **Backward Compatibility**: API 21+ devices must still work (no new API requirements)
*   **No Kotlin**: All changes must be in Java (per project constraints)

## 8. Future Enhancements (Phase 4+)
*   Hardware-accelerated tile rendering with OpenGL ES or Vulkan
*   Dynamic tile sheet selection based on device DPI
*   Adaptive detail levels for very large/small displays
*   HDR support for modern displays
