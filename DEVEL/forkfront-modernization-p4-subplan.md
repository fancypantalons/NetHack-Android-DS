# Phase 4 Sub-plan: Lifecycle & State Management

This sub-plan focuses on modernizing state and lifecycle management by introducing ViewModel, replacing deprecated AsyncTask, and decoupling JNI callbacks from Activity instances to enable proper configuration change handling and process death recovery.

## 1. Goal
Transform the legacy static state management and manual lifecycle handling into a modern, lifecycle-aware architecture using AndroidX Lifecycle components, ensuring the NetHack engine thread and UI state survive configuration changes and are properly cleaned up when the user leaves the app.

## 2. Current State Analysis

### 2.1 State Management Architecture
*   **NH_State Storage**: Stored as a **static variable** in `ForkFront.java` (line 45)
*   **Context Handling**: Holds `AppCompatActivity mContext` reference, updated via `setContext()` on configuration changes
*   **Configuration Changes**: Activity declares extensive `configChanges` in manifest to handle rotations/resizes manually
*   **Lifecycle Coupling**: 
    *   `onCreate()`: Creates new `NH_State` if null, or calls `setContext()` if restoring
    *   `onDestroy()`: Calls `saveAndQuit()` and sets static `nhState = null`
    *   `onSaveInstanceState()`: Calls `saveState()` to trigger native save
*   **Problems**:
    *   Static state survives configuration changes but isn't lifecycle-aware
    *   Direct Activity references can leak
    *   No support for process death recovery
    *   Manual lifecycle management is error-prone
    *   State initialization is tightly coupled to Activity creation

### 2.2 NetHackIO & Engine Thread Management
*   **Thread Creation**: `NetHackIO` creates a native thread in constructor, starts it via `start(path)` (line 156)
*   **Thread Lifecycle**: Thread runs `RunNetHack()` JNI method until game ends, then calls `System.exit(0)` (line 253)
*   **Handler Usage**: Creates `Handler mHandler = new Handler()` on UI thread (line 150) for JNI callbacks
*   **JNI Callback Pattern**: Native code calls Java methods (e.g., `printTile()`, `ynFunction()`), which post `Runnable` objects to `mHandler` to execute on UI thread (lines 454-557)
*   **Problems**:
    *   Handler is created from Activity context implicitly (via main looper)
    *   No explicit lifecycle management for the native thread
    *   Native thread calls `System.exit(0)` which terminates entire process
    *   If Activity is destroyed, JNI callbacks may post to a dead context
    *   No mechanism to pause/resume or properly clean up the native thread
    *   Thread outlives Activity but has no lifecycle owner

### 2.3 UpdateAssets Background Task
*   **Implementation**: Extends deprecated `AsyncTask<Void, Void, Void>` (line 18)
*   **Lifecycle**: Created and executed in `goodToGo()` callback (line 147):
    ```java
    new UpdateAssets(this, onAssetsReady).execute((Void[])null);
    ```
*   **UI Updates**: Shows `ProgressDialog` in `onProgressUpdate()` (line 144-150)
*   **Callback Pattern**: Uses custom `Listener` interface to notify when assets are ready (line 20-23)
*   **Problems**:
    *   `AsyncTask` has been deprecated since API 30 (Android 11)
    *   `ProgressDialog` can leak if Activity is destroyed during asset copying
    *   Activity reference held during background operation
    *   No built-in support for lifecycle-aware cancellation
    *   Dialog dismissed in `onPostExecute()` without lifecycle checks (line 111-112)

### 2.4 Current Dependencies
From `sys/android/forkfront/lib/build.gradle`:
```groovy
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.preference:preference:1.2.1'
implementation 'androidx.fragment:fragment:1.6.2'
```
**Missing**: `androidx.lifecycle:lifecycle-viewmodel`, `androidx.lifecycle:lifecycle-livedata`

## 3. Key Refactoring Steps

### 3.1 Introduce ViewModel for State Management

#### 3.1.1 Create NetHackViewModel
Create a new `NetHackViewModel` class to manage `NetHackIO`, `NH_State`, and engine thread lifecycle:

```java
public class NetHackViewModel extends ViewModel {
    private NetHackIO mNetHackIO;
    private NH_State mNHState;
    private boolean mIsEngineStarted = false;
    private String mDataPath;
    
    public NetHackViewModel() {
        super();
        Log.print("NetHackViewModel created");
    }
    
    // Initialize state with Application context (survives Activity)
    public void initialize(Application app, ByteDecoder decoder) {
        if (mNHState == null) {
            // NetHackIO needs Application context, not Activity
            mNetHackIO = new NetHackIO(app, nhHandler, decoder);
            mNHState = new NH_State(app, decoder, mNetHackIO);
        }
    }
    
    // Update Activity context when Activity is created/recreated
    public void attachActivity(AppCompatActivity activity) {
        if (mNHState != null) {
            mNHState.setContext(activity);
        }
    }
    
    public void startEngine(String dataPath) {
        if (!mIsEngineStarted) {
            mDataPath = dataPath;
            mNetHackIO.start(dataPath);
            mIsEngineStarted = true;
        }
    }
    
    public NH_State getState() {
        return mNHState;
    }
    
    @Override
    protected void onCleared() {
        Log.print("NetHackViewModel cleared - saving and quitting");
        if (mNetHackIO != null) {
            mNetHackIO.saveAndQuit();
        }
        super.onCleared();
    }
}
```

#### 3.1.2 Refactor NH_State to Support Application Context
**Challenge**: `NH_State` currently expects `AppCompatActivity` for:
*   Creating windows and dialogs
*   Starting Settings activity
*   Accessing preferences

**Solution**: Split into lifecycle-agnostic logic and Activity-dependent UI operations:

1.  **Change Constructor Signature**:
    ```java
    // Old
    public NH_State(AppCompatActivity context, ByteDecoder decoder)
    
    // New
    public NH_State(Application app, ByteDecoder decoder, NetHackIO io)
    ```

2.  **Store Application Context**:
    ```java
    private Application mApp;  // Never leaks, survives Activity
    private AppCompatActivity mActivity;  // Updated via setContext()
    ```

3.  **Defer Activity-Dependent Operations**:
    *   Window creation can use Application context for most Views
    *   Dialog creation requires Activity - defer until `setContext()` is called
    *   Preference access uses `PreferenceManager.getDefaultSharedPreferences(mApp)`

4.  **Update setContext() to Handle Null**:
    ```java
    public void setContext(AppCompatActivity activity) {
        mActivity = activity;
        // Update all child components
        for(NH_Window w : mWindows)
            w.setContext(activity);
        mGetLine.setContext(activity);
        // ... etc
    }
    ```

#### 3.1.3 Refactor NetHackIO to Use Application Context
**Challenge**: `NetHackIO` constructor takes `AppCompatActivity` to access resources (line 143-147):

```java
public NetHackIO(AppCompatActivity context, NH_Handler nhHandler, ByteDecoder decoder) {
    mLibraryName = context.getResources().getString(R.string.libraryName);
    // ...
}
```

**Solution**: Change to accept `Application` (which extends `Context`):

```java
public NetHackIO(Application app, NH_Handler nhHandler, ByteDecoder decoder) {
    mLibraryName = app.getResources().getString(R.string.libraryName);
    // Handler still uses main looper, but not tied to Activity lifecycle
    mHandler = new Handler(Looper.getMainLooper());
    // ...
}
```

**Key Insight**: `Handler(Looper.getMainLooper())` works without Activity context - it posts to the main thread regardless of Activity state.

#### 3.1.4 Update ForkFront Activity to Use ViewModel

```java
public class ForkFront extends AppCompatActivity {
    private NetHackViewModel mViewModel;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ... existing setup ...
        
        // Get or create ViewModel
        mViewModel = new ViewModelProvider(this).get(NetHackViewModel.class);
        
        ensureReadWritePermissions(new RequestExternalStorageResult() {
            @Override
            public void onGranted() {
                goodToGo();
            }
            // ...
        });
    }
    
    private void goodToGo() {
        ByteDecoder decoder = /* ... */;
        
        // Initialize ViewModel (only happens once)
        mViewModel.initialize(getApplication(), decoder);
        
        // Attach current Activity context
        mViewModel.attachActivity(this);
        
        // Start asset loading
        new UpdateAssets(this, onAssetsReady).execute((Void[])null);
    }
    
    private UpdateAssets.Listener onAssetsReady = new UpdateAssets.Listener() {
        @Override
        public void onAssetsReady(File path) {
            // ... existing save dir creation ...
            
            // Start engine through ViewModel
            mViewModel.startEngine(path.getAbsolutePath());
            
            // Get state and configure
            NH_State nhState = mViewModel.getState();
            nhState.preferencesUpdated();
            nhState.updateVisibleState();
            // ...
        }
    };
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        NH_State nhState = mViewModel.getState();
        if (nhState != null) {
            nhState.onConfigurationChanged(newConfig);
        }
        super.onConfigurationChanged(newConfig);
    }
    
    @Override
    protected void onDestroy() {
        // ViewModel handles cleanup automatically via onCleared()
        super.onDestroy();
    }
    
    // Remove static nhState variable entirely
}
```

**Benefits**:
*   ViewModel survives configuration changes automatically
*   No static variables needed
*   `onCleared()` is called when Activity is truly finished (not just rotated)
*   Proper separation of lifecycle-agnostic state and Activity-bound UI

### 3.2 Replace AsyncTask with ExecutorService

#### 3.2.1 Create Modern UpdateAssets Implementation

Replace `AsyncTask` with `java.util.concurrent.ExecutorService` and lifecycle-aware progress callbacks:

```java
public class UpdateAssets {
    public interface Listener {
        void onAssetsReady(File path);
        void onError(String error);
    }
    
    public interface ProgressListener {
        void onProgressUpdate(int current, int total);
    }
    
    private final WeakReference<AppCompatActivity> mActivityRef;
    private final ExecutorService mExecutor;
    private final Handler mMainHandler;
    private final Listener mListener;
    private final ProgressListener mProgressListener;
    private volatile boolean mIsCancelled = false;
    
    // ... existing fields ...
    
    public UpdateAssets(AppCompatActivity activity, Listener listener, ProgressListener progressListener) {
        mActivityRef = new WeakReference<>(activity);
        mExecutor = Executors.newSingleThreadExecutor();
        mMainHandler = new Handler(Looper.getMainLooper());
        mListener = listener;
        mProgressListener = progressListener;
        // ... existing initialization ...
    }
    
    public void execute() {
        mExecutor.execute(() -> {
            File result = load();
            
            mMainHandler.post(() -> {
                if (!mIsCancelled) {
                    if (result != null) {
                        mListener.onAssetsReady(result);
                    } else {
                        mListener.onError(mError);
                    }
                }
            });
        });
    }
    
    public void cancel() {
        mIsCancelled = true;
        mExecutor.shutdownNow();
    }
    
    private File load() {
        // ... existing load logic, but replace publishProgress() with:
        if (mProgressListener != null && !mIsCancelled) {
            final int current = (int)mTotalRead;
            final int total = (int)mRequiredSpace;
            mMainHandler.post(() -> {
                if (!mIsCancelled) {
                    mProgressListener.onProgressUpdate(current, total);
                }
            });
        }
        // ...
    }
    
    // Remove AsyncTask methods (onPreExecute, onPostExecute, etc.)
}
```

#### 3.2.2 Replace ProgressDialog with Material ProgressIndicator

**Problem**: `ProgressDialog` is deprecated and can leak.

**Solution**: Use Material Design 3 `LinearProgressIndicator` in layout:

1.  **Add to `mainwindow.xml`**:
    ```xml
    <FrameLayout
        android:id="@+id/loading_overlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#80000000"
        android:visibility="gone"
        android:clickable="true">
        
        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:orientation="vertical"
            android:background="@drawable/dialog_background"
            android:padding="24dp">
            
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Preparing content..."
                android:textAppearance="?attr/textAppearanceBodyLarge"
                android:layout_marginBottom="16dp"/>
            
            <com.google.android.material.progressindicator.LinearProgressIndicator
                android:id="@+id/asset_progress"
                android:layout_width="300dp"
                android:layout_height="wrap_content"
                android:indeterminate="false"/>
        </LinearLayout>
    </FrameLayout>
    ```

2.  **Update ForkFront to Use Layout-Based Progress**:
    ```java
    private void goodToGo() {
        // ...
        View loadingOverlay = findViewById(R.id.loading_overlay);
        LinearProgressIndicator progressBar = findViewById(R.id.asset_progress);
        
        UpdateAssets updateAssets = new UpdateAssets(
            this,
            onAssetsReady,
            (current, total) -> {
                if (progressBar != null) {
                    progressBar.setMax(total);
                    progressBar.setProgress(current);
                    if (loadingOverlay.getVisibility() != View.VISIBLE) {
                        loadingOverlay.setVisibility(View.VISIBLE);
                    }
                }
            }
        );
        updateAssets.execute();
    }
    
    private UpdateAssets.Listener onAssetsReady = new UpdateAssets.Listener() {
        @Override
        public void onAssetsReady(File path) {
            View loadingOverlay = findViewById(R.id.loading_overlay);
            if (loadingOverlay != null) {
                loadingOverlay.setVisibility(View.GONE);
            }
            // ... existing logic ...
        }
        
        @Override
        public void onError(String error) {
            View loadingOverlay = findViewById(R.id.loading_overlay);
            if (loadingOverlay != null) {
                loadingOverlay.setVisibility(View.GONE);
            }
            showErrorDialog(error);
        }
    };
    ```

**Benefits**:
*   No dialog lifecycle management needed
*   Progress indicator is part of layout, can't leak
*   Survives configuration changes naturally
*   Modern Material Design 3 appearance

### 3.3 Decouple JNI Callbacks from Activity Instances

#### 3.3.1 Analysis of JNI Callback Flow
Current flow (example with `ynFunction`):
1.  Native code calls `NetHackIO.ynFunction(byte[] question, byte[] choices, int def)` (line 542)
2.  Java method posts Runnable to `mHandler` (line 546-555)
3.  Runnable calls `mNhHandler.ynFunction()` on UI thread (line 552)
4.  NH_Handler implementation calls `mQuestion.show(mContext, ...)` (line 573)
5.  `NH_Question` shows dialog using Activity context

**Problem Points**:
*   `mHandler` posts to main looper (OK - survives Activity)
*   `mNhHandler` is `NH_Handler` interface implemented in `NH_State` (OK - in ViewModel)
*   `mQuestion.show()` requires Activity context for dialog creation (PROBLEM)

#### 3.3.2 Solution: Lifecycle-Aware Dialog Queue

**Strategy**: Queue JNI callbacks that require Activity context, process when Activity is available.

1.  **Create Pending UI Operation Queue in NetHackViewModel**:
    ```java
    public class NetHackViewModel extends ViewModel {
        private final Queue<Runnable> mPendingUIOperations = new LinkedList<>();
        private AppCompatActivity mCurrentActivity;
        
        public void attachActivity(AppCompatActivity activity) {
            mCurrentActivity = activity;
            if (mNHState != null) {
                mNHState.setContext(activity);
            }
            
            // Process pending operations
            synchronized (mPendingUIOperations) {
                while (!mPendingUIOperations.isEmpty()) {
                    Runnable op = mPendingUIOperations.poll();
                    if (op != null) {
                        op.run();
                    }
                }
            }
        }
        
        public void detachActivity() {
            mCurrentActivity = null;
        }
        
        public void runOnActivity(Runnable operation) {
            if (mCurrentActivity != null && !mCurrentActivity.isFinishing()) {
                operation.run();
            } else {
                // Queue for when Activity becomes available
                synchronized (mPendingUIOperations) {
                    mPendingUIOperations.add(operation);
                }
            }
        }
    }
    ```

2.  **Update NH_State to Use Deferred Operations**:
    ```java
    public class NH_State {
        private NetHackViewModel mViewModel;  // Add reference
        
        private NH_Handler NhHandler = new NH_Handler() {
            @Override
            public void ynFunction(String question, byte[] choices, int def) {
                // Defer to when Activity is available
                mViewModel.runOnActivity(() -> {
                    mQuestion.show(mActivity, question, choices, def);
                });
            }
            
            @Override
            public void getLine(String title, int nMaxChars, boolean showLog) {
                mViewModel.runOnActivity(() -> {
                    if (showLog)
                        mGetLine.show(mActivity, mMessage.getLogLine(2) + title, nMaxChars);
                    else
                        mGetLine.show(mActivity, title, nMaxChars);
                });
            }
            
            // Non-Activity operations can run immediately
            @Override
            public void printTile(int wid, int x, int y, int tile, int ch, int col, int special) {
                mMap.printTile(x, y, tile, ch, col, special);
            }
            // ...
        };
    }
    ```

3.  **Update ForkFront Lifecycle Management**:
    ```java
    @Override
    protected void onResume() {
        super.onResume();
        if (mViewModel != null) {
            mViewModel.attachActivity(this);
        }
    }
    
    @Override
    protected void onPause() {
        if (mViewModel != null) {
            mViewModel.detachActivity();
        }
        super.onPause();
    }
    ```

**Benefits**:
*   JNI callbacks never crash due to missing Activity
*   Dialogs appear when Activity is available
*   User can rotate device during dialog, operation completes after rotation
*   Graceful handling of background/foreground transitions

#### 3.3.3 Alternative: LiveData for UI Events

**For read-only UI updates** (like status updates), use LiveData:

```java
public class NetHackViewModel extends ViewModel {
    private final MutableLiveData<String> mRawPrintMessage = new MutableLiveData<>();
    
    public LiveData<String> getRawPrintMessage() {
        return mRawPrintMessage;
    }
    
    // Called from JNI callback
    void postRawPrint(String message) {
        mRawPrintMessage.postValue(message);
    }
}

// In ForkFront:
@Override
protected void onCreate(Bundle savedInstanceState) {
    // ...
    mViewModel.getRawPrintMessage().observe(this, message -> {
        NH_State state = mViewModel.getState();
        if (state != null) {
            state.handleRawPrint(message);
        }
    });
}
```

**When to Use**:
*   LiveData: For one-way data flow (engine → UI updates)
*   Runnable Queue: For two-way operations (show dialog, wait for response)

## 4. Implementation Sequence

### Phase 4.1: Add Lifecycle Dependencies
1.  **Update `forkfront/lib/build.gradle`**:
    ```groovy
    dependencies {
        implementation 'androidx.lifecycle:lifecycle-viewmodel:2.7.0'
        implementation 'androidx.lifecycle:lifecycle-livedata:2.7.0'
        implementation 'androidx.lifecycle:lifecycle-runtime:2.7.0'
        // ... existing dependencies
    }
    ```
2.  **Sync Gradle** and verify build still works
3.  **Run smoke tests** to establish baseline

### Phase 4.2: Create NetHackViewModel (Foundation)
1.  **Create `NetHackViewModel.java`**:
    *   Extend `ViewModel`
    *   Add `initialize()`, `attachActivity()`, `detachActivity()` methods
    *   Add `onCleared()` to call `saveAndQuit()`
    *   Add pending operation queue mechanism
2.  **Test ViewModel Lifecycle**:
    *   Add logging to verify creation, attachment, clearing
    *   Rotate device, verify ViewModel survives
    *   Back out of app, verify `onCleared()` is called

### Phase 4.3: Refactor NH_State for Application Context
1.  **Update NH_State Constructor**:
    *   Change parameter from `AppCompatActivity` to `Application`
    *   Add `NetHackIO` parameter (instead of creating internally)
    *   Store both `mApp` and `mActivity` references
2.  **Audit All Context Usage**:
    *   Search for `mContext.` in `NH_State.java`
    *   Identify which operations need Activity vs Application
    *   Update to use `mApp` or `mActivity` as appropriate
3.  **Update Child Components**:
    *   Ensure all window classes can handle `setContext(null)` gracefully
    *   Defer dialog creation until Activity is available
4.  **Test State Management**:
    *   Verify windows still render correctly
    *   Test configuration changes
    *   Test background/foreground transitions

### Phase 4.4: Refactor NetHackIO for Application Context
1.  **Update Constructor**:
    *   Change `AppCompatActivity context` to `Application app`
    *   Update resource access to use `app.getResources()`
    *   Change `mHandler = new Handler()` to `mHandler = new Handler(Looper.getMainLooper())`
2.  **Test Thread Safety**:
    *   Verify JNI callbacks still post correctly
    *   Test that Handler works without Activity
    *   Rotate device during gameplay, verify no crashes
3.  **Update Documentation**:
    *   Add comments explaining Handler lifecycle
    *   Document thread safety guarantees

### Phase 4.5: Integrate ViewModel into ForkFront Activity
1.  **Update `onCreate()`**:
    *   Add `ViewModelProvider` to get or create ViewModel
    *   Call `mViewModel.initialize()` with Application context
    *   Call `mViewModel.attachActivity(this)`
2.  **Update Lifecycle Callbacks**:
    *   Add `attachActivity()` to `onResume()`
    *   Add `detachActivity()` to `onPause()`
    *   Remove manual `saveAndQuit()` from `onDestroy()`
3.  **Remove Static State**:
    *   Delete `private static NH_State nhState;`
    *   Update all references to use `mViewModel.getState()`
4.  **Test Full Integration**:
    *   Launch app, verify gameplay works
    *   Rotate device, verify state survives
    *   Background/foreground app, verify no crashes
    *   Kill and restart app, verify proper initialization

### Phase 4.6: Implement JNI Callback Decoupling
1.  **Add Pending Operation Queue**:
    *   Implement `runOnActivity()` in ViewModel
    *   Add queue processing in `attachActivity()`
2.  **Update NH_Handler Implementation**:
    *   Wrap Activity-dependent callbacks with `runOnActivity()`
    *   Keep non-Activity callbacks immediate
3.  **Test Edge Cases**:
    *   Rotate during dialog display
    *   Background app during dialog wait
    *   Rapid configuration changes
4.  **Add Telemetry**:
    *   Log when operations are queued
    *   Track queue depth (should be small in normal use)

### Phase 4.7: Replace AsyncTask with ExecutorService
1.  **Create New UpdateAssets Implementation**:
    *   Replace `AsyncTask` with `ExecutorService`
    *   Add `WeakReference` for Activity
    *   Implement cancellation support
    *   Add progress callback interface
2.  **Add Progress UI to Layout**:
    *   Add loading overlay to `mainwindow.xml`
    *   Add Material `LinearProgressIndicator`
3.  **Update ForkFront Usage**:
    *   Change `execute()` call pattern
    *   Implement progress callback
    *   Update error handling
4.  **Test Asset Loading**:
    *   Fresh install (full asset copy)
    *   Update scenario (partial copy)
    *   Rotation during loading
    *   Low storage error handling
5.  **Remove Old Code**:
    *   Delete `ProgressDialog` references
    *   Remove `AsyncTask` import
    *   Clean up deprecated methods

### Phase 4.8: Validation & Cleanup
1.  **Run Full Test Suite**:
    *   Smoke tests must pass
    *   Manual gameplay testing
    *   Stress test configuration changes
2.  **Memory Leak Analysis**:
    *   Use Android Studio Profiler
    *   Verify no Activity leaks after rotation
    *   Check Handler doesn't hold Activity references
3.  **Code Review Checklist**:
    *   No static Activity references
    *   All JNI callbacks are lifecycle-aware
    *   No AsyncTask usage remains
    *   ViewModel properly manages engine thread
4.  **Documentation**:
    *   Update architecture diagrams
    *   Document ViewModel lifecycle
    *   Add comments on thread safety

## 5. Success Criteria
*   ✅ `NetHackViewModel` manages `NH_State` and `NetHackIO` lifecycle
*   ✅ No static Activity or Context references in codebase
*   ✅ Activity rotation preserves game state without crashes
*   ✅ JNI callbacks work correctly during configuration changes
*   ✅ Asset loading completes successfully even during rotation
*   ✅ No `AsyncTask` usage remains
*   ✅ No `ProgressDialog` usage remains
*   ✅ Memory profiler shows no Activity leaks
*   ✅ Engine thread is properly cleaned up when user exits app
*   ✅ All smoke tests pass
*   ✅ Manual testing confirms no regressions in gameplay

## 6. Risk Assessment

### High Risk
*   **Thread Synchronization with ViewModel**: The NetHack engine thread, render thread, and UI thread all interact. Adding ViewModel layer could introduce race conditions.
    *   **Mitigation**: Careful review of all thread interactions; use proven concurrency patterns; extensive testing
*   **JNI Callback Timing**: Queueing UI operations could cause game to hang if waiting for user input that's stuck in queue.
    *   **Mitigation**: Process queue eagerly in `onResume()`; add timeout mechanisms; log queue depth

### Medium Risk
*   **Context Refactoring Errors**: Changing from Activity to Application context could break existing functionality that implicitly depends on Activity.
    *   **Mitigation**: Audit all context usage; thorough testing of all UI components; smoke tests
*   **Configuration Change Edge Cases**: Complex interactions between ViewModel, Activity lifecycle, and native thread could fail in rare scenarios.
    *   **Mitigation**: Stress testing with rapid rotations, background/foreground transitions; device testing
*   **Asset Loading Cancellation**: ExecutorService-based loading needs proper cancellation handling.
    *   **Mitigation**: Test Activity destruction during loading; verify no file corruption

### Low Risk
*   **LiveData Learning Curve**: Team may be unfamiliar with LiveData patterns.
    *   **Mitigation**: Good documentation; start with simple read-only use cases; code review
*   **Material Components**: New progress indicator may have styling issues.
    *   **Mitigation**: Test across Android versions; fallback to simpler progress bar if needed

## 7. Dependencies & Constraints
*   **JNI Compatibility**: Must maintain `winandroid.c` callback interface (no changes to native layer)
*   **No Kotlin**: All changes must be in Java (per project constraints)
*   **Backward Compatibility**: API 21+ devices must still work
*   **Configuration Change Handling**: Must preserve existing `configChanges` manifest behavior
*   **Game State Preservation**: Native save/load mechanism must remain functional
*   **No Process Death Recovery**: Phase 4 focuses on configuration changes; full process death recovery (save/restore from Bundle) is a future enhancement

## 8. Future Enhancements (Phase 5+)
*   Process death recovery via `SavedStateHandle`
*   Migration to Kotlin with Coroutines instead of ExecutorService
*   WorkManager for long-running asset operations
*   Hilt dependency injection for ViewModel factory
*   DataStore to replace SharedPreferences
*   Proper app backgrounding (pause game thread instead of `System.exit(0)`)
*   Multiplayer support with shared ViewModel state
