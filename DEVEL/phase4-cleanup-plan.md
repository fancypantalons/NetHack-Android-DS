# Phase 4 Cleanup Plan: Critical Bug Fixes

**Created**: 2026-04-11  
**Priority**: HIGH - Must complete before Phase 5  
**Estimated Effort**: 1-2 days  
**Derived From**: [Phase 4 Code Review](phase4-code-review.md)

---

## 1. Objective

Fix critical threading, initialization, and configuration issues identified in Phase 4 code review to ensure production-ready lifecycle and state management.

---

## 2. Critical Issues Summary

| Issue | Severity | File | Impact |
|-------|----------|------|--------|
| Reflection-based circular dependency | 🔴 CRITICAL | NetHackViewModel.java | Breaks with obfuscation, fragile |
| Race condition in runOnActivity() | 🔴 CRITICAL | NetHackViewModel.java | UI ops on background thread → crashes |
| Missing volatile on mCurrentActivity | 🔴 CRITICAL | NetHackViewModel.java | Cross-thread visibility issues |
| Missing ProGuard rules | 🔴 CRITICAL | proguard-rules.pro | Release builds will crash |
| Unsafe queue processing | ⚠️ HIGH | NetHackViewModel.java | Potential ANR/deadlock |
| Incomplete asset loading UI | ⚠️ MEDIUM | ForkFront.java | Poor UX during first launch |

---

## 3. Implementation Tasks

### Task 1: Fix Reflection-Based Initialization 🔴 CRITICAL

**Problem**: `NetHackViewModel.initialize()` uses reflection to inject handler after construction, creating fragile circular dependency.

**File**: `sys/android/forkfront/lib/src/com/tbd/forkfront/NetHackViewModel.java`

**Current Code** (lines 44-63):
```java
public void initialize(Application app, ByteDecoder decoder) {
    if (mNHState == null) {
        mNetHackIO = new NetHackIO(app, null, decoder);  // ⚠️ null handler!
        mNHState = new NH_State(app, decoder, mNetHackIO);
        mNHState.setViewModel(this);
        
        // ❌ Reflection - fragile!
        try {
            java.lang.reflect.Field handlerField = NetHackIO.class.getDeclaredField("mNhHandler");
            handlerField.setAccessible(true);
            handlerField.set(mNetHackIO, mNHState.getNhHandler());
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject NhHandler into NetHackIO", e);
        }
    }
}
```

**Solution**: Add two-phase initialization to NetHackIO.

#### Step 1.1: Add setHandler() method to NetHackIO

**File**: `sys/android/forkfront/lib/src/com/tbd/forkfront/NetHackIO.java`

Add after constructor:
```java
/**
 * Set the handler for JNI callbacks.
 * Must be called before start() and can only be called once.
 * 
 * @param handler NH_Handler implementation for callbacks
 * @throws IllegalStateException if handler already set or thread already started
 */
public void setHandler(NH_Handler handler) {
    if (mNhHandler != null) {
        throw new IllegalStateException("Handler already set");
    }
    if (mThread.isAlive()) {
        throw new IllegalStateException("Cannot set handler after thread started");
    }
    mNhHandler = handler;
}
```

Also update constructor to allow null handler:
```java
public NetHackIO(Application app, NH_Handler nhHandler, ByteDecoder decoder) {
    mNhHandler = nhHandler;  // Allow null initially
    // ... rest of constructor
}
```

#### Step 1.2: Update NetHackViewModel to use setHandler()

**File**: `sys/android/forkfront/lib/src/com/tbd/forkfront/NetHackViewModel.java`

Replace reflection code (lines 44-63):
```java
public void initialize(Application app, ByteDecoder decoder) {
    if (mNHState == null) {
        Log.d(TAG, "Initializing NetHackViewModel with Application context");
        
        // Create NetHackIO with null handler initially
        mNetHackIO = new NetHackIO(app, null, decoder);
        
        // Create NH_State which creates the handler
        mNHState = new NH_State(app, decoder, mNetHackIO);
        
        // Set ViewModel reference for deferred UI operations
        mNHState.setViewModel(this);
        
        // Inject handler using proper API (not reflection)
        mNetHackIO.setHandler(mNHState.getNhHandler());
    } else {
        Log.d(TAG, "NetHackViewModel already initialized, skipping");
    }
}
```

**Testing**:
- Verify app starts without crashes
- Verify gameplay works (JNI callbacks function)
- Test rotation during gameplay

---

### Task 2: Fix Threading in runOnActivity() 🔴 CRITICAL

**Problem**: UI operations execute directly on background thread instead of posting to UI thread.

**File**: `sys/android/forkfront/lib/src/com/tbd/forkfront/NetHackViewModel.java`

**Current Code** (lines 114-124):
```java
public void runOnActivity(Runnable operation) {
    if (mCurrentActivity != null && !mCurrentActivity.isFinishing()) {
        operation.run();  // ❌ Runs on JNI callback thread!
    } else {
        synchronized (mPendingUIOperations) {
            mPendingUIOperations.add(operation);
        }
    }
}
```

**Solution**: Always post to UI thread.

**Replace with**:
```java
/**
 * Run an operation that requires an Activity context.
 * Always posts to UI thread for thread safety.
 * If an Activity is currently attached and not finishing, posts immediately.
 * Otherwise, queues the operation until an Activity becomes available.
 *
 * @param operation Runnable to execute on Activity (will run on UI thread)
 */
public void runOnActivity(Runnable operation) {
    synchronized (this) {
        if (mCurrentActivity != null && !mCurrentActivity.isFinishing()) {
            // Post to UI thread even if already attached
            mCurrentActivity.runOnUiThread(operation);
            Log.d(TAG, "Posted UI operation to current Activity");
        } else {
            // Queue for when Activity becomes available
            synchronized (mPendingUIOperations) {
                mPendingUIOperations.add(operation);
                Log.d(TAG, "Queued UI operation (queue depth: " + mPendingUIOperations.size() + ")");
            }
        }
    }
}
```

**Testing**:
- Verify no threading errors in logcat
- Test dialogs (yn_function, getline) during gameplay
- Test rotation while dialog is showing
- Test backgrounding app during dialog wait

---

### Task 3: Add Memory Barriers 🔴 CRITICAL

**Problem**: `mCurrentActivity` is written on UI thread but read on JNI callback thread without synchronization.

**File**: `sys/android/forkfront/lib/src/com/tbd/forkfront/NetHackViewModel.java`

**Current Code** (line 29):
```java
private AppCompatActivity mCurrentActivity;
```

**Solution**: Add volatile keyword.

**Replace with**:
```java
private volatile AppCompatActivity mCurrentActivity;  // Volatile for cross-thread visibility
```

**Also update attachActivity() and detachActivity()** to use synchronized blocks:

```java
/**
 * Attach an Activity to the ViewModel.
 * Called when Activity is created or recreated (e.g., after rotation).
 * Processes any pending UI operations that were queued while no Activity was available.
 *
 * @param activity Current Activity instance
 */
public void attachActivity(AppCompatActivity activity) {
    Log.d(TAG, "Attaching Activity: " + activity.getClass().getSimpleName());
    
    synchronized (this) {
        mCurrentActivity = activity;
        
        if (mNHState != null) {
            mNHState.setContext(activity);
        }
    }
    
    // Process pending operations without holding lock (prevents deadlock)
    List<Runnable> pending = new ArrayList<>();
    synchronized (mPendingUIOperations) {
        pending.addAll(mPendingUIOperations);
        mPendingUIOperations.clear();
    }
    
    int queueDepth = pending.size();
    if (queueDepth > 0) {
        Log.d(TAG, "Processing " + queueDepth + " pending UI operations");
    }
    
    // Execute on UI thread without holding any locks
    for (Runnable op : pending) {
        activity.runOnUiThread(op);
    }
}

/**
 * Detach the current Activity from the ViewModel.
 * Called when Activity is paused (e.g., going to background).
 * UI operations will be queued until a new Activity is attached.
 */
public void detachActivity() {
    synchronized (this) {
        if (mCurrentActivity != null) {
            Log.d(TAG, "Detaching Activity: " + mCurrentActivity.getClass().getSimpleName());
            mCurrentActivity = null;
        }
    }
}
```

**Add import**:
```java
import java.util.ArrayList;
import java.util.List;
```

**Testing**:
- Run with strict mode enabled
- Test rapid attach/detach cycles
- Verify no stale Activity references

---

### Task 4: Add ProGuard Rules 🔴 CRITICAL

**Problem**: No ProGuard rules to prevent obfuscation issues.

**Note**: If Task 1 is completed (removing reflection), this becomes less critical but still good practice.

**File**: `sys/android/forkfront/lib/proguard-rules.pro`

**Create or update file with**:
```proguard
# Keep NetHack JNI callback methods
-keepclassmembers class com.tbd.forkfront.NetHackIO {
    public void ynFunction(...);
    public void getLine(...);
    public void display(...);
    public void printGlyph(...);
    public void clearGlyph(...);
    public void printTile(...);
    public void putString(...);
    # ... add all JNI callback methods
}

# Keep NH_Handler interface (called from native code)
-keep interface com.tbd.forkfront.NH_Handler {
    *;
}

# Keep NH_State handler implementation (called from native code)
-keepclassmembers class com.tbd.forkfront.NH_State {
    com.tbd.forkfront.NH_Handler getNhHandler();
}

# If keeping reflection (not recommended):
# -keepclassmembers class com.tbd.forkfront.NetHackIO {
#     private com.tbd.forkfront.NH_Handler mNhHandler;
# }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
```

**Also verify in** `lib/build.gradle`:
```groovy
buildTypes {
    release {
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

**Testing**:
- Build release APK with ProGuard enabled
- Test full gameplay flow
- Verify JNI callbacks work
- Check for any missing class warnings

---

### Task 5: Fix Queue Processing Safety ⚠️ HIGH

**Problem**: Queue is processed while holding lock, could cause ANR or deadlock.

**File**: `sys/android/forkfront/lib/src/com/tbd/forkfront/NetHackViewModel.java`

**Already fixed in Task 3** - The updated `attachActivity()` method drains queue into local list before processing.

**Verification**:
- Ensure lock is released before executing operations
- Add logging to track queue processing time
- Test with many queued operations (simulate long background time)

---

### Task 6: Complete Asset Loading UI ⚠️ MEDIUM

**Problem**: ProgressDialog was removed but Material progress indicator was never added.

#### Step 6.1: Add Progress UI to Layout

**File**: `sys/android/forkfront/lib/res/layout/mainwindow.xml`

Add before closing `</FrameLayout>`:
```xml
<!-- Asset Loading Overlay -->
<FrameLayout
    android:id="@+id/loading_overlay"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#CC000000"
    android:visibility="gone"
    android:clickable="true"
    android:focusable="true">
    
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:orientation="vertical"
        android:background="?attr/colorSurface"
        android:elevation="8dp"
        android:padding="@dimen/padding_large">
        
        <TextView
            android:id="@+id/loading_message"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Preparing NetHack content..."
            android:textAppearance="?attr/textAppearanceBodyLarge"
            android:layout_marginBottom="@dimen/padding_medium"/>
        
        <com.google.android.material.progressindicator.LinearProgressIndicator
            android:id="@+id/asset_progress"
            android:layout_width="300dp"
            android:layout_height="wrap_content"
            android:indeterminate="false"
            android:max="100"
            android:progress="0"/>
        
        <TextView
            android:id="@+id/progress_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/padding_small"
            android:textAppearance="?attr/textAppearanceBodySmall"
            android:text="0%"/>
    </LinearLayout>
</FrameLayout>
```

#### Step 6.2: Add Progress Callback Interface

**File**: `sys/android/forkfront/lib/src/com/tbd/forkfront/UpdateAssets.java`

Add interface:
```java
public interface ProgressListener {
    void onProgressUpdate(int current, int total);
}
```

Update constructor:
```java
private final ProgressListener mProgressListener;

public UpdateAssets(AppCompatActivity activity, Listener listener, ProgressListener progressListener) {
    // ... existing code ...
    mProgressListener = progressListener;
}

// Also add overload for backwards compatibility
public UpdateAssets(AppCompatActivity activity, Listener listener) {
    this(activity, listener, null);
}
```

Update load() method to call progress callback:
```java
private File load() {
    // ... existing code ...
    
    while((read = input.read(buffer, 0, buffer.length)) >= 0) {
        output.write(buffer, 0, read);
        mTotalRead += read;
        
        // Report progress
        if (mProgressListener != null && !mIsCancelled) {
            final int current = (int)mTotalRead;
            final int total = (int)mRequiredSpace;
            mMainHandler.post(() -> {
                if (!mIsCancelled && mProgressListener != null) {
                    mProgressListener.onProgressUpdate(current, total);
                }
            });
        }
    }
    
    // ... rest of method ...
}
```

#### Step 6.3: Update ForkFront to Use Progress UI

**File**: `sys/android/forkfront/lib/src/com/tbd/forkfront/ForkFront.java`

Update goodToGo():
```java
private void goodToGo() {
    // Get or create ViewModel
    mViewModel = new ViewModelProvider(this).get(NetHackViewModel.class);
    
    // ... decoder setup ...
    
    // Initialize ViewModel
    mViewModel.initialize(getApplication(), decoder);
    mViewModel.attachActivity(this);
    
    // Get progress UI elements
    View loadingOverlay = findViewById(R.id.loading_overlay);
    LinearProgressIndicator progressBar = findViewById(R.id.asset_progress);
    TextView progressText = findViewById(R.id.progress_text);
    
    // Start asset loading with progress callback
    UpdateAssets updateAssets = new UpdateAssets(
        this,
        onAssetsReady,
        (current, total) -> {
            if (progressBar != null && progressText != null) {
                int percentage = (total > 0) ? (int)((current * 100L) / total) : 0;
                progressBar.setMax(total);
                progressBar.setProgress(current);
                progressText.setText(percentage + "%");
                
                if (loadingOverlay != null && loadingOverlay.getVisibility() != View.VISIBLE) {
                    loadingOverlay.setVisibility(View.VISIBLE);
                }
            }
        }
    );
    updateAssets.execute((Void[])null);
}
```

Update onAssetsReady callback:
```java
private UpdateAssets.Listener onAssetsReady = new UpdateAssets.Listener() {
    @Override
    public void onAssetsReady(File path) {
        // Hide loading overlay
        View loadingOverlay = findViewById(R.id.loading_overlay);
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
        
        // ... existing logic to start engine ...
    }
};
```

Add import:
```java
import com.google.android.material.progressindicator.LinearProgressIndicator;
```

**Testing**:
- Fresh install (full asset copy) - verify progress updates
- Rotation during asset loading
- Very fast completion (cached assets)

---

## 4. Testing Plan

### 4.1 Pre-Fix Baseline
- [ ] Document current behavior with test device
- [ ] Enable strict mode to catch threading violations
- [ ] Take memory snapshot before fixes

### 4.2 Per-Task Testing
Each task includes specific testing steps. Complete them before moving to next task.

### 4.3 Integration Testing
After all fixes:
- [ ] Fresh install on clean device
- [ ] Full gameplay session (30+ minutes)
- [ ] Multiple rotation cycles during gameplay
- [ ] Background/foreground transitions
- [ ] Dialog interactions (yn_function, getline)
- [ ] Asset loading with progress
- [ ] Settings changes
- [ ] Save and quit
- [ ] Restore from saved game

### 4.4 Release Build Testing
- [ ] Build release APK with ProGuard enabled
- [ ] Verify no obfuscation warnings
- [ ] Full gameplay test on release build
- [ ] Check APK size (should not increase significantly)

### 4.5 Memory Leak Testing
- [ ] Use Android Studio Profiler
- [ ] Perform 10 rotation cycles
- [ ] Force GC and check for leaked Activities
- [ ] Monitor for growing heap during gameplay

---

## 5. Implementation Sequence

### Day 1: Critical Threading Fixes
1. ✅ **Task 1**: Fix reflection initialization (1-2 hours)
2. ✅ **Task 2**: Fix runOnActivity threading (30 min)
3. ✅ **Task 3**: Add memory barriers (30 min)
4. ✅ **Task 4**: Add ProGuard rules (30 min)
5. Test Tasks 1-4 integration (1 hour)

### Day 2: Polish & Verification
6. ✅ **Task 6**: Complete asset loading UI (2 hours)
7. Full integration testing (2 hours)
8. Memory leak analysis (1 hour)
9. Documentation updates (30 min)

---

## 6. Success Criteria

- [ ] No reflection usage in initialization
- [ ] All UI operations execute on UI thread
- [ ] No threading violations in strict mode
- [ ] Release build works with ProGuard enabled
- [ ] No Activity leaks detected in Profiler
- [ ] Asset loading shows progress UI
- [ ] All Phase 4 code review issues marked resolved
- [ ] Documentation updated with new patterns

---

## 7. Risk Assessment

### Low Risk
- Tasks 1-3 are straightforward refactorings with clear solutions
- Each task is independently testable
- Changes are localized to specific files

### Medium Risk
- ProGuard configuration may need iteration to get JNI callbacks working
- Progress UI requires Material Components dependency (should already be present)

### Mitigation
- Test each task independently before integration
- Keep git history clean for easy rollback
- Test release builds early to catch ProGuard issues

---

## 8. Documentation Updates

After completion, update:
- [ ] `DEVEL/phase4-code-review.md` - Mark issues as resolved
- [ ] `DEVEL/forkfront-modernization-p4-subplan.md` - Add "Cleanup" section
- [ ] `DEVEL/port-architecture.md` - Update threading model documentation
- [ ] Code comments in NetHackViewModel explaining initialization pattern

---

## 9. Future Considerations

After this cleanup is complete:
- Consider removing `android:configChanges` from manifest to fully exercise ViewModel benefits
- Evaluate if pending operation queue can be replaced with LiveData for cleaner architecture
- Add unit tests for NetHackViewModel lifecycle
- Consider migrating to Hilt for proper dependency injection (Phase 5+)

---

## 10. Files to Modify

- `sys/android/forkfront/lib/src/com/tbd/forkfront/NetHackViewModel.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/NetHackIO.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/ForkFront.java`
- `sys/android/forkfront/lib/src/com/tbd/forkfront/UpdateAssets.java`
- `sys/android/forkfront/lib/res/layout/mainwindow.xml`
- `sys/android/forkfront/lib/proguard-rules.pro`

**Total**: 6 files

---

**Created by**: Claude Sonnet 4.6  
**Review Status**: Ready for implementation  
**Priority**: Must complete before Phase 5
