# NetHack Porting Layer - API Reference

Complete reference for the ~40 functions in the `window_procs` structure.

## Structure Overview

```c
struct window_procs {
    const char *name;              // Port name
    unsigned long wincap;          // Capability flags (WC_*)
    unsigned long wincap2;         // Extended capability flags (WC2_*)
    boolean has_color[CLR_MAX];    // Color support table (16 entries)
    
    // ~40 function pointers (documented below)
};

extern NEARDATA struct window_procs windowprocs;  // Global instance
```

Core code calls through macros: `init_nhwindows()` → `(*windowprocs.win_init_nhwindows)()`

## Initialization & Lifecycle

### init_nhwindows(int *argc, char **argv)

**Purpose**: Initialize the windowing system

**When Called**: Very early in startup, before any windows exist

**Must Do**:
- Process port-specific command-line arguments (modify argc/argv to remove consumed args)
- Initialize graphics subsystems, fonts, libraries
- Set `iflags.window_inited = TRUE` when ready

**Critical**: Until this sets `iflags.window_inited`, all messages go through `raw_print()`

**Example Tasks**:
- TTY: Set terminal to raw mode, load termcap/terminfo
- GUI: Initialize windowing toolkit, create main window
- Embedded: Initialize video hardware, load fonts/tiles

### exit_nhwindows(const char *str)

**Purpose**: Shut down windowing system

**When Called**: At game exit (death, quit, ascend)

**Must Do**:
- Dismiss all windows except raw_print window
- Print `str` if provided (goodbye message)
- Clean up resources, restore terminal state
- Free all allocated memory

**After This**: Only `raw_print()` is available

**Example Tasks**:
- TTY: Restore terminal to normal mode
- GUI: Close windows, shut down toolkit
- Embedded: Power off display hardware

### suspend_nhwindows(const char *str)

**Purpose**: Prepare windows for process suspension (Unix ^Z)

**When Called**: Before suspending the process

**Must Do**:
- Save window state
- Print `str` if needed
- Return terminal/display to normal mode

**Note**: Process will be suspended after this returns

### resume_nhwindows(void)

**Purpose**: Restore windows after suspension

**When Called**: After process resumes

**Must Do**:
- Restore terminal/display to game mode
- Reinitialize state
- Redraw everything

### can_suspend(void) -> boolean

**Purpose**: Ask if suspension is allowed now

**When Called**: Before attempting suspension

**Returns**: TRUE if suspension OK, FALSE otherwise

**Helper Functions**: 
- `genl_can_suspend_yes()` - Always returns TRUE
- `genl_can_suspend_no()` - Always returns FALSE

**Use**: Pick one based on your platform (Unix-like → yes, embedded → no)

## Window Management

### create_nhwindow(int type) -> winid

**Purpose**: Create a new window

**When Called**: Throughout gameplay as windows are needed

**Arguments - type**:
- `NHW_MESSAGE` (0) - Message/top line window
- `NHW_STATUS` (1) - Status display (legacy, avoid)
- `NHW_MAP` (2) - Main dungeon map
- `NHW_MENU` (3) - Menu or corner text window
- `NHW_TEXT` (4) - Full-screen text/help window

**Returns**: A `winid` (currently int, treat opaquely)

**Notes**: 
- Window is created but not displayed yet
- Port allocates and tracks window data
- Must support being called many times (especially for NHW_MENU)

**Standard Windows** (created early, persist entire game):
- `WIN_MESSAGE` = create_nhwindow(NHW_MESSAGE)
- `WIN_MAP` = create_nhwindow(NHW_MAP)
- Status via `status_initialize()` (modern) or `WIN_STATUS` (legacy)

### clear_nhwindow(winid window)

**Purpose**: Clear window contents for reuse

**When Called**: When window needs to be reused with fresh content

**Must Do**:
- Clear all text/graphics from window
- Reset cursor/scroll position
- Keep window allocated and visible state unchanged

**Does NOT**: Destroy or hide the window

**Common Use**: Clear MESSAGE window, clear MENU before building new menu

### display_nhwindow(winid window, boolean blocking)

**Purpose**: Make window visible and display its contents

**When Called**: After window content is ready

**Arguments**:
- `blocking`: If TRUE, must wait for user acknowledgment before returning
  - For WIN_MESSAGE: TTY shows "--More--" prompt
  - For WIN_MENU/TEXT: Wait for user to dismiss
  - For WIN_MAP: Usually non-blocking

**Notes**:
- Non-blocking allows async rendering
- All calls are effectively blocking in TTY port
- GUI ports can return immediately for non-blocking

### destroy_nhwindow(winid window)

**Purpose**: Dismiss and deallocate a window

**When Called**: When window is no longer needed

**Must Do**:
- Dismiss window if visible
- Free all associated memory (text, menu items, graphics)
- Invalidate the winid

**Notes**:
- Standard windows (MESSAGE, MAP) are rarely destroyed
- MENU/TEXT windows created/destroyed frequently

## Output - Text

### putstr(winid window, int attr, const char *str)

**Purpose**: Print a line of text to a window

**When Called**: For messages, menu/text content, help, etc.

**Arguments**:
- `window`: Target window
- `attr`: Display attribute (see below)
- `str`: Printable ASCII only (040-0126), null-terminated

**Attributes**:
- `ATR_NONE` (0) - Normal text
- `ATR_BOLD` (1) - Bold/bright
- `ATR_DIM` (2) - Dim/faint
- `ATR_ULINE` (4) - Underlined
- `ATR_BLINK` (5) - Blinking
- `ATR_INVERSE` (7) - Reverse video

**Special (MESSAGE only)**:
- `ATR_URGENT` (0x10) - Urgent message (requires WC2_URGENT_MESG)
- `ATR_NOHISTORY` (0x20) - Don't save to history (requires WC2_SUPPRESS_HIST)

**Behavior**:
- Multiple calls print on separate lines
- Port may compress spaces, break lines, or truncate as needed
- Must clear to end-of-line after breaks
- Consecutive putstr() calls should be visible in order

**CRITICAL for NHW_MENU**: First putstr() commits window to **text mode** - cannot use as menu afterward!

### putmixed(winid window, int attr, const char *str)

**Purpose**: Like putstr() but supports embedded glyph encoding

**When Called**: For messages that need to display glyphs inline (rare)

**Encoding**: `\\GXXXXNNNN` where:
- `XXXX` = validation code from `context.rndencode`
- `NNNN` = hexadecimal glyph number

**Fallback**: Most ports use `genl_putmixed()` which strips glyph encoding and displays as characters

**Use This**: If you don't need special glyph embedding support:
```c
windowprocs.win_putmixed = genl_putmixed;
```

### raw_print(const char *str)

**Purpose**: Print directly to screen, bypassing window system

**When Called**:
- Before windowing is initialized
- For error messages
- During critical operations
- For debugging

**Behavior**:
- Appends newline automatically
- Must guarantee user sees it (flush output, block if needed)
- Need not recognize control characters
- Should work even if windowing is broken

**Implementation**:
- TTY: Direct printf()
- GUI: Popup dialog or stderr
- Embedded: Debug console or screen overlay

### raw_print_bold(const char *str)

**Purpose**: Like raw_print() but in bold/standout

**When Called**: For emphasis in critical messages

**Can Fallback**: To raw_print() if bold not available

### curs(winid window, int x, int y)

**Purpose**: Position cursor in window

**When Called**: For status updates, screen locating (identify, teleport)

**Coordinates**: 1 ≤ x < cols, 0 ≤ y < rows

**Notes**:
- Mostly obsolete (field-based status doesn't use it)
- NHW_MESSAGE, NHW_MENU, NHW_TEXT don't support it in TTY
- Behavior outside window bounds is unspecified
- Can be no-op for most modern ports

## Output - Graphics

### print_glyph(winid window, xchar x, xchar y, int glyph, int bkglyph)

**Purpose**: Draw a glyph (game entity representation) on the map

**When Called**: To update map display (constantly during gameplay)

**Arguments**:
- `window`: Usually WIN_MAP
- `x, y`: Map coordinates (0-based)
- `glyph`: Integer glyph ID (use `mapglyph()` to translate!)
- `bkglyph`: Background glyph for tiled environments (NO_GLYPH if none)

**CRITICAL**: Always call `mapglyph()` to translate glyph to symbol/color:

```c
int ch, color;
unsigned special;
mapglyph(glyph, &ch, &color, &special, x, y, 0);
```

**Outputs from mapglyph()**:
- `ch`: Character/symbol to display ('@', 'g', '#', etc.)
- `color`: Color (CLR_RED, CLR_BLUE, etc. or NO_COLOR)
- `special`: Flags (MG_PET, MG_CORPSE, MG_DETECT, MG_STATUE, etc.)

**Special Flags**:
```c
MG_CORPSE   0x01  // It's a corpse
MG_INVIS    0x02  // Invisible monster marker
MG_DETECT   0x04  // Detected monster (show dimly)
MG_PET      0x08  // Tame monster (highlight if iflags.hilite_pet)
MG_RIDDEN   0x10  // Monster being ridden
MG_STATUE   0x20  // Statue
MG_OBJPILE  0x40  // Multiple objects here
MG_BW_LAVA  0x80  // Lava (when no color available)
```

**Background Glyph**:
- For ASCII: Usually ignored (pass NO_GLYPH)
- For tiles: Draw background tile before foreground

**Port Decides**: How to render (ASCII characters, graphical tiles, etc.)

## Input

### nhgetch(void) -> int

**Purpose**: Get a single character from user

**When Called**: For commands, menu selection, prompts

**MUST**: Block until input available

**Returns**: Non-zero character (must not return 0 or meta-zero)

**Special Handling**:
- Must return ASCII 033 (ESC) instead of EOF or NUL
- If `program_state.done_hup` is set (SAFERHANGUP), return ESC immediately
- Never return 0 (reserved for mouse in nh_poskey)

**Example**:
```c
int port_nhgetch(void) {
    int ch = getchar();  // Or platform equivalent - OK to block!
    
    if (ch == 0 || ch == EOF)
        ch = '\033';  // Map to ESC
    
    return ch;
}
```

### nh_poskey(int *x, int *y, int *mod) -> int

**Purpose**: Get character OR mouse position

**When Called**: For mouse-aware input (if WC_MOUSE_SUPPORT advertised)

**MUST**: Block until input available

**Returns**:
- **Non-zero**: Character was typed (like nhgetch), ignore x/y/mod
- **Zero**: Mouse click, position in `*x`, `*y`, type in `*mod`

**Mouse Types** (mod):
- `CLICK_1` (1) - Primary click
- `CLICK_2` (2) - Secondary click

**If No Mouse**: Can just call nhgetch() and always return non-zero

**Example**:
```c
int port_nh_poskey(int *x, int *y, int *mod) {
    if (keyboard_input()) {
        return read_key();  // Non-zero
    }
    
    if (mouse_click()) {
        *x = mouse_x;
        *y = mouse_y;
        *mod = CLICK_1;
        return 0;  // Zero = mouse
    }
    
    // Block until something happens
    wait_for_input();
    return port_nh_poskey(x, y, mod);
}
```

### getlin(const char *ques, char *input)

**Purpose**: Get a line of text input

**When Called**: For player name, extended commands, #name, etc.

**MUST**: Call `flush_screen(1)` first!

```c
void port_getlin(const char *ques, char *input) {
    flush_screen(1);  // CRITICAL!
    
    display_prompt(ques);
    // Read line into input buffer
}
```

**Behavior**:
- ESC cancels: Return "\\033\\000" (two chars: ESC and NUL)
- Input buffer is at least BUFSZ bytes
- Must truncate and null-terminate
- Read until newline/RETURN

**Display**: 
- TTY: Uses top line
- GUI: May use popup dialog

### get_ext_cmd(void) -> int

**Purpose**: Get extended command selection

**When Called**: When player presses '#'

**Returns**: Index into `extcmdlist[]`, or -1 if cancelled

**UI is Port-Specific**:
- Simple: Use getlin() and match against extcmdlist
- Menu: Show all commands in a menu
- Fancy: Autocomplete interface (curses)

**Example**:
```c
int port_get_ext_cmd(void) {
    char buf[BUFSZ];
    getlin("Extended command:", buf);
    
    if (buf[0] == '\033')
        return -1;  // Cancelled
    
    // Find in extcmdlist
    for (int i = 0; extcmdlist[i].ef_txt; i++) {
        if (!strcmp(buf, extcmdlist[i].ef_txt))
            return i;
    }
    
    pline("Unknown command.");
    return -1;
}
```

### yn_function(const char *ques, const char *choices, char default) -> char

**Purpose**: Get yes/no or single-character choice

**When Called**: For confirmations, options

**Arguments**:
- `ques`: Prompt (≤ QBUFSZ-1 chars)
- `choices`: Acceptable characters (lowercase), or NULL for any
- `default`: Default choice (if SPACE/RETURN pressed)

**ESC Handling** (try in order):
1. If 'q' in choices, return 'q'
2. If 'n' in choices, return 'n'
3. Return default

**SPACE/RETURN/NEWLINE**: Return default

**'#' in choices**: Accept count, store in `yn_number`, return '#'

**ESC in choices**: Characters after ESC are accepted but not shown in prompt

**NULL choices**: Accept any character, preserve case

**Example**:
```c
char port_yn_function(const char *ques, const char *choices, char def) {
    display_prompt(ques);
    
    int ch = nhgetch();
    
    if (ch == '\033') {  // ESC
        if (choices && strchr(choices, 'q')) return 'q';
        if (choices && strchr(choices, 'n')) return 'n';
        return def;
    }
    
    if (ch == ' ' || ch == '\r' || ch == '\n')
        return def;
    
    if (!choices)
        return ch;  // Any char accepted
    
    if (strchr(choices, tolower(ch)))
        return tolower(ch);
    
    nhbell();  // Invalid choice
    return port_yn_function(ques, choices, def);  // Retry
}
```

**Note**: Core has wrapper in cmd.c that validates buffer sizes

## Menus

Menus use a four-phase pattern: **create → start → add items → end → select**.

### start_menu(winid window)

**Purpose**: Begin building a menu

**When Called**: Before any add_menu() calls

**Requirements**: 
- Must call on NHW_MENU window
- Must call before any add_menu()

**CRITICAL**: After this, cannot use putstr() on this window (committed to menu mode)

### add_menu(winid window, int glyph, const anything *identifier, char accelerator, char groupacc, int attr, const char *str, boolean preselected)

**Purpose**: Add one line to the menu

**Arguments**:
- `glyph`: Optional icon (NO_GLYPH if none) - port may ignore
- `identifier`: Value returned if selected
  - If 0/NULL: Non-selectable title line
  - Otherwise: Pointer to data identifying this item (obj*, int, char*, etc.)
- `accelerator`: Keystroke to select
  - 'a'-'z', 'A'-'Z': Use this specific key
  - 0: Port assigns automatically (a, b, c, ...)
  - Other: Special cases (player selection uses '*')
- `groupacc`: Group selection key
  - Number or letter: Select all with this group key (e.g., ')' for all weapons)
  - 0: Not part of a group
- `attr`: Display attribute (ATR_BOLD, ATR_NONE, etc.)
- `str`: Menu text - **MUST COPY** (core's buffer is temporary!)
- `preselected`: TRUE = pre-checked in PICK_ANY menus

**CRITICAL**: Port must copy `str` - core's buffer may be reused

**Accelerator Strategy**: 
- Don't mix caller-assigned (accelerator != 0) and port-assigned (accelerator == 0) in same menu!
- Port should make accelerator visible (e.g., "a - long sword")

**Group Accelerators**:
- May conflict with menu commands (^, <, >, ., etc.)
- Menu commands take precedence

### end_menu(winid window, const char *prompt)

**Purpose**: Finish adding menu items

**Arguments**: `prompt` shown to user (NULL = no prompt)

**Notes**: Makes menu ready for select_menu() but doesn't display yet

### select_menu(winid window, int how, menu_item **selected) -> int

**Purpose**: Display menu and get user selections

**Arguments**:
- `how`: Selection mode
  - `PICK_NONE` (0) - Display only, nothing selectable
  - `PICK_ONE` (1) - Single selection
  - `PICK_ANY` (2) - Multiple selections with counts
- `selected`: Output pointer for results array

**Returns**:
- **n > 0**: Selected n items, `*selected` is allocated array
- **0**: Nothing selected (valid - user accepted empty selection)
- **-1**: Explicitly cancelled (ESC)

**Output Structure**:
```c
typedef struct menu_item {
    anything item;  // The identifier from add_menu()
    long count;     // User-supplied count, or -1 for "all"
} menu_item;
```

**CRITICAL**: Port allocates array, **caller MUST free** if n > 0:
```c
int n = select_menu(menu, PICK_ANY, &selected);
if (n > 0) {
    // Use selected[0..n-1]
    free(selected);  // MUST FREE!
}
```

**Menu Commands** (port should support):
```c
'^'  - MENU_FIRST_PAGE
'|'  - MENU_LAST_PAGE
'>'  - MENU_NEXT_PAGE
'<'  - MENU_PREVIOUS_PAGE
'.'  - MENU_SELECT_ALL
'-'  - MENU_UNSELECT_ALL
'@'  - MENU_INVERT_ALL
','  - MENU_SELECT_PAGE
'\\' - MENU_UNSELECT_PAGE
'~'  - MENU_INVERT_PAGE
':'  - MENU_SEARCH
```

**Persistence**: Menu survives multiple select_menu() calls - only destroyed by start_menu() or destroy_nhwindow()

### message_menu(char let, int how, const char *mesg) -> char

**Purpose**: TTY-specific hack for context-sensitive help

**When Called**: When prompt is active, to show help without interrupting

**Most Ports**: Use `genl_message_menu()` which does nothing:
```c
windowprocs.win_message_menu = genl_message_menu;
```

**TTY Behavior**: Sends `mesg` to message window with "--More--", makes `let` valid to dismiss

## Status Display

Modern status uses **field-based** updates, not string formatting.

### status_init(void)

**Purpose**: Initialize status display

**When Called**: Once at startup (via `status_initialize()` in botl.c)

**Responsibilities**: 
- Allocate resources for status window/area
- Set up layout structures
- Initialize fonts, colors

### status_enablefield(int fldindex, const char *name, const char *fmt, boolean enable)

**Purpose**: Enable or disable a status field

**When Called**: At startup and when fields become relevant (e.g., BL_ENE when first gain magic)

**Arguments**:
- `fldindex`: Field ID (see below)
- `name`: Field name ("HP", "Str", "Gold", etc.)
- `fmt`: Format string ("HP:%d(%d)", "Str:%d", etc.) - informational
- `enable`: TRUE to enable, FALSE to disable

**Status Fields** (23 total):
```c
BL_TITLE = 0   // Role/rank
BL_STR         // Strength
BL_DX          // Dexterity
BL_CO          // Constitution
BL_IN          // Intelligence
BL_WI          // Wisdom
BL_CH          // Charisma
BL_ALIGN       // Alignment
BL_SCORE       // Score
BL_CAP         // Encumbrance
BL_GOLD        // Gold (format: "$:123")
BL_ENE         // Current power
BL_ENEMAX      // Max power
BL_XP          // Experience level
BL_AC          // Armor class
BL_HD          // Hit dice (when polymorphed)
BL_TIME        // Turn count
BL_HUNGER      // Hunger state
BL_HP          // Current HP
BL_HPMAX       // Max HP
BL_LEVELDESC   // Dungeon level description
BL_EXP         // Experience points
BL_CONDITION   // Status conditions (SPECIAL - see below)
```

**Port Should**:
- Allocate display area for enabled fields
- Mark field as enabled/disabled for update tracking

### status_update(int fldindex, genericptr_t ptr, int chg, int percent, int color, unsigned long *colormasks)

**Purpose**: Update a status field value

**When Called**: When game state changes (HP, gold, hunger, etc.)

**Special fldindex Values**:
- `BL_FLUSH (-1)`: **Render all buffered changes NOW** (end of update cycle)
- `BL_RESET (-2)`: **Redisplay everything** (even unchanged fields) - window resized, mode changed

**For Regular Fields** (most):
- `ptr`: `char *` with formatted value ("12(15)", "Lawful", "Burdened", etc.)
- `chg`: Change indicator (for highlighting - currently unused)
- `percent`: Percentage (for highlighting - HP/max ratio, etc.)
- `color`: `(color & 0xFF)` = CLR_*, `(color >> 8)` = attribute

**BL_CONDITION - SPECIAL HANDLING**:
- `ptr`: **`unsigned long` bitmask** (NOT a string!)
- `colormasks`: Array where `colormasks[CLR_RED]` = bitmask of conditions to show in red
- Condition bits: BL_MASK_STONE, BL_MASK_STUN, BL_MASK_CONF, BL_MASK_BLIND, etc.

**Example**:
```c
case BL_CONDITION:
    unsigned long cond = (unsigned long)ptr;  // BITMASK!
    
    if (cond & BL_MASK_STONE) {
        // Find color from colormasks
        int color = find_color_for_condition(BL_MASK_STONE, colormasks);
        display_condition("Stone", color);
    }
    // ... check other condition bits
    break;
```

**BL_GOLD - Special Format**:
- Value is "$:123" (includes "$:" prefix)
- Skip first 2 chars if you just want the number: `(char *)ptr + 2`

**Pattern**:
```c
void port_status_update(int fld, genericptr_t ptr, int chg, 
                        int percent, int color, unsigned long *colormasks) {
    switch (fld) {
    case BL_FLUSH:
        render_all_buffered_changes();
        return;
        
    case BL_RESET:
        force_complete_redraw();
        return;
        
    case BL_HP:
        sscanf((char *)ptr, "%d(%d)", &hp, &hpmax);
        buffer_field_update(fld, hp, hpmax, color);
        break;
        
    case BL_CONDITION:
        unsigned long cond = (unsigned long)ptr;
        buffer_conditions(cond, colormasks);
        break;
        
    case BL_GOLD:
        const char *gold_str = (char *)ptr + 2;  // Skip "$:"
        buffer_field_update(fld, atol(gold_str), color);
        break;
        
    default:
        buffer_field_update(fld, (char *)ptr, color);
        break;
    }
}
```

**Update Cycle**:
```
status_update(BL_HP, "12(15)", ...);
status_update(BL_ENE, "5(5)", ...);
status_update(BL_GOLD, "$:123", ...);
status_update(BL_CONDITION, (genericptr_t)conditions, ..., colormasks);
status_update(BL_FLUSH, NULL, 0, 0, 0, NULL);  // ← RENDER NOW!
```

**Port Should**: Buffer updates, render only on BL_FLUSH (for efficiency)

### status_finish(void)

**Purpose**: Tear down status display

**When Called**: At game exit

**Responsibilities**: Free status resources

**Most Ports**: Use `genl_status_finish()`:
```c
windowprocs.win_status_finish = genl_status_finish;
```

## File Display

### display_file(const char *filename, boolean complain)

**Purpose**: Display a text file

**When Called**: For help system, license, credits, etc.

**Arguments**:
- `filename`: File to display
- `complain`: TRUE = show error if file missing

**Implementation**:
- Create NHW_TEXT window
- Read file line by line
- Call putstr() for each line
- display_nhwindow(win, TRUE) - blocking
- destroy_nhwindow(win)

**File Location**: 
- Help files usually in HACKDIR
- May need to search multiple paths

## Synchronization

### get_nh_event(void)

**Purpose**: Process window events (exposure, resize, mouse, etc.)

**When Called**: **EVERY iteration** of main game loop (potentially 100s of times per second)

**CRITICAL RULE**: **MUST NOT BLOCK** - return immediately

**What It Does**:
- **TTY**: No-op (returns immediately)
- **GUI**: Process:
  - Window exposure/redraw events
  - Window resize events
  - Mouse movements (don't consume input, just note)
  - Queued window system messages
  - Animation timers
  - Background tasks

**Example**:
```c
void port_get_nh_event(void) {
    // Process pending events
    while (event_pending()) {
        process_event();
    }
    
    // Update display if needed
    if (need_redraw) {
        redraw();
    }
    
    // RETURN IMMEDIATELY - don't wait for input!
    return;
}
```

**Why This Matters**: Game loop needs to continue to check for multi-turn commands, update animations, handle game logic. If this blocks, game freezes.

### mark_synch(void)

**Purpose**: Mark a synchronization point

**When Called**: To establish ordering guarantees

**Can Be**: No-op for most ports

**GUI Ports**: May note sync point in render queue

### wait_synch(void)

**Purpose**: Block until all output is complete

**When Called**: Before operations requiring stable display

**Responsibilities**:
- Flush all output streams
- Handle pending exposure events
- Ensure display is current

**Example**:
```c
void port_wait_synch(void) {
    fflush(stdout);  // Or equivalent
    
    // Process pending redraw events
    while (redraw_pending()) {
        process_redraw();
    }
    
    // Wait for rendering to complete
    wait_for_render_complete();
}
```

**TTY/NDS**: Can be simple `fflush(stdout)`

### delay_output(void)

**Purpose**: Visible 50ms delay

**When Called**: For animations, explosions, zap beams, etc.

**Implementation**: Like wait_synch() + nap(50ms)

**Can Be Async**: Port decides whether to block or return immediately and delay render

**Example**:
```c
void port_delay_output(void) {
    wait_synch();      // Ensure current output visible
    usleep(50000);     // 50ms
}
```

## Other Functions

### player_selection(void)

**Purpose**: Get player role/race/alignment

**When Called**: At game start (if port provides GUI)

**Responsibilities**:
- Fill in `pl_character[0]` array (role, race, alignment, gender)
- If offering Quit option, must clean up and exit
- Can use menus, dialogs, or prompts

**Port-Specific**: TTY uses simple prompts, GUI can use rich dialogs

### askname(void)

**Purpose**: Get player name

**When Called**: At game start

**Responsibilities**:
- Prompt for name
- Store in `plname[]`
- Handle default names

**Usually**: Uses getlin() or custom dialog

### update_inventory(void)

**Purpose**: Notify port that inventory changed

**When Called**: After inventory modifications (pickup, drop, use, etc.)

**For**: Ports with persistent inventory windows (WC_PERM_INVENT)

**Can Be**: No-op if no persistent inventory

### doprev_message(void) -> int

**Purpose**: Show message history

**When Called**: ^P command

**TTY**: Scrolls WIN_MESSAGE back one line

**Most Ports**: Use `genl_doprev_message()`

### nhbell(void)

**Purpose**: Beep/alert

**When Called**: For errors, alerts, attention needed

**Implementation**: Simple beep (until sound system exists)

**Example**: `printf("\a")` or platform beep API

### number_pad(int state)

**Purpose**: Configure number pad mode

**When Called**: When number_pad option changes

**Can Be**: No-op if not relevant

### cliparound(int x, int y)

**Purpose**: Center view on player

**When Called**: When map is larger than screen

**Only If**: CLIPPING defined

**Responsibilities**: Adjust viewport to center on (x, y)

### update_positionbar(char *features)

**Purpose**: Update position bar with landmarks

**When Called**: To show stairs, player position on clipped maps

**Only If**: POSITIONBAR defined

**Arguments**: Pairs of (symbol, column), null-terminated
- '<': upstairs
- '>': downstairs
- '@': player

**Shows**: Horizontal bar with landmark positions

### outrip(winid window, int how, time_t when)

**Purpose**: Display tombstone/endgame

**When Called**: At death/ascension

**Most Ports**: Use `genl_outrip()`:
```c
windowprocs.win_outrip = genl_outrip;
```

**Custom**: Can implement custom death screen

### preference_update(const char *pref)

**Purpose**: Notification of preference change

**When Called**: When user changes wincap option

**Note**: Only notified of options port advertised in wincap

**Can Be**: No-op if no dynamic reconfiguration

### getmsghistory(boolean init) -> char *

**Purpose**: Retrieve messages for saving

**When Called**: During save, repeatedly

**Behavior**:
- `init=TRUE`: Reset to start
- Return oldest message first
- Successive calls return newer messages
- Return NULL when done

**Most Ports**: Use `genl_getmsghistory()`

### putmsghistory(const char *msg, boolean is_restoring)

**Purpose**: Restore messages from savefile

**When Called**: During restore, repeatedly

**Behavior**: Add messages to history in order received

**Most Ports**: Use `genl_putmsghistory()`

### start_screen(void) / end_screen(void)

**Purpose**: TTY-specific screen initialization/cleanup

**When Called**: To enter/leave full-screen mode

**Non-TTY Ports**: Empty stubs

## Capability Flags

### wincap Flags

```c
WC_COLOR            // Can display color
WC_HILITE_PET       // Can highlight pets
WC_ASCII_MAP        // ASCII map support
WC_TILED_MAP        // Graphical tiles
WC_PRELOAD_TILES    // Pre-load tile files
WC_TILE_WIDTH       // Prefer tile width setting
WC_TILE_HEIGHT      // Prefer tile height setting
WC_TILE_FILE        // Alternative tile file
WC_INVERSE          // Inverse video support
WC_ALIGN_MESSAGE    // Message alignment option
WC_ALIGN_STATUS     // Status alignment option
WC_VARY_MSGCOUNT    // Variable message line count
WC_FONT_MAP         // Map font selection
WC_FONT_MESSAGE     // Message font selection
WC_FONT_STATUS      // Status font selection
WC_FONT_MENU        // Menu font selection
WC_FONT_TEXT        // Text font selection
WC_FONTSIZ_MAP      // Map font size
WC_FONTSIZ_MESSAGE  // Message font size
WC_FONTSIZ_STATUS   // Status font size
WC_FONTSIZ_MENU     // Menu font size
WC_FONTSIZ_TEXT     // Text font size
WC_SCROLL_MARGIN    // Map scroll margin
WC_SPLASH_SCREEN    // Splash screen support
WC_POPUP_DIALOG     // Popup dialog support
WC_SCROLL_AMOUNT    // Scroll amount setting
WC_EIGHT_BIT_IN     // 8-bit character input
WC_PERM_INVENT      // Persistent inventory window
WC_MAP_MODE         // Map mode option
WC_WINDOWCOLORS     // Window color customization
WC_PLAYER_SELECTION // GUI player selection
WC_MOUSE_SUPPORT    // Mouse support (requires nh_poskey)
```

### wincap2 Flags

```c
WC2_FULLSCREEN      // Fullscreen mode
WC2_SOFTKEYBOARD    // Software keyboard
WC2_WRAPTEXT        // Text wrapping
WC2_HILITE_STATUS   // Status highlighting
WC2_SELECTSAVED     // Saved game selection menu
WC2_DARKGRAY        // Bold black for dark gray
WC2_HITPOINTBAR     // HP bar display
WC2_FLUSH_STATUS    // Receive BL_FLUSH notifications
WC2_RESET_STATUS    // Receive BL_RESET notifications
WC2_TERM_SIZE       // Terminal size setting
WC2_STATUSLINES     // 2 or 3 status lines
WC2_WINDOWBORDERS   // Window borders
WC2_PETATTR         // Pet highlighting attributes
WC2_GUICOLOR        // Colors outside map
WC2_URGENT_MESG     // Urgent message support (ATR_URGENT)
WC2_SUPPRESS_HIST   // History suppression (ATR_NOHISTORY)
```

### Modern Status Support

To get field-based status updates (instead of legacy string-based):
```c
wincap2 |= WC2_FLUSH_STATUS | WC2_RESET_STATUS | WC2_HILITE_STATUS;
```

### Color Support

```c
boolean has_color[CLR_MAX];  // 16 entries

// Set to 1 for supported colors, 0 for unsupported:
has_color[CLR_BLACK] = 1;
has_color[CLR_RED] = 1;
has_color[CLR_GREEN] = 1;
// ... etc for all 16 colors
```

**Colors**:
```c
CLR_BLACK, CLR_RED, CLR_GREEN, CLR_BROWN, CLR_BLUE,
CLR_MAGENTA, CLR_CYAN, CLR_GRAY, NO_COLOR, CLR_ORANGE,
CLR_BRIGHT_GREEN, CLR_YELLOW, CLR_BRIGHT_BLUE,
CLR_BRIGHT_MAGENTA, CLR_BRIGHT_CYAN, CLR_WHITE
```

## Required vs Optional

### Always Required
- init_nhwindows, exit_nhwindows, suspend_nhwindows, resume_nhwindows, can_suspend
- create_nhwindow, clear_nhwindow, display_nhwindow, destroy_nhwindow
- putstr, print_glyph, raw_print, raw_print_bold
- nhgetch, yn_function, getlin
- start_menu, add_menu, end_menu, select_menu
- status_init, status_enablefield, status_update, status_finish
- get_nh_event, mark_synch, wait_synch, delay_output
- askname, nhbell

### Conditionally Required
- nh_poskey - If WC_MOUSE_SUPPORT set
- putmixed - Can use genl_putmixed
- cliparound - Only if CLIPPING defined
- update_positionbar - Only if POSITIONBAR defined

### Optional/Stub-able
- curs (mostly obsolete)
- player_selection, get_ext_cmd (can use simple prompts)
- update_inventory (no-op if no persistent inventory)
- doprev_message, message_menu (use genl_*)
- start_screen, end_screen (empty for non-TTY)
- outrip (use genl_outrip)
- preference_update, number_pad (no-op if not needed)
- getmsghistory, putmsghistory (use genl_*)

## Helper Functions You Should Use

```c
// Unless you need glyph embedding:
windowprocs.win_putmixed = genl_putmixed;

// Unless you want custom tombstone:
windowprocs.win_outrip = genl_outrip;

// Almost everyone uses:
windowprocs.win_status_finish = genl_status_finish;

// Simple yes/no answer:
windowprocs.win_can_suspend = genl_can_suspend_yes;  // or _no

// Unless TTY-specific needs:
windowprocs.win_message_menu = genl_message_menu;

// Message history (if you want basic support):
windowprocs.win_getmsghistory = genl_getmsghistory;
windowprocs.win_putmsghistory = genl_putmsghistory;

// Previous message (basic support):
windowprocs.win_doprev_message = genl_doprev_message;
```

## Summary

The window_procs API provides ~40 functions for:
- Lifecycle: init, exit, suspend, resume
- Windows: create, clear, display, destroy
- Output: text (putstr), graphics (print_glyph), status (status_update)
- Input: character (nhgetch), mouse (nh_poskey), prompts (yn_function, getlin), menus (select_menu)
- Sync: event processing (get_nh_event), output flushing (wait_synch)

**Key distinctions**:
- get_nh_event(): Non-blocking, called constantly
- nhgetch()/nh_poskey(): Blocking, called only when input needed
- BL_CONDITION: Bitmask, not string
- BL_FLUSH/BL_RESET: Special field indices for rendering
- Menu allocations: Port allocates, caller frees

**Use helper functions** where appropriate to reduce implementation burden.
