# NetHack Porting Layer - Gotchas & Best Practices

Critical rules, common mistakes, and practical guidance for port implementers.

## Top 10 Critical Rules

### 1. get_nh_event() NEVER Blocks

**THE #1 MISTAKE**

```c
// WRONG - Hangs the game!
void bad_get_nh_event(void) {
    int ch = getchar();  // BLOCKS - game freezes!
    process_input(ch);
}

// CORRECT
void good_get_nh_event(void) {
    // Process pending events
    while (event_pending()) {
        process_event();
    }
    
    // Update display if needed
    if (need_redraw) {
        redraw();
    }
    
    // Return immediately!
    return;
}
```

**Why**: Called every loop iteration (100s/sec). If it blocks, game freezes.

**Symptoms**: Game appears frozen, can't see updates, animations don't work.

### 2. nhgetch() MUST Block

```c
// CORRECT
int port_nhgetch(void) {
    return getchar();  // OK to block here
}

// ALSO CORRECT (embedded)
int port_nhgetch(void) {
    int ch = 0;
    while (!ch) {
        poll_hardware();
        if (input_available()) {
            ch = read_input();
        } else {
            sleep_briefly();
        }
    }
    return ch;
}
```

**Why**: This is WHERE blocking happens in the game loop.

**Contrast**:
- `get_nh_event()`: Non-blocking, called constantly
- `nhgetch()`: Blocking, called only when input needed

### 3. Status Updates: Field-Based, NOT String-Based

```c
// BAD - NDS anti-pattern (don't copy!)
void bad_status_update(...) {
    genl_status_update(...);  // Formats to string
    sscanf(buffer, "HP:%d(%d) ...", &hp, ...);  // Parse back - FRAGILE!
}

// GOOD - Direct field access
void good_status_update(int fld, genericptr_t ptr, ...) {
    switch (fld) {
    case BL_FLUSH:
        render_status();
        return;
        
    case BL_HP:
        sscanf((char *)ptr, "%d(%d)", &hp, &hpmax);  // Direct access
        store_hp(hp, hpmax, color);
        break;
        
    case BL_CONDITION:
        unsigned long cond = (unsigned long)ptr;  // BITMASK!
        store_conditions(cond, colormasks);
        break;
    }
}
```

**Why string parsing is bad**:
- Fragile (format can change)
- Loses information (color, highlighting)
- Can't handle BL_CONDITION (it's a bitmask, not string!)
- Not all fields in the string

### 4. BL_CONDITION is a Bitmask, NOT a String

```c
case BL_CONDITION:
    // WRONG
    char *str = (char *)ptr;  // NO! This is a bitmask!
    
    // CORRECT
    unsigned long cond = (unsigned long)ptr;  // Bitmask
    
    if (cond & BL_MASK_STONE) {
        // Petrifying - DEADLY!
        int color = find_color_in_colormasks(BL_MASK_STONE, colormasks);
        display_condition("Stone", color);
    }
    if (cond & BL_MASK_STUN) {
        display_condition("Stun", ...);
    }
    // ... check all condition bits
    break;
```

**Why**: BL_CONDITION is the ONLY field where ptr is not a string.

**colormasks**: Array where `colormasks[CLR_RED]` = bitmask of conditions to show in red.

### 5. Free menu_item Arrays

```c
menu_item *selected;
int n = select_menu(menu, PICK_ANY, &selected);

if (n > 0) {
    // Use selected[0..n-1]
    for (int i = 0; i < n; i++) {
        use_item(selected[i].item, selected[i].count);
    }
    
    // MUST FREE!
    free(selected);
}

// If n == 0 or n == -1, selected is NULL - no need to free
```

**Why**: Port allocates array in select_menu(), caller must free.

**Forgetting causes**: Memory leaks on every menu operation.

### 6. Never Return 0 from nhgetch()

```c
int port_nhgetch(void) {
    int ch = getchar();  // Or platform equivalent
    
    // CRITICAL: Map 0, NUL, EOF to ESC
    if (ch == 0 || ch == EOF)
        ch = '\033';
    
    return ch;  // Never return 0!
}
```

**Why**: NetHack uses 0 as sentinel for mouse clicks in nh_poskey().

**Returning 0 breaks**: Command processing, movement, everything.

### 7. Always Use mapglyph()

```c
void port_print_glyph(winid win, int x, int y, int glyph, int bkglyph) {
    int ch, color;
    unsigned special;
    
    // ALWAYS call mapglyph() - don't try to decode glyph yourself!
    mapglyph(glyph, &ch, &color, &special, x, y, 0);
    
    // Now use ch, color, special
    draw_char(x, y, ch, color);
    
    if (special & MG_PET && iflags.hilite_pet) {
        highlight_pet(x, y);
    }
}
```

**Why**: Glyph encoding is complex and can change. mapglyph() handles:
- Glyph to symbol translation
- Color selection
- Special flags (pet, corpse, detect, etc.)
- Hallucination randomization

**Never**: Try to decode glyph ranges yourself.

### 8. getlin() Must Call flush_screen(1)

```c
void port_getlin(const char *ques, char *input) {
    // CRITICAL: Call first!
    flush_screen(1);
    
    display_prompt(ques);
    read_line_into(input, BUFSZ);
    
    // ESC cancels: return "\\033\\000"
    if (cancelled) {
        input[0] = '\033';
        input[1] = '\0';
    }
}
```

**Why**: Ensures all pending output is visible before prompting.

**Forgetting causes**: Prompt appears before previous messages, confusing display.

### 9. Copy Strings in add_menu()

```c
void port_add_menu(winid win, int glyph, const anything *identifier,
                   char acc, char grp, int attr, const char *str,
                   boolean presel) {
    menu_item *item = allocate_menu_item();
    
    // Store identifier pointer (core owns it, don't free)
    item->identifier = identifier;
    
    // MUST COPY string (core's buffer is temporary)
    item->text = strdup(str);  // Or your allocator
    
    // Other fields...
    item->accelerator = acc;
    item->attr = attr;
}
```

**Why**: Core's string buffer may be reused after add_menu() returns.

**Not copying causes**: Garbage text, crashes, menu corruption.

### 10. Advertise Only What You Implement

```c
// BAD - Claims mouse but doesn't implement nh_poskey
wincap = WC_MOUSE_SUPPORT;
// ... but win_nh_poskey just calls nhgetch()

// GOOD - Honest
wincap = WC_COLOR | WC_HILITE_PET | WC_INVERSE;
wincap2 = WC2_FLUSH_STATUS | WC2_HILITE_STATUS;

// Later, when you add mouse:
wincap |= WC_MOUSE_SUPPORT;
windowprocs.win_nh_poskey = my_nh_poskey;  // Now implement it
```

**Why**: Core game changes behavior based on capability flags.

**Claiming without implementing**: Confuses users, breaks features.

## Memory Management

### Who Owns What?

| What | Who Allocates | Who Frees | Notes |
|------|--------------|-----------|-------|
| `menu_item` array | Port (in select_menu) | **Caller** | MUST free if n > 0 |
| Window structures | Port (in create_nhwindow) | Port (in destroy_nhwindow) | Port-specific |
| Menu item data | Port (in add_menu) | Port (destroy or start_menu) | Must copy strings |
| `identifier` in add_menu | Core | Core | Port only stores pointer |
| Status strings | Core (temporary) | Core | Port must copy if storing |

### String Handling Example

```c
void port_add_menu(..., const char *str, ...) {
    item->text = strdup(str);  // COPY - core's buffer is temporary
}

void port_destroy_nhwindow(winid win) {
    if (windows[win]->type == NHW_MENU) {
        for (int i = 0; i < menu->count; i++) {
            free(menu->items[i].text);  // FREE copies
        }
        free(menu->items);
        free(menu);
    }
    free(windows[win]);
}
```

## Synchronization Pitfalls

### mark_synch() and wait_synch()

**Most ports**: Can be no-ops (TTY, NDS).

**GUI ports with async rendering**: May need to implement.

```c
// Simple (TTY)
void port_wait_synch(void) {
    fflush(stdout);
}

// Complex (GUI with render queue)
void port_wait_synch(void) {
    flush_render_queue();
    wait_for_completion();
}
```

**Use**: Before operations requiring stable display (save game, etc.).

### delay_output()

Should pause ~50ms for animations.

```c
void port_delay_output(void) {
    wait_synch();      // Ensure current output visible
    usleep(50000);     // 50ms
}
```

## Input Handling

### yn_function ESC Handling

```c
char port_yn_function(const char *ques, const char *choices, char def) {
    display_prompt(ques);
    int ch = nhgetch();
    
    // ESC handling (try in order):
    if (ch == '\033') {
        if (choices && strchr(choices, 'q')) return 'q';
        if (choices && strchr(choices, 'n')) return 'n';
        return def;
    }
    
    // SPACE/RETURN map to default
    if (ch == ' ' || ch == '\r' || ch == '\n')
        return def;
    
    // Validate choice
    if (!choices)
        return ch;  // Any char accepted
    
    if (strchr(choices, tolower(ch)))
        return tolower(ch);
    
    // Invalid - beep and retry
    nhbell();
    return port_yn_function(ques, choices, def);
}
```

### Count Handling

```c
// If '#' in choices, accept count
if (choices && strchr(choices, '#')) {
    if (ch >= '0' && ch <= '9') {
        yn_number = get_full_count(ch);
        return '#';
    }
}
```

## Menu Accelerators

### Don't Mix Strategies

```c
// WRONG - Mixed
add_menu(menu, ..., 'a', ...);  // Caller assigns
add_menu(menu, ..., 0, ...);    // Port assigns?
add_menu(menu, ..., 'c', ...);  // Caller assigns

// CORRECT - Consistent (caller assigns)
add_menu(menu, ..., 'a', ...);
add_menu(menu, ..., 'b', ...);
add_menu(menu, ..., 'c', ...);

// CORRECT - Consistent (port assigns)
add_menu(menu, ..., 0, ...);
add_menu(menu, ..., 0, ...);
add_menu(menu, ..., 0, ...);
```

**Port should detect and enforce** consistent strategy.

## Window Lifecycle

### Persistent vs Temporary

**Persistent** (created once, live entire game):
- WIN_MESSAGE
- WIN_MAP
- Status system (via status_init, not NHW_STATUS window)

**Temporary** (created on-demand, destroyed after use):
- NHW_MENU windows
- NHW_TEXT windows

### NHW_MENU Polymorphism

**CRITICAL**: One-way commitment!

```c
winid menu = create_nhwindow(NHW_MENU);

// Choice 1: Use as menu
start_menu(menu);  // ← Commits to MENU mode
add_menu(...);
// Can't call putstr() now!

// Choice 2: Use as text
putstr(menu, ATR_NONE, "Text");  // ← Commits to TEXT mode
// Can't call start_menu() now!
```

**Cannot change mode** after first use.

## Platform-Specific Gotchas

### Terminal (TTY)

**Terminal Capability Issues**:
```c
if (!term_has_capability(cap)) {
    // Fallback gracefully
    use_simpler_approach();
}
```

**Raw Mode Restoration**:
```c
void port_exit_nhwindows(const char *str) {
    // CRITICAL: Restore terminal state
    settty(ttyorig);
    
    if (str) printf("%s\n", str);
}
```

**Test with minimal terminals**: TERM=dumb, TERM=vt100

### GUI/Hardware

**Screen Refresh Timing**:
```c
// DON'T refresh on every print_glyph
void port_print_glyph(...) {
    update_buffer(x, y, glyph);
    // Don't refresh here - too expensive
}

// DO refresh in display_nhwindow or BL_FLUSH
void port_display_nhwindow(winid win, boolean blocking) {
    if (win == WIN_MAP) {
        refresh_display();  // Now refresh
    }
}
```

**Double-Buffering**:
```c
void port_print_glyph(...) {
    draw_to_back_buffer(x, y, glyph);
}

void refresh_display(void) {
    swap_buffers();  // Atomic swap prevents flicker
}
```

### Mouse/Touch

**Coordinate Mapping**:
```c
int port_nh_poskey(int *x, int *y, int *mod) {
    if (key_pressed()) {
        return read_key();  // Non-zero
    }
    
    if (touch_active()) {
        read_touch_coords(x, y);
        
        // Map screen coords to game coords
        *x = screen_to_game_x(*x);
        *y = screen_to_game_y(*y);
        
        *mod = CLICK_1;
        return 0;  // Zero = mouse/touch
    }
    
    wait_for_input();
    return port_nh_poskey(x, y, mod);
}
```

## Best Practices

### Start Minimal

**Phase 1**: Core functionality
- init/exit
- create/destroy windows (allocate/free)
- putstr (append to buffer)
- print_glyph (store in grid)
- display_nhwindow (render buffers)
- nhgetch (blocking input)
- raw_print (direct output)
- status_init/enablefield/update/finish (minimal)
- get_nh_event (no-op or minimal)

**Phase 2**: Menus
- start/add/end/select_menu (simple keyboard-only)

**Phase 3**: Rich Features
- yn_function, getlin, get_ext_cmd
- Mouse/touch, colors, status highlighting

**Phase 4**: Polish
- Help system, message history, custom UI

### Use Helper Functions

```c
// Use these unless you have specific needs:
windowprocs.win_putmixed = genl_putmixed;
windowprocs.win_outrip = genl_outrip;
windowprocs.win_status_finish = genl_status_finish;
windowprocs.win_can_suspend = genl_can_suspend_yes;  // or _no
windowprocs.win_message_menu = genl_message_menu;
windowprocs.win_doprev_message = genl_doprev_message;
windowprocs.win_getmsghistory = genl_getmsghistory;
windowprocs.win_putmsghistory = genl_putmsghistory;
```

### Test Thoroughly

**Test Cases**:

1. **Basic Gameplay**:
   - Start game, move around, inventory, drop, quit

2. **Status Updates**:
   - Take damage (HP changes)
   - Cast spell (Pw changes)
   - Pick up gold
   - Get hungry
   - Gain level
   - Polymorph (HD field appears)
   - Get blinded/confused/stunned (conditions)

3. **Menus**:
   - Inventory (PICK_NONE)
   - Drop (PICK_ANY with counts)
   - Eat (PICK_ONE)
   - Empty inventory
   - Single item
   - Many pages (100+ items)

4. **Input**:
   - All movement keys
   - Extended commands (#sit, #pray, etc.)
   - Counts (5h = move 5 steps)
   - ESC cancellation
   - yn_function with various choice sets

5. **Display**:
   - Hallucination (randomized glyphs)
   - Blindness (no map updates except by touch)
   - Clipping (if supported)
   - Colors and attributes
   - Window resize (if supported)

6. **Edge Cases**:
   - Very small window
   - Very long messages
   - Deep menu (100+ items)
   - Rapid input
   - Occupation interrupts (monster appears while searching)

## Debugging Tips

### Enable Debug Logging

```c
#ifdef PORT_DEBUG
#define PORT_LOG(fmt, ...) \
    fprintf(debug_file, "[PORT] " fmt "\n", ##__VA_ARGS__)
#else
#define PORT_LOG(fmt, ...)
#endif

void port_print_glyph(...) {
    PORT_LOG("print_glyph: win=%d x=%d y=%d glyph=%d", win, x, y, glyph);
    // ...
}
```

### Validate Assumptions

```c
void port_status_update(int fld, ...) {
    if (fld >= 0 && fld < MAXBLSTATS) {
        if (!status_fields[fld].enabled && fld != BL_CONDITION) {
            impossible("Update for disabled field %d", fld);
        }
    }
}

void port_select_menu(winid win, ...) {
    if (!windows[win]->is_menu) {
        impossible("select_menu on non-menu window!");
    }
}
```

### Assert in Debug Builds

```c
#ifdef DEBUG
#define ASSERT(cond) \
    if (!(cond)) { \
        fprintf(stderr, "Assertion failed: %s at %s:%d\n", \
                #cond, __FILE__, __LINE__); \
        abort(); \
    }
#else
#define ASSERT(cond)
#endif

int port_nhgetch(void) {
    int ch = read_input();
    ASSERT(ch != 0);  // Should never return 0
    return ch;
}
```

## Common Mistakes Summary

1. **Blocking in get_nh_event()** → Game freezes
2. **Not freeing menu_item arrays** → Memory leaks
3. **Parsing status strings (like NDS)** → Fragile, loses info
4. **Treating BL_CONDITION as string** → Crashes, wrong display
5. **Not implementing BL_FLUSH** → Status never renders
6. **Returning 0 from nhgetch()** → Breaks command processing
7. **Not calling mapglyph()** → Wrong symbols, missing colors
8. **Not copying strings in add_menu()** → Garbage text
9. **Mixing accelerator strategies** → Confusing menus
10. **Claiming unsupported capabilities** → Broken features

## Summary

**Critical Rules** (memorize these):
1. get_nh_event() never blocks
2. nhgetch() always blocks
3. Use field-based status, not string parsing
4. BL_CONDITION is a bitmask
5. Free menu_item arrays
6. Never return 0 from nhgetch()
7. Always use mapglyph()
8. getlin() calls flush_screen(1) first
9. Copy strings in add_menu()
10. Advertise only what you implement

**Best Practices**:
- Start minimal, add features incrementally
- Use helper functions (genl_*)
- Buffer rendering, flush on demand
- Test thoroughly
- Validate assumptions in debug builds

**Remember**: The porting layer is well-designed. Most problems come from not following these rules, not from the API itself.
