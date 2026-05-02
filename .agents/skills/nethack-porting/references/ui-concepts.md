# NetHack Porting Layer - UI Concepts

Core UI concepts you need to understand when implementing a NetHack port.

## Window Types

NetHack defines five window types, each with distinct purpose and lifecycle.

### NHW_MESSAGE - Message Window

**Purpose**: Display game messages and narrative text

**Global Variable**: `WIN_MESSAGE`

**Lifecycle**: Created early in startup, persists entire game

**Characteristics**:
- Usually appears at top of screen (configurable via WC_ALIGN_MESSAGE)
- May have multiple lines (WC_VARY_MSGCOUNT)
- Supports message history (scrollback via ^P)
- Text flows continuously - older messages scroll away
- `display_nhwindow(WIN_MESSAGE, TRUE)` shows "--More--" prompt in TTY

**Usage Pattern**:
```c
pline("You hit the orc!");  // Core uses pline() → putstr(WIN_MESSAGE, ...)
You("miss.");               // "You miss."
Your("sword glows!");       // "Your sword glows!"
The("door opens.");         // "The door opens."
```

**Special Attributes** (MESSAGE only):
- `ATR_URGENT` (0x10) - Urgent messages requiring attention (requires WC2_URGENT_MESG)
- `ATR_NOHISTORY` (0x20) - Don't save to history (requires WC2_SUPPRESS_HIST)

**Example**:
```c
putstr(WIN_MESSAGE, ATR_BOLD | ATR_URGENT, "The orc hits you!");
```

### NHW_STATUS - Status Window (LEGACY)

**Purpose**: Display character statistics

**CRITICAL**: This type is **LEGACY** and being phased out

**Modern Approach**: Use field-based `status_*` functions instead

**Why Avoid**:
- String parsing is error-prone and fragile
- No support for highlighting or customization
- Field-based approach gives port full control over layout and rendering
- Can't handle all fields properly (especially BL_CONDITION)

**What to Do Instead**:
- Don't create NHW_STATUS window
- Set `wincap2 |= WC2_FLUSH_STATUS | WC2_HILITE_STATUS`
- Implement status_init(), status_enablefield(), status_update()
- Port controls its own layout and rendering

### NHW_MAP - Main Map Window

**Purpose**: Display the dungeon map (primary game view)

**Global Variable**: `WIN_MAP`

**Lifecycle**: Created early in startup, persists entire game

**Characteristics**:
- Shows 21×79 (or larger) dungeon grid
- Updated via `print_glyph(WIN_MAP, x, y, glyph, bkglyph)`
- Supports clipping (`cliparound()`) if map > screen
- May be ASCII or tiled graphics (WC_ASCII_MAP, WC_TILED_MAP)

**Coordinate System**:
- X: 0 to COLNO-1 (typically 0-78)
- Y: 0 to ROWNO-1 (typically 0-20)
- (0,0) is top-left

**Update Pattern**:
```c
// Draw a monster
print_glyph(WIN_MAP, x, y, mon_to_glyph(monster), NO_GLYPH);

// Draw terrain
print_glyph(WIN_MAP, x, y, cmap_to_glyph(S_room), NO_GLYPH);

// Refresh display
display_nhwindow(WIN_MAP, FALSE);
```

**Viewport Management** (if map > screen):
```c
cliparound(u.ux, u.uy);  // Center on player
```

### NHW_MENU - Menu/Corner Text Window (POLYMORPHIC!)

**Purpose**: Flexible window for menus OR text displays

**CRITICAL**: NHW_MENU windows are **polymorphic** (one-way commitment):
- First call to `start_menu()` → becomes a **menu** (can't use putstr after)
- First call to `putstr()` → becomes **text display** (can't use start_menu after)
- **Cannot change mode** after first use!

**Lifecycle**: Created on-demand, destroyed after use

**As a Menu**:
- Inventory selection (drop, eat, wear, etc.)
- Spell selection
- Pick-up menus
- Any list-based choice
- Uses: start_menu → add_menu × N → end_menu → select_menu

**As Text Display**:
- Help pages
- Short text files
- Status dumps
- Corner messages
- Uses: putstr × N → display_nhwindow

**Selection Modes** (for menus):
```c
PICK_NONE (0)  // Display only, no selection
PICK_ONE  (1)  // Single selection
PICK_ANY  (2)  // Multiple selections with counts
```

**TTY Behavior**:
- If content fits in corner: displayed there (doesn't overwrite map)
- If too large: takes full screen with paging ("--More--")

**Example - Menu**:
```c
winid menu = create_nhwindow(NHW_MENU);
start_menu(menu);  // ← Commits to MENU mode
add_menu(menu, NO_GLYPH, &sword, 'a', 0, ATR_NONE, "long sword", FALSE);
add_menu(menu, NO_GLYPH, &shield, 'b', 0, ATR_NONE, "shield", FALSE);
end_menu(menu, "What to drop?");

menu_item *selected;
int n = select_menu(menu, PICK_ANY, &selected);
if (n > 0) {
    // Process selected[0..n-1]
    free(selected);  // MUST FREE!
}
destroy_nhwindow(menu);
```

**Example - Text**:
```c
winid text = create_nhwindow(NHW_MENU);
putstr(text, ATR_BOLD, "Stats");  // ← Commits to TEXT mode
putstr(text, ATR_NONE, "HP: 15/15");
putstr(text, ATR_NONE, "Pw: 5/5");
display_nhwindow(text, TRUE);  // Blocking
destroy_nhwindow(text);
```

### NHW_TEXT - Full-Screen Text Window

**Purpose**: Display large text files, help, licenses, documentation

**Lifecycle**: Created on-demand, destroyed after display

**Characteristics**:
- Always full-screen
- Monospaced font assumed (help files formatted accordingly)
- Paged display (using DEF_PAGER if available)
- Read-only text display

**Common Uses**:
- Help files (via `display_file()`)
- License
- Credits
- Long-form documentation
- Top 10 scores

**vs NHW_MENU as text**:
- NHW_TEXT is semantically for "documents" (always full-screen)
- NHW_MENU might fit in corner (if short enough)

**Example**:
```c
winid helpwin = create_nhwindow(NHW_TEXT);
putstr(helpwin, ATR_BOLD, "== NETHACK HELP ==");
putstr(helpwin, ATR_NONE, "");
putstr(helpwin, ATR_NONE, "Move with hjkl or arrow keys...");
// ... more lines ...
display_nhwindow(helpwin, TRUE);  // Blocking
destroy_nhwindow(helpwin);
```

## The Glyph System

**Glyphs** are NetHack's core abstraction for map display.

### What is a Glyph?

A **glyph** is an integer that uniquely represents everything that can appear on the map.

**The Problem**: NetHack has:
- ~400 monsters (some tame, some detected, some ridden, some invisible)
- ~400 objects (including corpses, statues)
- Dungeon features (walls, doors, floors, traps in various states)
- Effects (explosions in 7 types × 9 positions, zap beams in 8 types × 4 directions)
- Special states (being swallowed, warnings)

**The Solution**: Assign each distinct visual entity a unique integer (glyph).

### Glyph Ranges

Glyphs are allocated in ranges by category:

```c
// From display.h
GLYPH_MON_OFF     = 0                    // Normal monsters (×NUMMONS)
GLYPH_PET_OFF     = NUMMONS              // Tame monsters (×NUMMONS)
GLYPH_INVIS_OFF   = NUMMONS*2            // Invisible marker (×1)
GLYPH_DETECT_OFF  = NUMMONS*2 + 1        // Detected monsters (×NUMMONS)
GLYPH_BODY_OFF    = NUMMONS*3 + 1        // Corpses (×NUMMONS)
GLYPH_RIDDEN_OFF  = NUMMONS*4 + 1        // Ridden monsters (×NUMMONS)
GLYPH_OBJ_OFF     = NUMMONS*5 + 1        // Objects (×NUM_OBJECTS)
GLYPH_CMAP_OFF    = ...                  // Dungeon features (×MAXPCHARS)
GLYPH_EXPLODE_OFF = ...                  // Explosions (×MAXEXPCHARS × EXPL_MAX)
GLYPH_ZAP_OFF     = ...                  // Zap beams (×NUM_ZAP << 2)
GLYPH_SWALLOW_OFF = ...                  // Swallowed (×NUMMONS << 3)
GLYPH_WARNING_OFF = ...                  // Warnings (×WARNCOUNT)
GLYPH_STATUE_OFF  = ...                  // Statues (×NUMMONS)
MAX_GLYPH         = ...                  // Total count
NO_GLYPH          = MAX_GLYPH            // Sentinel value
```

**Example Glyphs** (assuming NUMMONS=400):
```
Glyph 0     = Normal goblin
Glyph 5     = Normal troll
Glyph 400   = Pet goblin
Glyph 401   = Pet troll
Glyph 800   = Invisible monster marker
Glyph 1201  = Goblin corpse
Glyph 2005  = Long sword object
Glyph 2050  = Floor tile (cmap)
Glyph 2055  = Vertical wall (cmap)
```

### Creating Glyphs (Core Game Does This)

Core game creates glyphs using helper macros (from display.h):

```c
// Monster glyphs
int glyph = mon_to_glyph(monster, rng);           // Normal
int glyph = pet_to_glyph(monster, rng);           // Tame
int glyph = detected_mon_to_glyph(monster, rng);  // Detected
int glyph = ridden_mon_to_glyph(monster, rng);    // Being ridden

// Object glyphs
int glyph = obj_to_glyph(object, rng);      // Handles corpses, statues
int glyph = objnum_to_glyph(LONG_SWORD);    // Specific object type

// Dungeon glyphs
int glyph = cmap_to_glyph(S_vwall);         // Vertical wall
int glyph = cmap_to_glyph(S_room);          // Room floor
int glyph = trap_to_glyph(trap, rng);       // Trap

// Hero
int glyph = hero_glyph;                     // Player character

// Effects
int glyph = explosion_to_glyph(EXPL_FIERY, idx);  // Fire explosion
```

**Port doesn't create glyphs** - core game does. Port just displays them.

### Translating Glyphs: mapglyph()

**CRITICAL**: Window ports don't use glyph numbers directly. Always use `mapglyph()` to translate:

```c
int mapglyph(int glyph, int *ochar, int *ocolor, 
             unsigned *ospecial, int x, int y, unsigned mgflags);
```

**Returns**: Index into showsyms[] symbol table

**Outputs**:
- `*ochar` - Character/symbol to display ('@', 'g', '#', '+', etc.)
- `*ocolor` - Color (CLR_RED, CLR_BLUE, etc. or NO_COLOR)
- `*ospecial` - Special flags (MG_PET, MG_CORPSE, MG_DETECT, etc.)

**Example**:
```c
void port_print_glyph(winid win, int x, int y, int glyph, int bkglyph) {
    int ch, color;
    unsigned special;
    
    // ALWAYS call mapglyph() - don't try to decode glyph yourself!
    mapglyph(glyph, &ch, &color, &special, x, y, 0);
    
    // Now we have:
    // ch = '@' for hero, 'g' for goblin, '#' for corridor, etc.
    // color = CLR_RED, CLR_BLUE, CLR_BROWN, etc.
    // special = MG_PET if tame, MG_CORPSE if corpse, etc.
    
    draw_character(x, y, ch, color);
    
    if (special & MG_PET && iflags.hilite_pet) {
        draw_pet_highlight(x, y);
    }
}
```

### Glyph Special Flags

The `special` output from `mapglyph()` contains bit flags:

```c
#define MG_CORPSE   0x01  // It's a corpse
#define MG_INVIS    0x02  // Invisible monster marker
#define MG_DETECT   0x04  // Detected monster (show dimly)
#define MG_PET      0x08  // Tame monster (highlight if iflags.hilite_pet)
#define MG_RIDDEN   0x10  // Monster being ridden
#define MG_STATUE   0x20  // Statue
#define MG_OBJPILE  0x40  // Multiple objects here
#define MG_BW_LAVA  0x80  // Lava (when no color available)
```

**Use These** for visual enhancements:
```c
if (special & MG_PET && iflags.hilite_pet) {
    // Highlight pets - inverse, underline, color, border, etc.
}

if (special & MG_DETECT) {
    // Detected monster - show dimly or with special indicator
}

if (special & MG_OBJPILE) {
    // Multiple objects - show indicator (',', '*', etc.)
}

if (special & MG_CORPSE) {
    // It's a corpse - maybe show differently
}
```

### Hallucination

When hallucinating, `mapglyph()` automatically randomizes what you see:
- Monsters appear as random monsters
- Objects appear as random objects
- Colors may shift

**Ports don't need to handle this** - it's built into glyph creation and mapglyph() logic.

### Background Glyphs

`print_glyph(winid, x, y, glyph, bkglyph)` includes optional `bkglyph`:

**For Tiled Graphics**:
- Draw `bkglyph` tile as background
- Draw `glyph` tile as foreground (may be transparent/overlay)
- Provides context for transparent tiles

**For ASCII**:
- Usually ignored
- Core passes NO_GLYPH for bkglyph

**Example (tile-based)**:
```c
if (bkglyph != NO_GLYPH) {
    int bg_ch, bg_color;
    unsigned bg_special;
    mapglyph(bkglyph, &bg_ch, &bg_color, &bg_special, x, y, 0);
    
    draw_background_tile(x, y, glyph_to_tile(bkglyph));
}

draw_foreground_tile(x, y, glyph_to_tile(glyph));
```

## Menu System

Menus follow a four-phase pattern: **create → start → add items → end → select**.

### Phase 1: Create & Start

```c
winid menu = create_nhwindow(NHW_MENU);
start_menu(menu);
```

**CRITICAL**: After `start_menu()`, window is committed to **menu mode**. Cannot use `putstr()` anymore.

### Phase 2: Populate with add_menu()

```c
void add_menu(winid menu, int glyph, const anything *identifier,
              char accelerator, char groupacc, int attr,
              const char *str, boolean preselected);
```

**identifier**: Value returned if selected
- If 0/NULL: Non-selectable title line
- Otherwise: Pointer to data (obj*, int, char*, etc.)
- Type is `anything` union (can hold various types)

**accelerator**: Keystroke to select
- 'a'-'z', 'A'-'Z': Use this specific key
- 0: Port assigns automatically (a, b, c, ...)
- Other: Special cases

**Don't Mix Strategies!** Either all items have caller-assigned accelerators, or all are port-assigned (accelerator=0).

**groupacc**: Group selection key
- Number or letter: Select all with this group (e.g., ')' for all weapons)
- 0: Not part of a group

**str**: Menu text - **PORT MUST COPY** (core's buffer is temporary!)

**preselected**: TRUE = pre-checked in PICK_ANY menus

**Example**:
```c
// Title (non-selectable)
anything any;
any.a_void = 0;
add_menu(menu, NO_GLYPH, &any, 0, 0, ATR_BOLD, "Your Inventory:", FALSE);

// Selectable items
any.a_obj = sword_obj;
add_menu(menu, sword_glyph, &any, 'a', WEAPON_CLASS, 
         ATR_NONE, "a - long sword", FALSE);

any.a_obj = shield_obj;
add_menu(menu, shield_glyph, &any, 'b', ARMOR_CLASS,
         ATR_NONE, "b - shield", FALSE);
```

### Phase 3: End Menu

```c
end_menu(menu, "What do you want to drop?");
```

Finalizes menu construction. Prompt shown to user (NULL = no prompt).

### Phase 4: Select

```c
menu_item *selected;
int n = select_menu(menu, how, &selected);
```

**How values**:
```c
PICK_NONE  // Display only, no selection (n always 0 or -1)
PICK_ONE   // Single item selection
PICK_ANY   // Multiple items with optional counts
```

**Return values**:
- **n > 0**: Selected n items, `selected` is allocated array
- **0**: Nothing selected (user accepted empty selection)
- **-1**: Cancelled (ESC)

**Output Structure**:
```c
typedef struct menu_item {
    anything item;  // The identifier from add_menu()
    long count;     // User-supplied count, or -1 for "all"
} menu_item;
```

**CRITICAL**: Caller **MUST** free array if n > 0:
```c
int n = select_menu(menu, PICK_ANY, &selected);
if (n > 0) {
    for (int i = 0; i < n; i++) {
        struct obj *obj = selected[i].item.a_obj;
        long count = selected[i].count;
        if (count == -1)
            count = obj->quan;  // "all"
        
        drop_object(obj, count);
    }
    free(selected);  // MUST FREE!
}

destroy_nhwindow(menu);
```

### Menu Persistence

Menus persist until explicitly destroyed:
```c
// First selection
int n1 = select_menu(menu, PICK_ONE, &selected);
if (n1 > 0) {
    // Use selected[0]
    free(selected);
}

// Menu still exists - can select again!
int n2 = select_menu(menu, PICK_ANY, &selected);
if (n2 > 0) {
    // Use selected[0..n2-1]
    free(selected);
}

destroy_nhwindow(menu);  // Clean up when done
```

### Menu Commands

Ports should support these standard menu commands:

```c
'^'  - MENU_FIRST_PAGE      // Jump to first page
'|'  - MENU_LAST_PAGE       // Jump to last page
'>'  - MENU_NEXT_PAGE       // Next page
'<'  - MENU_PREVIOUS_PAGE   // Previous page
'.'  - MENU_SELECT_ALL      // Select all items
'-'  - MENU_UNSELECT_ALL    // Deselect all
'@'  - MENU_INVERT_ALL      // Toggle all selections
','  - MENU_SELECT_PAGE     // Select all on current page
'\\' - MENU_UNSELECT_PAGE   // Deselect all on current page
'~'  - MENU_INVERT_PAGE     // Toggle page selections
':'  - MENU_SEARCH          // Search for item
```

**Also Support**:
- Scrolling (pgup/pgdown, j/k, arrow keys)
- Typing counts before accelerators (5a = select 'a' with count 5)
- ESC to cancel
- SPACE/RETURN to accept

**Group Accelerators vs Menu Commands**:
- Group accelerators can conflict with menu commands
- Menu commands take precedence
- Example: '^' as group key conflicts with MENU_FIRST_PAGE

## Status Display (Modern Field-Based)

Modern status uses **field-based** updates, not string formatting.

### Philosophy

**Port controls rendering.** Core game provides raw field values.

### Three-Phase Pattern: init → enable → update cycle

#### Phase 1: Initialize

```c
void port_status_init(void) {
    // Allocate your status display data structures
    // Create status window/area
    // Set up fonts, colors, layout
}
```

Called once at startup.

#### Phase 2: Enable Fields

```c
void port_status_enablefield(int fieldidx, const char *name, 
                             const char *fmt, boolean enable) {
    // fieldidx = BL_HP, BL_STR, BL_GOLD, etc.
    // name = "HP", "Str", "Gold"
    // fmt = "HP:%d(%d)", "Str:%d", etc. (informational)
    // enable = TRUE or FALSE
    
    if (enable) {
        enabled_fields[fieldidx] = TRUE;
        // Set up display area for this field
    } else {
        enabled_fields[fieldidx] = FALSE;
        // Hide/disable this field
    }
}
```

Called at startup and when fields become relevant.

#### Phase 3: Update Fields

```c
void port_status_update(int fieldidx, genericptr_t ptr, int chg,
                        int percent, int color, unsigned long *colormasks) {
    switch (fieldidx) {
    case BL_FLUSH:
        // Special: render all buffered changes NOW
        render_status();
        return;
        
    case BL_RESET:
        // Special: redraw everything (even unchanged)
        force_full_redraw = TRUE;
        return;
        
    case BL_HP:
        // ptr is "char *" with value like "12(15)"
        int current, max;
        sscanf((char *)ptr, "%d(%d)", &current, &max);
        
        // Store for rendering on BL_FLUSH
        status_hp = current;
        status_hpmax = max;
        status_hp_color = color & 0xFF;
        status_hp_attr = (color >> 8) & 0xFF;
        break;
        
    case BL_CONDITION:
        // SPECIAL: ptr is a LONG bitmask, NOT a string!
        unsigned long conditions = (unsigned long)ptr;
        
        // colormasks[CLR_RED] = bitmask of conditions to show in red
        // colormasks[HL_ATTCLR_BOLD] = bitmask of conditions to bold
        
        if (conditions & BL_MASK_STONE) {
            int color = find_condition_color(BL_MASK_STONE, colormasks);
            store_condition("Stone", color);
        }
        if (conditions & BL_MASK_STUN) {
            int color = find_condition_color(BL_MASK_STUN, colormasks);
            store_condition("Stun", color);
        }
        // ... check all condition bits
        break;
        
    case BL_GOLD:
        // SPECIAL: ptr is "char *" like "$:123"
        // Skip first 2 chars to get number
        const char *gold_str = (char *)ptr + 2;
        status_gold = atol(gold_str);
        break;
        
    default:
        // Most fields: ptr is "char *" with formatted value
        strncpy(status_values[fieldidx], (char *)ptr, BUFSZ);
        status_colors[fieldidx] = color;
        break;
    }
}
```

### Status Fields (23 total)

```c
enum statusfields {
    BL_TITLE = 0,    // Role/rank (e.g., "Valkyrie")
    BL_STR,          // Strength (1-25 or 18/** notation)
    BL_DX,           // Dexterity
    BL_CO,           // Constitution
    BL_IN,           // Intelligence
    BL_WI,           // Wisdom
    BL_CH,           // Charisma
    BL_ALIGN,        // Alignment (Lawful/Neutral/Chaotic)
    BL_SCORE,        // Score
    BL_CAP,          // Encumbrance (Burdened/Stressed/etc.)
    BL_GOLD,         // Gold count (format: "$:123")
    BL_ENE,          // Current power
    BL_ENEMAX,       // Maximum power
    BL_XP,           // Experience level
    BL_AC,           // Armor class
    BL_HD,           // Hit dice (when polymorphed)
    BL_TIME,         // Turn count
    BL_HUNGER,       // Hunger state
    BL_HP,           // Current hit points
    BL_HPMAX,        // Maximum hit points
    BL_LEVELDESC,    // Dungeon level description
    BL_EXP,          // Experience points
    BL_CONDITION,    // Status conditions (SPECIAL - bitmask!)
    MAXBLSTATS       // Count (23)
};
```

### Special Field Indices

- **BL_FLUSH (-1)**: End of update cycle - **render all changes now**
- **BL_RESET (-2)**: Redraw everything (window resized, mode changed)

### The Update Cycle

```
status_update(BL_HP, "12(15)", chg, 80, CLR_GREEN, NULL);
status_update(BL_ENE, "5(5)", chg, 100, CLR_BLUE, NULL);
status_update(BL_GOLD, "$:123", chg, 0, CLR_YELLOW, NULL);
status_update(BL_CONDITION, (genericptr_t)conditions, 0, 0, 0, colormasks);
status_update(BL_FLUSH, NULL, 0, 0, 0, NULL);  // ← RENDER NOW!
```

**Port Should**: Buffer updates, render only on BL_FLUSH (for efficiency).

### BL_CONDITION - Detailed Handling

**CRITICAL**: BL_CONDITION is the only field where `ptr` is NOT a string!

```c
case BL_CONDITION:
    unsigned long cond = (unsigned long)ptr;  // BITMASK, not string!
    
    // Check each condition bit
    if (cond & BL_MASK_STONE)    { /* petrifying - DEADLY! */ }
    if (cond & BL_MASK_SLIME)    { /* slimed - DEADLY! */ }
    if (cond & BL_MASK_STRNGL)   { /* strangling - DEADLY! */ }
    if (cond & BL_MASK_FOODPOIS) { /* food poisoning - DEADLY! */ }
    if (cond & BL_MASK_TERMILL)  { /* terminal illness - DEADLY! */ }
    if (cond & BL_MASK_BLIND)    { /* blind */ }
    if (cond & BL_MASK_DEAF)     { /* deaf */ }
    if (cond & BL_MASK_STUN)     { /* stunned */ }
    if (cond & BL_MASK_CONF)     { /* confused */ }
    if (cond & BL_MASK_HALLU)    { /* hallucinating */ }
    if (cond & BL_MASK_LEV)      { /* levitating */ }
    if (cond & BL_MASK_FLY)      { /* flying */ }
    if (cond & BL_MASK_RIDE)     { /* riding */ }
    
    // Use colormasks to find colors
    // colormasks is array where:
    // colormasks[0..15] = colors (CLR_BLACK..CLR_WHITE)
    // colormasks[16..20] = attributes (HL_ATTCLR_DIM, BLINK, ULINE, INVERSE, BOLD)
    
    // Example: Find color for Stone condition
    for (int i = 0; i < BL_ATTCLR_MAX; i++) {
        if (colormasks[i] & BL_MASK_STONE) {
            if (i < CLR_MAX) {
                // It's a color
                condition_color = i;
            } else if (i == HL_ATTCLR_BOLD) {
                // It's bold
                condition_attr |= ATR_BOLD;
            }
            // ... etc for other attributes
        }
    }
    break;
```

**Condition Bits**:
```c
BL_MASK_STONE       0x00000001L  // Petrifying
BL_MASK_SLIME       0x00000002L  // Slimed
BL_MASK_STRNGL      0x00000004L  // Strangling
BL_MASK_FOODPOIS    0x00000008L  // Food poisoning
BL_MASK_TERMILL     0x00000010L  // Terminal illness
BL_MASK_BLIND       0x00000020L  // Blind
BL_MASK_DEAF        0x00000040L  // Deaf
BL_MASK_STUN        0x00000080L  // Stunned
BL_MASK_CONF        0x00000100L  // Confused
BL_MASK_HALLU       0x00000200L  // Hallucinating
BL_MASK_LEV         0x00000400L  // Levitating
BL_MASK_FLY         0x00000800L  // Flying
BL_MASK_RIDE        0x00001000L  // Riding
```

## Text Attributes

```c
ATR_NONE    0  // Normal text
ATR_BOLD    1  // Bold/bright
ATR_DIM     2  // Dim/faint
ATR_ULINE   4  // Underlined
ATR_BLINK   5  // Blinking
ATR_INVERSE 7  // Reverse video
```

**Used In**:
- `putstr(win, attr, str)`
- `putmixed(win, attr, str)`
- `add_menu(..., attr, str, ...)`

**Port Behavior**:
- If port doesn't support an attribute, map to another or ignore
- Example: Map all to INVERSE if that's all you have

### Special Attributes (MESSAGE only)

```c
ATR_URGENT    16  // 0x10 - Urgent message (requires WC2_URGENT_MESG)
ATR_NOHISTORY 32  // 0x20 - Don't save to history (requires WC2_SUPPRESS_HIST)
```

**Not Display Attributes** - they're flags:
- Can combine with one regular attribute: `ATR_BOLD | ATR_URGENT`
- `ATR_URGENT`: Message needs attention (flash, sound, color)
- `ATR_NOHISTORY`: Ephemeral message, don't save to history

**Example**:
```c
putstr(WIN_MESSAGE, ATR_BOLD | ATR_URGENT, "The orc hits you!");
```

## Colors

NetHack defines 16 colors:

```c
CLR_BLACK          0
CLR_RED            1
CLR_GREEN          2
CLR_BROWN          3
CLR_BLUE           4
CLR_MAGENTA        5
CLR_CYAN           6
CLR_GRAY           7
NO_COLOR           8  // Sentinel (not a real color)
CLR_ORANGE         9
CLR_BRIGHT_GREEN  10
CLR_YELLOW        11
CLR_BRIGHT_BLUE   12
CLR_BRIGHT_MAGENTA 13
CLR_BRIGHT_CYAN   14
CLR_WHITE         15
CLR_MAX           16
```

**Port Advertises Support**:
```c
// In window_procs structure:
boolean has_color[CLR_MAX];  // 1 = supported, 0 = not

// Example:
port_procs.has_color[CLR_RED] = 1;
port_procs.has_color[CLR_BLUE] = 1;
port_procs.has_color[CLR_ORANGE] = 0;  // Can't do orange
```

**Usage**:
- `mapglyph()` returns color for glyphs
- `status_update()` receives color for fields
- Ports can ignore colors if `!(wincap & WC_COLOR)`

## Summary

**Five Window Types**:
1. MESSAGE - Scrolling game messages (persistent)
2. STATUS - (Legacy) Use status_* functions instead
3. MAP - Main dungeon display (persistent)
4. MENU - Polymorphic: menu or text (temporary)
5. TEXT - Full-screen documents (temporary)

**Glyphs**:
- Integer IDs for everything on the map (~2000+ distinct entities)
- **Always use mapglyph()** to translate to symbol/color/special
- Special flags (MG_PET, MG_CORPSE, etc.) for visual enhancements

**Menus**:
- Pattern: create → start → add × N → end → select
- Three modes: PICK_NONE, PICK_ONE, PICK_ANY
- **Caller must free** returned array if n > 0
- Don't mix accelerator assignment strategies

**Status**:
- Field-based, not string-based (modern approach)
- Pattern: init → enable → update cycle → BL_FLUSH to render
- **BL_CONDITION is a bitmask**, not a string
- Use colormasks for condition highlighting

**Attributes & Colors**:
- 6 text attributes (bold, dim, inverse, etc.)
- 16 colors (port advertises support in has_color[])
- Special flags: ATR_URGENT, ATR_NOHISTORY (MESSAGE only)

Understanding these concepts is essential for implementing a NetHack port correctly.
