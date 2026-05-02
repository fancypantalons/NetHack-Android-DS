# NetHack Porting Layer - Game Loop Integration

When and why window port functions are called during NetHack gameplay.

## Startup Sequence

```
main()                         [sys/*/main.c - platform-specific]
  ↓
init_nhwindows(&argc, argv)   ← Port initializes windowing system
  ↓
[Process command-line arguments]
  ↓
player_selection()             ← Port may provide GUI for role/race selection
askname()                      ← Port gets player name
  ↓
display_gamewindows()          ← Create standard windows (MESSAGE, MAP, status)
  ↓
newgame() or dorestore()      ← Start new game or restore from save
  ↓
welcome(TRUE)                  ← Print welcome message
  ↓
moveloop(FALSE)                ← MAIN GAME LOOP (runs until game ends)
```

### display_gamewindows()

Creates the three standard windows:

```c
void display_gamewindows(void) {
    // Message window - persists entire game
    WIN_MESSAGE = create_nhwindow(NHW_MESSAGE);
    
    // Status - modern field-based OR legacy window
    if (VIA_WINDOWPORT()) {
        status_initialize(0);  // Modern: field-based status
    } else {
        WIN_STATUS = create_nhwindow(NHW_STATUS);  // Legacy: string-based
    }
    
    // Map window - persists entire game
    WIN_MAP = create_nhwindow(NHW_MAP);
    
    // Inventory window (for persistent inventory ports)
    WIN_INVEN = create_nhwindow(NHW_MENU);
    
    // Display them
    display_nhwindow(WIN_MESSAGE, FALSE);
    display_nhwindow(WIN_STATUS, FALSE);
    display_nhwindow(WIN_MAP, FALSE);
}
```

**VIA_WINDOWPORT()**: Returns TRUE if port supports modern status (WC2_HILITE_STATUS or WC2_FLUSH_STATUS set).

**Key Point**: WIN_MESSAGE, WIN_MAP, and status system are created very early, before gameplay begins.

## The Main Game Loop: moveloop()

Heart of NetHack is `moveloop()` in `src/allmain.c`. Runs an infinite loop processing turns.

### High-Level Structure

```c
void moveloop(boolean resuming) {
    // Initialization
    decl_init();
    monst_init();
    objects_init();
    
    // Setup
    update_inventory();
    context.botlx = TRUE;
    
    for (;;) {  // ← INFINITE LOOP
        // CRITICAL: Called EVERY iteration (potentially 100s/sec)
        get_nh_event();  // ← MUST NOT BLOCK!
        
        if (context.move) {
            // Turn happened - process game logic
            // Monster movement, timeouts, regeneration, etc.
        }
        
        // Update displays
        if (context.botl || context.botlx) {
            bot();           // Update status
            curs_on_u();     // Position cursor on player
        }
        
        // Process player input
        if (multi > 0) {
            // Multi-turn command in progress (search, eat, etc.)
            domove();
        } else if (multi == 0) {
            rhack(NULL);     // ← Get & process player command (blocks in nhgetch)
        }
        
        // Update map display periodically
        display_nhwindow(WIN_MAP, FALSE);
    }
}
```

### Critical Timing: Every Loop Iteration

**Every single iteration** (potentially hundreds of times per second):

1. `get_nh_event()` - ALWAYS called first
2. Game logic (if turn happened)
3. Status update (`bot()`) if needed
4. Get player input (`rhack()`) - blocks in nhgetch()
5. Map display update (periodically)

## get_nh_event() - The Heart of the Event Loop

```c
for (;;) {
    get_nh_event();  // ← Called EVERY iteration
    // ... rest of loop
}
```

**Purpose**: Process windowing system events

**When Called**: Every iteration of main loop (potentially hundreds of times per second)

**CRITICAL RULE**: **MUST NOT BLOCK** - return immediately

### What It Does

**TTY**: No-op (returns immediately)

**GUI Ports**: Process:
- Window exposure/redraw events
- Window resize events
- Mouse movements (don't consume, just note)
- Queued window system messages
- Animation timers
- Background tasks

### Implementation Pattern

```c
void port_get_nh_event(void) {
    // TTY: Just return
    // GUI: Process events
    
    // Check for resize
    if (window_resized) {
        handle_resize();
    }
    
    // Process pending events
    while (event_pending()) {
        process_event();
    }
    
    // Update display if needed
    if (need_redraw) {
        redraw();
    }
    
    // CRITICAL: RETURN IMMEDIATELY - don't wait for input!
    return;
}
```

**Why This Matters**: Game loop needs to continue to:
- Check for multi-turn commands
- Update animations
- Handle background game logic
- Eventually call `nhgetch()` for real input

**If get_nh_event() blocks, the entire game freezes!**

## Input Processing: rhack() → nhgetch()

When game loop reaches the point where it needs player input:

```c
if (multi == 0) {
    rhack(NULL);  // Get and process one command
}
```

### rhack() Flow

```c
void rhack(char *cmd) {
    if (cmd == NULL) {
        cmd = parse();  // ← Calls nhgetch() or nh_poskey()
    }
    
    // Process command
    if (movement_command(cmd)) {
        domove();
    } else {
        switch (cmd) {
        case 'i':  // Inventory
            ddoinv();
            break;
        case 'd':  // Drop
            dodrop();
            break;
        case '#':  // Extended command
            int ext_cmd = get_ext_cmd();  // ← Port-specific UI
            do_extcmd(ext_cmd);
            break;
        // ... hundreds of other commands
        }
    }
}
```

### parse() → nhgetch() / nh_poskey()

```c
char *parse(void) {
    int ch;
    
    if (iflags.wc_mouse_support) {
        int x, y, mod;
        ch = nh_poskey(&x, &y, &mod);  // ← Port BLOCKS here
        
        if (ch == 0) {
            // Mouse click at (x, y)
            return map_click_to_command(x, y, mod);
        }
        // Else keyboard char in ch
    } else {
        ch = nhgetch();  // ← Port BLOCKS here waiting for input
    }
    
    return command_from_char(ch);
}
```

**This is where blocking happens**: `nhgetch()` / `nh_poskey()` **SHOULD block** until user provides input.

### Critical Contrast

- **get_nh_event()**: Never blocks, called constantly (every iteration)
- **nhgetch() / nh_poskey()**: Blocks until input, called only when needed

## Display Updates

Display updates happen at several points in the game loop.

### Status Updates: bot()

```c
if (context.botl || context.botlx) {
    bot();         // Update status display
    curs_on_u();   // Position cursor on player
}
```

**When**: After turn processing, when `context.botl` or `context.botlx` is set.

**bot() Implementation**:

```c
void bot(void) {
    if (VIA_WINDOWPORT()) {
        bot_via_windowport();  // Modern field-based
    } else {
        // Legacy string-based (avoid)
        curs(WIN_STATUS, 1, 0);
        putstr(WIN_STATUS, 0, do_statusline1());
        curs(WIN_STATUS, 1, 1);
        putmixed(WIN_STATUS, 0, do_statusline2());
    }
    context.botl = context.botlx = FALSE;
}
```

**bot_via_windowport()** (modern approach):

```c
static void bot_via_windowport(void) {
    // Update each changed field
    status_update(BL_HP, hp_string, chg, percent, color, NULL);
    status_update(BL_ENE, ene_string, chg, percent, color, NULL);
    status_update(BL_GOLD, gold_string, chg, 0, color, NULL);
    status_update(BL_XP, xp_string, chg, 0, color, NULL);
    status_update(BL_AC, ac_string, chg, 0, color, NULL);
    // ... all other fields ...
    status_update(BL_CONDITION, (genericptr_t)conditions, 0, 0, 0, colormasks);
    
    status_update(BL_FLUSH, NULL, 0, 0, 0, NULL);  // ← RENDER NOW!
}
```

**Trigger**: Game sets `context.botl = TRUE` whenever stats change:
- HP/max HP change
- Power change
- Gold change
- Encumbrance change
- Hunger change
- Condition change (blind, confused, etc.)
- Experience level change

### Map Updates: print_glyph()

Map updates happen in two patterns:

#### Pattern 1: Individual Cell Updates

```c
print_glyph(WIN_MAP, x, y, glyph, bkglyph);
```

Called from dozens of places throughout gameplay:
- `newsym(x, y)` - When map cell changes
- Monster movement
- Object placement/removal
- Door open/close
- Trap detection
- Vision updates

#### Pattern 2: Full Map Refresh

```c
display_nhwindow(WIN_MAP, FALSE);
```

Called:
- Every 7 turns during running/travel
- When occupation active (searching, eating, etc.)
- After major map changes

### Vision Updates: see_monsters() / see_objects()

```c
if (!context.mv || Blind) {
    if (Hallucination) {
        see_monsters();  // Randomize monster appearances
        see_objects();   // Randomize object appearances
        see_traps();
    } else if (Unblind_telepat) {
        see_monsters();  // Show telepathic vision
    }
    
    if (vision_full_recalc)
        vision_recalc(0);  // Recalculate entire FOV
}
```

These call `print_glyph()` for each visible cell that changed.

## Message Display: pline() and Friends

Messages to player use these functions (all route through `putstr(WIN_MESSAGE, ...)`):

```c
pline("You hit the orc!");           // Normal message
You("miss the goblin.");             // "You miss..."
Your("sword glows blue!");           // "Your sword..."
The("door opens.");                  // "The door..."
impossible("bug: %s", error);        // Error message (for debugging)
raw_print("Panic!");                 // Before windowing init or for errors
```

**Implementation**:
```c
void vpline(const char *fmt, va_list args) {
    char buf[BUFSZ];
    vsprintf(buf, fmt, args);
    putstr(WIN_MESSAGE, ATR_NONE, buf);
}
```

**Urgent Messages**:
```c
putstr(WIN_MESSAGE, ATR_BOLD | ATR_URGENT, "The orc hits you!");
```

**Suppressed History**:
```c
putstr(WIN_MESSAGE, ATR_NOHISTORY, "Still eating...");
```

## Menus in Practice

Menu usage follows common pattern throughout code.

### Example: Inventory Display

```c
int display_inventory(char *lets, boolean want_reply) {
    winid menu = create_nhwindow(NHW_MENU);
    start_menu(menu);
    
    // Add items
    for (obj = invent; obj; obj = obj->nobj) {
        if (!lets || index(lets, obj->invlet)) {
            any.a_obj = obj;
            add_menu(menu, obj_to_glyph(obj, rn2_on_display_rng), &any,
                    obj->invlet, obj->oclass, ATR_NONE, 
                    doname(obj), FALSE);
        }
    }
    
    end_menu(menu, "Inventory:");
    
    if (want_reply) {
        menu_item *selected;
        int n = select_menu(menu, PICK_ANY, &selected);
        if (n > 0) {
            // Process selections
            free(selected);
        }
    } else {
        select_menu(menu, PICK_NONE, NULL);  // Display only
    }
    
    destroy_nhwindow(menu);
}
```

### Example: Yes/No Prompt

```c
boolean doit = (yn_function("Really quit?", "yn", 'n') == 'y');
```

**Flow**:
1. `yn_function()` wrapper in cmd.c validates args
2. Calls `(*windowprocs.win_yn_function)(...)` (port implementation)
3. Port displays prompt and gets input
4. Returns character
5. Core validates it's in acceptable set

### Example: Get Extended Command

```c
if (*cmd == '#') {
    int idx = get_ext_cmd();  // ← Port-specific UI
    if (idx >= 0) {
        return extcmdlist[idx].ef_funct();
    }
}
```

Port can show:
- Simple getlin() prompt with string matching
- Autocomplete menu (curses does this)
- Full menu of all extended commands

## Synchronization

### mark_synch() and wait_synch()

```c
mark_synch();
// ... output operations ...
wait_synch();
```

**Purpose**: Ensure output ordering when port does async rendering.

**Example Use** (from save code):
```c
pline("Saving...");
mark_synch();
wait_synch();  // Ensure "Saving..." is visible before writing file
// ... write save file (may take time) ...
```

**Most Ports**: Can be no-ops (TTY, NDS do synchronous rendering).

**GUI Ports**: Might need to:
- `mark_synch()`: Note sync point in render queue
- `wait_synch()`: Flush output queue, wait for rendering complete

### delay_output()

```c
void explosion_animation(void) {
    for (int phase = 0; phase < 9; phase++) {
        display_explosion(phase);
        delay_output();  // ← 50ms pause
    }
}
```

**Purpose**: Visible 50ms delay for animations.

**Implementation**: Like `wait_synch()` + `nap(50ms)`, but can be async.

## Special Case: Occupation

During long multi-turn commands (search, eat, pray, travel):

```c
if (multi >= 0 && occupation) {
    if ((*occupation)() == 0)
        occupation = 0;  // Finished
    
    if (monster_nearby())
        stop_occupation();  // Interrupted!
    
    // Refresh display periodically
    if (!(++occtime % 7))
        display_nhwindow(WIN_MAP, FALSE);
    
    continue;  // Skip rhack(), don't get input
}
```

**occupation** is a function pointer that:
- Returns 1: continue occupation
- Returns 0: finished
- Can be interrupted by monsters, HP loss, hunger, etc.

**Window Port Impact**: Map updates still happen, but no input requested (skips nhgetch).

## Exit Sequence

When game ends (quit, die, ascend):

```c
void terminate(int status) {
    clearlocks();
    
    if (iflags.window_inited) {
        exit_nhwindows(goodbye_msg);  // ← Port cleanup
        iflags.window_inited = FALSE;
    }
    
    exit(status);
}
```

**exit_nhwindows()**:
- Dismiss all windows except raw_print window
- Print goodbye message if provided
- Clean up resources (fonts, graphics, memory)
- Restore terminal state (TTY) or shut down GUI toolkit

**IMPORTANT**: After `exit_nhwindows()`, only `raw_print()` is available.

## Typical Turn Sequence

Here's what happens in a typical turn:

```
1. get_nh_event()           ← Process window events (non-blocking, every iteration)

2. [Turn processing]
   - Monster movement       → Calls print_glyph() for each move
   - Timeout effects        → Hunger, regeneration, etc.
   - Random events
   - context.botl = TRUE    ← Flag: status update needed

3. bot()                    ← Update status if flagged
   if (VIA_WINDOWPORT())
       bot_via_windowport():
           status_update(BL_HP, ...)
           status_update(BL_ENE, ...)
           status_update(BL_GOLD, ...)
           status_update(BL_CONDITION, ...)
           status_update(BL_FLUSH, ...)  ← RENDER!

4. curs_on_u()              ← Position cursor on player

5. rhack(NULL)              ← Get player command
   parse()
     nh_poskey(&x, &y, &mod)  ← BLOCKS waiting for input

6. Process command
   if (movement) {
       domove()              → Updates map, calls print_glyph()
   } else {
       do_inventory()        → Creates menu, uses menu system
       dodrop()              → Creates menu, modifies inventory
       // ... etc
   }

7. Update vision
   vision_recalc()           → Calls print_glyph() for FOV changes

8. display_nhwindow(WIN_MAP, FALSE)  ← Periodic map refresh

9. Loop back to step 1
```

## Window Function Call Frequency

Sorted by how often functions are called:

### Every Iteration (100s/sec)
- `get_nh_event()` - Every single loop iteration (MUST NOT BLOCK!)

### Every Turn (once per player action)
- `status_update()` - When stats change (most turns)
- `print_glyph()` - Dozens of times per turn (map updates)
- `nhgetch()` / `nh_poskey()` - Once per turn (BLOCKS waiting for input)

### Occasionally (some turns)
- `create_nhwindow()` / `destroy_nhwindow()` - For menus, help
- `start_menu()` / `add_menu()` / `end_menu()` / `select_menu()` - For inventories, choices
- `yn_function()` - For confirmations
- `getlin()` - For text input (extended commands, naming items)
- `display_nhwindow(WIN_MAP)` - Periodic refresh (every 7 turns during running)
- `putstr(WIN_MESSAGE)` - For messages (via pline() etc.)

### Rarely (uncommon actions)
- `display_file()` - Help, license, credits
- `update_inventory()` - Inventory changed (only if WC_PERM_INVENT)
- `cliparound()` - When player moves (only if CLIPPING)
- `outrip()` - Death screen
- `suspend_nhwindows()` / `resume_nhwindows()` - Ctrl-Z (Unix only)

### Once (startup/shutdown)
- `init_nhwindows()` - Startup
- `player_selection()` / `askname()` - New game
- `status_init()` / `status_enablefield()` - Startup (per field)
- `status_finish()` - Shutdown
- `exit_nhwindows()` - Shutdown

## Important Patterns

### Pattern 1: Flag-Based Updates

Game code doesn't call display functions directly. Instead, it sets flags:

```c
// Game code
u.uhp -= damage;
context.botl = TRUE;  // Flag "status needs update"

// Main loop
if (context.botl) {
    bot();  // Actually update status
}
```

**Why**: Batching. Multiple HP changes in one turn → single status update.

### Pattern 2: Lazy Vision Updates

```c
// Game code
u.ux = newx;
u.uy = newy;
vision_full_recalc = TRUE;  // Flag "need to recalc FOV"

// Main loop
if (vision_full_recalc) {
    vision_recalc(0);  // Actually recalc and redraw
}
```

**Why**: Expensive operation - do it once per turn max.

### Pattern 3: Non-Blocking Event Loop

```c
for (;;) {
    get_nh_event();  // Non-blocking, returns immediately
    
    // ... game logic ...
    
    if (need_input) {
        ch = nhgetch();  // Blocking, waits for user
    }
}
```

**Why**: Allows event processing (resize, expose, etc.) while waiting for input.

## Summary

**Startup**: init → create windows → game loop

**Main Loop**:
- `get_nh_event()` every iteration (non-blocking)
- Turn processing (game logic)
- Status update (if needed)
- Input (blocking `nhgetch()` / `nh_poskey()`)
- Command execution
- Display updates

**Display Updates**:
- Status: via `bot()` → `status_update()` × N → `BL_FLUSH`
- Map: via `print_glyph()` called from vision/movement code
- Messages: via `pline()` → `putstr(WIN_MESSAGE)`
- Menus: create → populate → select → destroy

**Input**:
- `get_nh_event()`: Non-blocking, event processing
- `nhgetch()` / `nh_poskey()`: Blocking, wait for user
- `yn_function()`: Blocking, yes/no/choice
- `getlin()`: Blocking, text input
- `get_ext_cmd()`: Blocking, extended command selection
- Menu `select_menu()`: Blocking, item selection

**Critical Distinction**:
- **Non-blocking**: `get_nh_event()` - returns immediately, process pending events
- **Blocking**: `nhgetch()`, `yn_function()`, etc. - wait for user input

Understanding this timing is crucial for implementing a port that feels responsive and correct.
