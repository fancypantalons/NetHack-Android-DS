# NetHack Porting Layer - Quick Reference

Concise tables and summaries for fast lookups.

## Function Call Frequency

| Frequency | Functions |
|-----------|-----------|
| **Every iteration** (100s/sec) | `get_nh_event()` |
| **Every turn** | `status_update()`, `print_glyph()`, `nhgetch()` |
| **Occasionally** | menus, `yn_function()`, `getlin()`, `putstr()` |
| **Rarely** | `display_file()`, `update_inventory()`, `outrip()`, suspend/resume |
| **Once** | `init_nhwindows()`, `status_init()`, `status_enablefield()`, `exit_nhwindows()` |

## Required vs Optional Functions

### Always Required
- **Lifecycle**: init_nhwindows, exit_nhwindows, suspend_nhwindows, resume_nhwindows, can_suspend
- **Windows**: create_nhwindow, clear_nhwindow, display_nhwindow, destroy_nhwindow
- **Output**: putstr, print_glyph, raw_print, raw_print_bold
- **Input**: nhgetch, yn_function, getlin
- **Menus**: start_menu, add_menu, end_menu, select_menu
- **Status**: status_init, status_enablefield, status_update, status_finish
- **Sync**: get_nh_event, mark_synch, wait_synch, delay_output
- **Other**: askname, nhbell

### Conditionally Required
- `nh_poskey` - Only if WC_MOUSE_SUPPORT set
- `putmixed` - Can use genl_putmixed()
- `cliparound` - Only if CLIPPING defined
- `update_positionbar` - Only if POSITIONBAR defined

### Optional / Stub-able
- `curs` - Mostly obsolete
- `player_selection`, `get_ext_cmd` - Can use simple prompts
- `update_inventory` - No-op if no persistent inventory
- `doprev_message`, `message_menu` - Use genl_*
- `start_screen`, `end_screen` - Empty for non-TTY
- `outrip` - Use genl_outrip()
- `preference_update`, `number_pad` - No-op if not needed
- `getmsghistory`, `putmsghistory` - Use genl_*

## Helper Functions to Use

| Function | Recommendation |
|----------|----------------|
| `putmixed` | Use `genl_putmixed` unless glyph embedding needed |
| `outrip` | Use `genl_outrip` unless custom tombstone wanted |
| `status_finish` | Use `genl_status_finish` - almost everyone does |
| `can_suspend` | Use `genl_can_suspend_yes` or `genl_can_suspend_no` |
| `message_menu` | Use `genl_message_menu` unless TTY-specific needs |
| `doprev_message` | Use `genl_doprev_message` for basic support |
| `getmsghistory` / `putmsghistory` | Use `genl_*` for basic support |

## Status Fields

| Field | Type | Special Handling |
|-------|------|------------------|
| Most fields | `char *` | Formatted value (e.g., "12(15)") |
| BL_CONDITION | `unsigned long` | **BITMASK**, not string! |
| BL_GOLD | `char *` | Format: "$:123" (skip first 2 chars for number) |
| BL_FLUSH | - | Special index (-1): Render all changes NOW |
| BL_RESET | - | Special index (-2): Redraw everything |

### Status Field List (23 total)

```
BL_TITLE, BL_STR, BL_DX, BL_CO, BL_IN, BL_WI, BL_CH,
BL_ALIGN, BL_SCORE, BL_CAP, BL_GOLD, BL_ENE, BL_ENEMAX,
BL_XP, BL_AC, BL_HD, BL_TIME, BL_HUNGER, BL_HP, BL_HPMAX,
BL_LEVELDESC, BL_EXP, BL_CONDITION
```

### Condition Bits

```c
BL_MASK_STONE       // Petrifying (DEADLY!)
BL_MASK_SLIME       // Slimed (DEADLY!)
BL_MASK_STRNGL      // Strangling (DEADLY!)
BL_MASK_FOODPOIS    // Food poisoning (DEADLY!)
BL_MASK_TERMILL     // Terminal illness (DEADLY!)
BL_MASK_BLIND       // Blind
BL_MASK_DEAF        // Deaf
BL_MASK_STUN        // Stunned
BL_MASK_CONF        // Confused
BL_MASK_HALLU       // Hallucinating
BL_MASK_LEV         // Levitating
BL_MASK_FLY         // Flying
BL_MASK_RIDE        // Riding
```

## Window Types

| Type | Purpose | Lifecycle | Global Var |
|------|---------|-----------|------------|
| NHW_MESSAGE | Game messages | Persistent | WIN_MESSAGE |
| NHW_STATUS | Status display | **LEGACY - Use status_* instead** | WIN_STATUS |
| NHW_MAP | Dungeon map | Persistent | WIN_MAP |
| NHW_MENU | Menus or text (polymorphic) | Temporary | (none) |
| NHW_TEXT | Full-screen text | Temporary | (none) |

## Text Attributes

```c
ATR_NONE    0  // Normal
ATR_BOLD    1  // Bold/bright
ATR_DIM     2  // Dim/faint
ATR_ULINE   4  // Underlined
ATR_BLINK   5  // Blinking
ATR_INVERSE 7  // Reverse video

// MESSAGE-only flags (can combine with one regular attr):
ATR_URGENT    16  // 0x10 - Needs attention (requires WC2_URGENT_MESG)
ATR_NOHISTORY 32  // 0x20 - Don't save (requires WC2_SUPPRESS_HIST)
```

## Colors

```c
CLR_BLACK, CLR_RED, CLR_GREEN, CLR_BROWN, CLR_BLUE,
CLR_MAGENTA, CLR_CYAN, CLR_GRAY, NO_COLOR,
CLR_ORANGE, CLR_BRIGHT_GREEN, CLR_YELLOW,
CLR_BRIGHT_BLUE, CLR_BRIGHT_MAGENTA,
CLR_BRIGHT_CYAN, CLR_WHITE
```

Total: 16 colors (CLR_MAX = 16)

## Glyph Special Flags

```c
MG_CORPSE   0x01  // Corpse
MG_INVIS    0x02  // Invisible monster marker
MG_DETECT   0x04  // Detected monster (show dimly)
MG_PET      0x08  // Tame monster (highlight if iflags.hilite_pet)
MG_RIDDEN   0x10  // Monster being ridden
MG_STATUE   0x20  // Statue
MG_OBJPILE  0x40  // Multiple objects
MG_BW_LAVA  0x80  // Lava (when no color)
```

## Menu Selection Modes

| Mode | Value | Behavior |
|------|-------|----------|
| PICK_NONE | 0 | Display only, nothing selectable |
| PICK_ONE | 1 | Single selection |
| PICK_ANY | 2 | Multiple selections with counts |

## Menu Commands

```
'^'  - MENU_FIRST_PAGE      (jump to first)
'|'  - MENU_LAST_PAGE       (jump to last)
'>'  - MENU_NEXT_PAGE       (next page)
'<'  - MENU_PREVIOUS_PAGE   (previous page)
'.'  - MENU_SELECT_ALL      (select all)
'-'  - MENU_UNSELECT_ALL    (deselect all)
'@'  - MENU_INVERT_ALL      (toggle all)
','  - MENU_SELECT_PAGE     (select page)
'\\' - MENU_UNSELECT_PAGE   (deselect page)
'~'  - MENU_INVERT_PAGE     (toggle page)
':'  - MENU_SEARCH          (search)
```

## Capability Flags (Key Ones)

### wincap

| Flag | Meaning |
|------|---------|
| WC_COLOR | Can display color |
| WC_HILITE_PET | Can highlight pets |
| WC_TILED_MAP | Graphical tiles (vs ASCII) |
| WC_MOUSE_SUPPORT | Mouse support (requires nh_poskey) |
| WC_PERM_INVENT | Persistent inventory window |
| WC_POPUP_DIALOG | Popup dialog support |
| WC_INVERSE | Inverse video support |

### wincap2

| Flag | Meaning |
|------|---------|
| WC2_FLUSH_STATUS | Receive BL_FLUSH notifications |
| WC2_RESET_STATUS | Receive BL_RESET notifications |
| WC2_HILITE_STATUS | Status highlighting support |
| WC2_HITPOINTBAR | HP bar display |
| WC2_URGENT_MESG | Support ATR_URGENT |
| WC2_SUPPRESS_HIST | Support ATR_NOHISTORY |
| WC2_FULLSCREEN | Fullscreen mode |

### Modern Status Support

To get field-based status updates (instead of legacy string-based):

```c
wincap2 |= WC2_FLUSH_STATUS | WC2_RESET_STATUS | WC2_HILITE_STATUS;
```

## Port Comparison Matrix

| Aspect | TTY | Curses | NDS |
|--------|-----|--------|-----|
| **Lines of code** | ~5K | ~15K | ~10K |
| **Complexity** | Low | High | Medium |
| **Display** | Terminal I/O | Curses library | Hardware framebuffer |
| **Input** | Blocking getchar() | Blocking getch() | Polling loop |
| **Colors** | Termcap codes | Curses pairs | Hardware palette |
| **Mouse** | No (except WIN32) | Yes | Touch screen |
| **Layout** | Dynamic fitting | Window-based | Fixed hardware |
| **Glyphs** | ASCII | ASCII/UTF-8 | 16×16 tiles |
| **Best for** | Reference | Rich UI | Embedded |

## Memory Management Summary

| What | Allocates | Frees | Notes |
|------|-----------|-------|-------|
| menu_item array | Port (select_menu) | **Caller** | MUST free if n > 0 |
| Window structures | Port (create) | Port (destroy) | Port-specific |
| Menu item text | Port (add_menu) | Port (destroy/start) | Must copy core's string |
| identifier in add_menu | Core | Core | Port stores pointer only |
| Status strings | Core (temp) | Core | Port must copy if storing |

## Top 10 Critical Rules

1. **get_nh_event() NEVER blocks** - Called every iteration
2. **nhgetch() MUST block** - Wait for input here
3. **Use field-based status** - Don't parse strings
4. **BL_CONDITION is a bitmask** - Not a string!
5. **Free menu_item arrays** - After select_menu() if n > 0
6. **Never return 0 from nhgetch()** - Map to ESC (033)
7. **Always use mapglyph()** - Don't decode glyphs yourself
8. **getlin() calls flush_screen(1)** - Before prompting
9. **Copy strings in add_menu()** - Core's buffer is temporary
10. **Advertise only what you implement** - Don't claim unsupported caps

## Minimal Starting Configuration

```c
// Minimal capabilities
wincap = WC_COLOR | WC_HILITE_PET | WC_INVERSE;
wincap2 = WC2_FLUSH_STATUS;

// Color support (set for all 16 colors you support)
has_color[CLR_RED] = 1;
has_color[CLR_BLUE] = 1;
// ...

// Use helper functions
windowprocs.win_putmixed = genl_putmixed;
windowprocs.win_outrip = genl_outrip;
windowprocs.win_status_finish = genl_status_finish;
windowprocs.win_can_suspend = genl_can_suspend_yes;  // or _no
windowprocs.win_message_menu = genl_message_menu;

// Must customize
windowprocs.win_init_nhwindows = my_init;
windowprocs.win_exit_nhwindows = my_exit;
windowprocs.win_create_nhwindow = my_create;
windowprocs.win_destroy_nhwindow = my_destroy;
windowprocs.win_display_nhwindow = my_display;
windowprocs.win_putstr = my_putstr;
windowprocs.win_print_glyph = my_print_glyph;
windowprocs.win_nhgetch = my_nhgetch;
windowprocs.win_yn_function = my_yn_function;
windowprocs.win_getlin = my_getlin;
windowprocs.win_start_menu = my_start_menu;
windowprocs.win_add_menu = my_add_menu;
windowprocs.win_end_menu = my_end_menu;
windowprocs.win_select_menu = my_select_menu;
windowprocs.win_status_init = my_status_init;
windowprocs.win_status_enablefield = my_status_enablefield;
windowprocs.win_status_update = my_status_update;
windowprocs.win_get_nh_event = my_get_nh_event;
windowprocs.win_raw_print = my_raw_print;
windowprocs.win_raw_print_bold = my_raw_print_bold;
windowprocs.win_askname = my_askname;
windowprocs.win_nhbell = my_nhbell;
// ... (fill in remaining required functions)
```

## Common Patterns

### Buffered Rendering

```c
// Update
void port_print_glyph(...) {
    buffer[y][x].glyph = glyph;
    buffer[y][x].dirty = TRUE;
}

// Render
void port_display_nhwindow(winid win, boolean blocking) {
    if (win == WIN_MAP) {
        for (each dirty_cell) {
            render_cell(cell);
        }
    }
}
```

### Status Update Cycle

```c
// init once
status_init();
status_enablefield(BL_HP, "HP", "HP:%d(%d)", TRUE);
// ... enable all fields

// update cycle (every turn)
status_update(BL_HP, "12(15)", chg, percent, color, NULL);
status_update(BL_ENE, "5(5)", chg, percent, color, NULL);
// ... update all changed fields
status_update(BL_FLUSH, NULL, 0, 0, 0, NULL);  // RENDER

// finish once
status_finish();
```

### Menu Pattern

```c
winid menu = create_nhwindow(NHW_MENU);
start_menu(menu);
add_menu(menu, NO_GLYPH, &item1, 'a', 0, ATR_NONE, "First", FALSE);
add_menu(menu, NO_GLYPH, &item2, 'b', 0, ATR_NONE, "Second", FALSE);
end_menu(menu, "Pick one:");

menu_item *selected;
int n = select_menu(menu, PICK_ONE, &selected);
if (n > 0) {
    // Use selected[0]
    free(selected);  // MUST FREE
}
destroy_nhwindow(menu);
```

## Key Source Files

| File | Purpose |
|------|---------|
| `include/winprocs.h` | window_procs structure definition |
| `include/wintype.h` | Window types, constants, data structures |
| `include/botl.h` | Status field definitions (BL_* constants) |
| `include/display.h` | Glyph system definitions |
| `src/allmain.c` | Main game loop (moveloop) |
| `src/botl.c` | Status update core code |
| `src/mapglyph.c` | Glyph translation |
| `win/tty/wintty.c` | TTY port (simple reference) |
| `win/curses/curses.c` | Curses port (rich features) |
| `sys/nds/arm9/src/nds_win.c` | NDS port (embedded example) |

## Typical Turn Sequence

```
1. get_nh_event()           ← Non-blocking (every iteration)
2. [Turn processing]
3. bot() → status_update()  ← Update status if changed
4. rhack() → nhgetch()      ← BLOCKS for input
5. Process command
6. vision_recalc()          ← Update map
7. display_nhwindow(MAP)    ← Refresh (periodic)
8. Loop to step 1
```

## Progressive Implementation Path

1. **Core display**: print_glyph, putstr, status (minimal)
2. **Input**: nhgetch, yn_function, getlin
3. **Menus**: start/add/end/select (keyboard-only)
4. **Enhanced features**: mouse, colors, status highlighting
5. **Polish**: help, history, custom UI

## When to Use Which Reference

| Working On | See |
|------------|-----|
| Specific function details | [API Reference](api-reference.md) |
| Understanding concepts (glyphs, menus, status) | [UI Concepts](ui-concepts.md) |
| Timing / call sequences | [Game Loop](game-loop.md) |
| Implementation examples | [Implementation Patterns](implementation-patterns.md) |
| Avoiding mistakes | [Gotchas](gotchas.md) |
| Quick lookups | This document |

## Summary Cheat Sheet

**Blocking**:
- get_nh_event() → NEVER blocks
- nhgetch(), nh_poskey(), yn_function(), getlin(), select_menu() → MUST block

**Status**:
- Use field-based (status_update)
- BL_CONDITION is bitmask
- BL_FLUSH to render

**Menus**:
- create → start → add × N → end → select
- Caller frees array

**Glyphs**:
- Always use mapglyph()
- Returns ch, color, special

**Memory**:
- Copy strings in add_menu()
- Free menu_item arrays
- Port owns window structures

**Capabilities**:
- Only advertise what you implement
- WC2_FLUSH_STATUS for modern status

This quick reference provides fast lookups. For details, see the other reference documents.
