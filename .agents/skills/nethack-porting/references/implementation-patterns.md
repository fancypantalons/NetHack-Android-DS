# NetHack Porting Layer - Implementation Patterns

How different ports solve the same problems. Learn from TTY, Curses, and NDS implementations.

## Port Overview

**TTY** (~5,000 lines): Simple, direct, text-based. Good reference implementation.
- Direct terminal I/O (termcap/terminfo)
- Blocking getchar() for input
- Termcap codes for colors
- No mouse (except WIN32CON)
- Dynamic status layout fitting
- ASCII characters only

**Curses** (~15,000 lines): Rich UI, library-managed, feature-complete.
- Curses library handles terminal abstraction
- Blocking getch() with event handling
- Curses color pairs
- Mouse support
- Window-based layout
- ASCII or UTF-8

**NDS** (~10,000 lines): Hardware access, graphical, embedded.
- Direct hardware framebuffer
- Manual polling loop for input
- Hardware palette
- Touch screen (not mouse API)
- Fixed hardware screens
- 16×16 pixel tiles

## Capability Flags Comparison

```c
// TTY
wincap = WC_COLOR | WC_HILITE_PET | WC_INVERSE | WC_EIGHT_BIT_IN
wincap2 = WC2_HILITE_STATUS | WC2_FLUSH_STATUS | WC2_HITPOINTBAR

// Curses
wincap = WC_COLOR | WC_HILITE_PET | WC_POPUP_DIALOG | WC_PLAYER_SELECTION
         | WC_MOUSE_SUPPORT | WC_PERM_INVENT | WC_INVERSE
wincap2 = WC2_FULLSCREEN | WC2_HILITE_STATUS | WC2_FLUSH_STATUS

// NDS
wincap = WC_COLOR | WC_HILITE_PET | WC_INVERSE
wincap2 = WC2_HILITE_STATUS | WC2_FLUSH_STATUS
```

**Key Takeaways**:
- All three support modern status (WC2_FLUSH_STATUS)
- Curses has richest feature set
- Start minimal, add capabilities as you implement them

## Helper Function Usage

| Function | TTY | Curses | NDS | Recommendation |
|----------|-----|--------|-----|----------------|
| `putmixed` | genl | genl | genl | **Use genl** unless glyph embedding needed |
| `status_finish` | genl | genl | genl | **Use genl** - almost everyone does |
| `status_update` | custom | custom | custom | **Must customize** - core of status display |
| `outrip` | genl | genl | custom | **Use genl** unless custom tombstone |
| `can_suspend` | genl_yes | genl_yes | genl_no | **Use genl_yes or genl_no** based on platform |
| `message_menu` | custom | genl | genl | **Use genl** unless TTY-specific needs |

## get_nh_event() - Three Approaches

### TTY: No-Op
```c
void tty_get_nh_event(void) {
    return;  // Nothing to do
}
```
**Why**: Terminal I/O is synchronous, no windowing events.

### Curses: Event Processing
```c
void curses_get_nh_event(void) {
    if (term_resized) {
        handle_resize();
    }
    
    // Check for input (don't consume)
    if (has_input()) {
        // Note available, don't read
    }
    
    return;  // Non-blocking
}
```
**Why**: Handle resize (SIGWINCH), but don't block.

### NDS: Hardware Polling
```c
void nds_get_nh_event(void) {
    scanKeys();       // Poll hardware
    touchRead(&touch); // Update touch state
    
    if (need_refresh) {
        refresh_display();
    }
    
    return;  // Non-blocking
}
```
**Why**: Embedded - must poll hardware, no OS events.

**CRITICAL COMMONALITY**: All three return immediately - never block!

## nhgetch() - Three Approaches

### TTY: Simple Blocking
```c
int tty_nhgetch(void) {
    fflush(stdout);
    
    int ch = getchar();  // ← BLOCKS
    
    if (ch == 0 || ch == EOF)
        ch = '\033';
    
    return ch;
}
```
**Simple**: Call OS function, block until input.

### Curses: Event Handling While Blocking
```c
int curses_nhgetch(void) {
    int ch;
    
    while ((ch = getch()) == ERR) {
        if (term_resized) {
            handle_resize();
        }
    }
    
    return map_key(ch);  // Translate curses keys
}
```
**Advanced**: Can process resize even while waiting for input.

### NDS: Manual Polling Loop
```c
int nds_nhgetch(void) {
    int ch = 0;
    
    while (!ch) {
        scanKeys();
        
        if (keysDown()) {
            ch = nds_translate_key(keysDown());
        } else if (touch.px || touch.py) {
            ch = nds_handle_touch(&touch);
        } else {
            swiWaitForVBlank();  // Sleep one frame
        }
    }
    
    return ch;
}
```
**Embedded**: No blocking OS call, must poll hardware.

**CRITICAL COMMONALITY**: All three block - but how differs by platform.

## print_glyph() - Three Approaches

### TTY: Direct Terminal Output
```c
void tty_print_glyph(winid win, int x, int y, int glyph, int bkglyph) {
    int ch, color;
    unsigned special;
    
    mapglyph(glyph, &ch, &color, &special, x, y, 0);
    
    tty_curs(win, x, y);  // Position cursor
    
    if (color != NO_COLOR)
        term_start_color(color);
    if (special & MG_PET && iflags.hilite_pet)
        term_start_attr(ATR_INVERSE);
    
    putchar(ch);
    
    term_end_attr(ATR_INVERSE);
    term_end_color();
}
```
**Pattern**: Position → set attributes → output → reset.
**Optimization**: Early-out for clipping.

### Curses: Buffered Output
```c
void curses_print_glyph(winid win, int x, int y, int glyph, int bkglyph) {
    int ch, color;
    unsigned special;
    attr_t attr = A_NORMAL;
    
    mapglyph(glyph, &ch, &color, &special, x, y, 0);
    
    if (color != NO_COLOR)
        attr |= curses_color_pair(color, NO_COLOR);
    if (special & MG_PET)
        attr |= curses_get_pet_attr();
    if (special & MG_DETECT)
        attr |= A_DIM;
    
    mvwaddch(wins[win]->curses_win, y, x, ch | attr);
}
```
**Pattern**: Build attributes → buffered write (refreshed later).
**Advantage**: Can combine multiple attributes.

### NDS: Tile-Based Layered
```c
void nds_print_glyph(winid win, int x, int y, int glyph, int bkglyph) {
    int ch, color;
    unsigned special;
    
    mapglyph(glyph, &ch, &color, &special, x, y, 0);
    
    int tile_idx = nds_glyph_to_tile(glyph);
    
    if (bkglyph != NO_GLYPH) {
        nds_draw_tile(BG_LAYER, x, y, glyph_to_tile(bkglyph));
    }
    
    nds_draw_tile(FG_LAYER, x, y, tile_idx);
    
    if (special & MG_PET) {
        nds_draw_pet_indicator(x, y);
    }
}
```
**Pattern**: Background layer → foreground layer → indicators.
**Note**: Ignores color parameter (tiles are pre-colored).

**UNIVERSAL**: All three call `mapglyph()` first!

## status_update() - Three Approaches

### TTY: Field-Based with Dynamic Layout (GOOD)
```c
void tty_status_update(int fld, genericptr_t ptr, int chg,
                       int percent, int color, unsigned long *colormasks) {
    switch (fld) {
    case BL_FLUSH:
        make_things_fit(FALSE);  // Dynamic layout
        render_status();
        return;
        
    case BL_RESET:
        make_things_fit(TRUE);
        render_status();
        return;
        
    case BL_CONDITION:
        tty_condition_bits = *(long *)ptr;  // BITMASK!
        tty_colormasks = colormasks;
        break;
        
    default:
        // Store field value
        sprintf(status_vals[fld], fmt, (char *)ptr);
        tty_status[NOW][fld].color = color & 0xFF;
        tty_status[NOW][fld].attr = (color >> 8) & 0xFF;
        break;
    }
}
```
**Features**: HP bar, condition coloring, 2-3 line modes, dynamic fitting.

### Curses: Field-Based with Windows (GOOD)
```c
void curses_status_update(int fld, genericptr_t ptr, int chg,
                          int percent, int color, unsigned long *colormasks) {
    if (fld == BL_FLUSH) {
        for (int i = 0; i < MAXBLSTATS; i++) {
            if (status[i].dirty) {
                draw_status_field(i);
            }
        }
        wrefresh(status_win);
        return;
    }
    
    // Store values, mark dirty
    status[fld].value = ptr;
    status[fld].color = color;
    status[fld].dirty = TRUE;
}
```
**Features**: Better color, window borders, rich display.

### NDS: String-Based Helper (BAD - Don't Copy!)
```c
void nds_status_update(int fld, genericptr_t ptr, int chg,
                       int percent, int color, unsigned long *colormasks) {
    char buf[MAXCO];
    genl_status_update(fld, ptr, chg, percent, color, colormasks);
    
    // Parse formatted string back - FRAGILE!
    int hp, hpmax, pw, pwmax;
    sscanf(buf, "HP:%d(%d) Pw:%d(%d) ...", &hp, &hpmax, &pw, &pwmax);
    
    nds_draw_hp(hp, hpmax);
    nds_draw_power(pw, pwmax);
}
```
**Why BAD**: Fragile parsing, loses information, can't handle all fields (especially BL_CONDITION).

**RECOMMENDATION**: Use field-based approach like TTY/Curses, not string-based like NDS.

## select_menu() - Three UI Approaches

### TTY: Keyboard-Only
```c
int tty_select_menu(winid win, int how, menu_item **selected) {
    display_menu_items(menu);
    
    while ((ch = tty_nhgetch()) != '\n' && ch != ' ') {
        if (ch >= 'a' && ch <= 'z') {
            toggle_item(find_item(menu, ch));
            if (how == PICK_ONE) break;
        } else if (ch == '.') {
            select_all(menu);
        } else if (ch == '-') {
            unselect_all(menu);
        }
    }
    
    return collect_selections(menu, selected);
}
```
**Simple**: Accelerators + menu commands.

### Curses: Mouse + Keyboard
```c
int curses_select_menu(winid win, int how, menu_item **selected) {
    display_menu_with_scrollbar(win);
    
    while (TRUE) {
        ch = getch();
        
        if (ch == KEY_MOUSE) {
            if (getmouse(&event) == OK) {
                if (event.bstate & BUTTON1_CLICKED) {
                    toggle_item(mouse_y_to_item(event.y));
                }
            }
        } else if (ch == KEY_UP || ch == KEY_DOWN) {
            scroll_menu(ch == KEY_UP ? -1 : 1);
        } else if (ch >= 'a' && ch <= 'z') {
            select_by_accel(ch);
        }
    }
    
    return collect_selections(menu, selected);
}
```
**Rich**: Mouse clicks, arrow keys, scrollbar, visual feedback.

### NDS: Touch + Buttons
```c
int nds_select_menu(winid win, int how, menu_item **selected) {
    while (TRUE) {
        nds_draw_menu(menu, scroll_pos);
        
        scanKeys();
        touchRead(&touch);
        
        if (touch.px && touch.py) {
            int item = nds_touch_to_item(&touch, scroll_pos);
            if (item >= 0) toggle_item(item);
        }
        
        if (keysDown() & KEY_UP) scroll_pos--;
        if (keysDown() & KEY_DOWN) scroll_pos++;
        if (keysDown() & KEY_A) break;      // Accept
        if (keysDown() & KEY_B) return -1;  // Cancel
        
        swiWaitForVBlank();
    }
    
    return collect_selections(menu, selected);
}
```
**Hybrid**: Touch to select, buttons to navigate/accept/cancel.

## Initialization Patterns

### TTY: Terminal Setup
```c
void tty_init_nhwindows(int *argc, char **argv) {
    // Get terminal capabilities
    tgetent(tcapbuf, getenv("TERM"));
    init_termc_codes();
    
    // Set raw mode
    gettty();
    setftty();  // No echo, no line buffering
    
    // Initialize colors
    init_hilite();
    
    iflags.window_inited = TRUE;
}
```
**Pattern**: Query terminal → set raw mode → initialize features.

### Curses: Library Initialization
```c
void curses_init_nhwindows(int *argc, char **argv) {
    // Initialize curses
    initscr();
    cbreak();   // No line buffering
    noecho();   // No echo
    keypad(stdscr, TRUE);  // Function keys
    
    // Colors
    if (has_colors()) {
        start_color();
        use_default_colors();
        init_color_pairs();
    }
    
    // Create windows
    curses_create_main_windows();
    
    iflags.window_inited = TRUE;
}
```
**Pattern**: Initialize library → configure → create windows.

### NDS: Hardware Setup
```c
void nds_init_nhwindows(int *argc, char **argv) {
    // Initialize video
    videoSetMode(MODE_5_2D | DISPLAY_BG_EXT_PALETTE);
    videoSetModeSub(MODE_5_2D | DISPLAY_BG3_ACTIVE);
    
    // Set up VRAM
    vramSetMainBanks(VRAM_A_MAIN_BG, VRAM_B_MAIN_SPRITE, ...);
    
    // Load assets
    system_font = read_bdf("font.bdf");
    nds_init_map();  // Load tiles
    nds_load_palette("minimap.pal", BG_PALETTE_SUB);
    
    // Initialize input
    touchRead(&touch);
    
    iflags.window_inited = TRUE;
}
```
**Pattern**: Initialize hardware → load assets → configure input.

## Common Patterns Across All Ports

### Pattern 1: Buffered Rendering
```c
// Update: Store in buffer
void port_print_glyph(...) {
    map_buffer[y][x].glyph = glyph;
    map_buffer[y][x].dirty = TRUE;
}

// Render: Flush on demand
void port_display_nhwindow(winid win, boolean blocking) {
    if (win == WIN_MAP) {
        for (cell in dirty_cells) {
            render_cell(cell);
        }
    }
}
```

### Pattern 2: Window Data Structure
```c
typedef struct port_window {
    int type;  // NHW_MESSAGE, NHW_MAP, etc.
    
    union {
        struct { char **lines; int count; } message;
        struct { cell_t cells[ROWNO][COLNO]; } map;
        struct { menu_item *items; int count; } menu;
    } data;
} port_window_t;
```

### Pattern 3: Status Field Storage
```c
struct status_field {
    int fld;
    char value[BUFSZ];
    int color;
    int attr;
    boolean enabled;
    boolean dirty;
};

status_field status_fields[MAXBLSTATS];
```

## Complexity vs Features

| Port | Lines | Complexity | Features | Best For |
|------|-------|------------|----------|----------|
| TTY | ~5K | Low | Basic | Learning, reference |
| NDS | ~10K | Medium | Graphical | Embedded systems |
| Curses | ~15K | High | Rich UI | Feature-rich ports |

**Recommendation**: Start with TTY-like simplicity, add features incrementally.

## Platform-Specific Strategies

### Terminal-Based (TTY, Curses)
- Use termcap/terminfo or curses for abstraction
- Handle SIGWINCH for resize
- Block in getchar()/getch()
- ASCII characters (or UTF-8 with curses)

### GUI-Based (hypothetical X11, SDL, Qt)
- Event loop in get_nh_event()
- Block in custom input function
- Graphical tiles or fonts
- Mouse support via nh_poskey()

### Embedded (NDS, hypothetical microcontroller)
- Poll hardware in get_nh_event()
- Manual polling loop in nhgetch()
- Direct framebuffer access
- Fixed resolution, pre-loaded assets

## Key Lessons

1. **Start simple** - TTY approach is proven and understandable
2. **Use helper functions** - genl_* for common tasks
3. **Customize critical functions** - display, input, status
4. **Don't parse strings** - Use field-based status
5. **Match platform** - Use platform's strengths (curses windows, hardware layers, etc.)
6. **Buffer and flush** - Don't render on every update
7. **Always call mapglyph()** - Never decode glyphs yourself
8. **Test thoroughly** - Each port has subtle platform-specific bugs

## Recommendations for New Ports

### Minimal Starting Point
```c
wincap = WC_COLOR | WC_HILITE_PET | WC_INVERSE;
wincap2 = WC2_FLUSH_STATUS;

// Use helpers
windowprocs.win_putmixed = genl_putmixed;
windowprocs.win_outrip = genl_outrip;
windowprocs.win_status_finish = genl_status_finish;
windowprocs.win_can_suspend = genl_can_suspend_yes;  // or _no
windowprocs.win_message_menu = genl_message_menu;

// Must customize
windowprocs.win_init_nhwindows = my_init;
windowprocs.win_print_glyph = my_print_glyph;
windowprocs.win_nhgetch = my_nhgetch;
windowprocs.win_status_update = my_status_update;
windowprocs.win_select_menu = my_select_menu;
```

### Incremental Feature Addition
1. **Phase 1**: Core display (print_glyph, putstr, status)
2. **Phase 2**: Input (nhgetch, yn_function, getlin)
3. **Phase 3**: Menus (keyboard-only initially)
4. **Phase 4**: Enhanced features (mouse, persistent inventory, etc.)
5. **Phase 5**: Polish (help system, message history, custom UI)

## Summary

Different ports demonstrate the flexibility of NetHack's porting layer:
- TTY: Simple, direct, good reference
- Curses: Rich features, library-managed
- NDS: Hardware access, graphical

**Universal patterns**:
- get_nh_event() never blocks
- nhgetch() always blocks
- mapglyph() for glyph translation
- Field-based status updates
- Buffered rendering with flush

**Platform-specific**:
- Input method (blocking vs polling)
- Display technology (terminal vs framebuffer vs GUI)
- Feature set (minimal vs rich)

**Best practice**: Start with TTY-like simplicity, use field-based status (not string parsing), customize critical functions, use helpers for the rest.
