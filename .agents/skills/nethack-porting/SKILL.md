---
name: nethack-porting
description: Knowledge base for NetHack window port implementation. Provides API reference, UI concepts, game loop integration, implementation patterns, and best practices. Use when working on NetHack porting layer code (window_procs functions, display/input/status/menus) or answering questions about how the porting layer works.
---

# NetHack Porting Layer

This skill provides reference knowledge about NetHack's porting layer - the abstraction that allows NetHack to run on different platforms with different UI systems.

## What is the Porting Layer?

NetHack's porting layer is built around a **function pointer table** called `window_procs` (defined in `include/winprocs.h`). Each port (TTY, curses, X11, NDS, etc.) implements ~40 functions that handle:

- **Window management**: Creating, displaying, destroying windows
- **Display output**: Text (messages), graphics (map glyphs), status updates
- **User input**: Keyboard, mouse/touch, prompts, menus
- **Synchronization**: Event processing, display refresh timing

The core game calls through this abstraction, allowing the same game code to work with different UIs.

## Architecture Overview

```
Core Game Code (src/*.c)
    ↓ calls through macros
window_procs function pointer table
    ↓ implemented by each port
Port-Specific Code (win/tty/, win/curses/, sys/nds/, etc.)
    ↓
Platform Display/Input (terminal, X11, SDL, hardware, etc.)
```

**Key insight**: The porting layer is an **information boundary**. Game code knows nothing about terminals, pixels, or input devices. Port code knows nothing about game mechanics.

## When to Use This Skill

### If you're implementing a window_procs function
→ See [API Reference](references/api-reference.md)
- Look up function signature, purpose, when it's called, requirements
- Check required vs optional implementations
- Understand capability flags

### If you're trying to understand a UI concept
→ See [UI Concepts](references/ui-concepts.md)
- Window types (MESSAGE, STATUS, MAP, MENU, TEXT)
- Glyph system and mapglyph() translation
- Menu lifecycle and selection patterns
- Modern field-based status system

### If you're debugging timing or call sequence issues
→ See [Game Loop Integration](references/game-loop.md)
- When functions are called during gameplay
- Startup/shutdown sequence
- Main game loop structure
- Critical distinction: get_nh_event() vs nhgetch()
- Display update patterns

### If you're looking for implementation guidance
→ See [Implementation Patterns](references/implementation-patterns.md)
- How different ports solve the same problems
- TTY vs Curses vs NDS approaches
- Helper functions to use vs customize
- Platform-specific strategies

### If you're avoiding common mistakes
→ See [Gotchas & Best Practices](references/gotchas.md)
- Top 10 critical rules (blocking, memory management, status updates)
- Platform-specific pitfalls
- Testing strategies
- Debugging tips

### If you need quick lookups
→ See [Quick Reference](references/quick-reference.md)
- Function call frequency table
- Required vs optional functions
- Status field types
- Window lifecycle
- Port comparison matrix

## Common Questions

**Q: Where do I start implementing a new port?**
A: See [Implementation Patterns](references/implementation-patterns.md) for minimal implementation strategy. Start with init_nhwindows, create/destroy windows, putstr, print_glyph, nhgetch, and basic status.

**Q: Why isn't my status display working?**
A: See [Gotchas - Status Updates](references/gotchas.md#status-updates). Make sure you're using field-based updates, handling BL_CONDITION as a bitmask (not string), and responding to BL_FLUSH.

**Q: Why does my game freeze?**
A: See [Gotchas - Blocking](references/gotchas.md#blocking-vs-non-blocking). You're probably blocking in get_nh_event() (which is called every loop iteration). Input blocking should happen in nhgetch() only.

**Q: What's a glyph and how do I display it?**
A: See [UI Concepts - Glyphs](references/ui-concepts.md#the-glyph-system). Glyphs are integer IDs for map entities. Always use mapglyph() to translate them to symbol/color.

**Q: How do menus work?**
A: See [UI Concepts - Menus](references/ui-concepts.md#menu-system) and [API Reference - Menus](references/api-reference.md#menus). Pattern: create → start → add items → end → select. Caller must free the returned array.

**Q: Which functions must I implement vs can stub?**
A: See [Quick Reference - Required vs Optional](references/quick-reference.md#required-vs-optional-functions).

## Key Files in NetHack Source

Reference these when implementing:

- `include/winprocs.h` - window_procs structure definition
- `include/wintype.h` - Window types, constants, data structures
- `include/botl.h` - Status field definitions (BL_* constants)
- `include/display.h` - Glyph system definitions
- `src/allmain.c` - Main game loop (moveloop)
- `win/tty/wintty.c` - TTY port (simple reference implementation)
- `win/curses/curses.c` - Curses port (rich features)
- `sys/nds/arm9/src/nds_win.c` - NDS port (embedded example)

## Progressive Learning Path

1. **Start**: Read [UI Concepts](references/ui-concepts.md) to understand the model
2. **Then**: Skim [API Reference](references/api-reference.md) to see available functions
3. **Next**: Read [Game Loop Integration](references/game-loop.md) to understand timing
4. **Before coding**: Read [Gotchas - Critical Rules](references/gotchas.md#critical-rules)
5. **While implementing**: Consult specific function docs in [API Reference](references/api-reference.md)
6. **For examples**: Check [Implementation Patterns](references/implementation-patterns.md)

## Critical Rules (Read These First!)

1. **get_nh_event() NEVER blocks** - Called every loop iteration, return immediately
2. **nhgetch() MUST block** - Wait for user input here
3. **Use field-based status** - Don't parse strings like NDS does
4. **BL_CONDITION is a bitmask** - Not a string
5. **Free menu_item arrays** - After select_menu() returns n > 0
6. **Always use mapglyph()** - Don't decode glyphs yourself
7. **Never return 0 from nhgetch()** - Map to ESC (033)
8. **getlin() must call flush_screen(1)** - Before prompting
9. **Copy strings in add_menu()** - Core's buffer is temporary
10. **Advertise only what you implement** - Don't claim capabilities you lack

See [Gotchas](references/gotchas.md) for details on each.

## How This Skill Works

This skill provides **reference knowledge**, not step-by-step procedures. When you need information:

1. Identify what you're working on (function, concept, problem)
2. Check "When to Use This Skill" above to find the right reference
3. Read only the relevant section of that reference file
4. Return here if you need a different perspective

Reference files are loaded **on-demand** - you don't need to read everything at once.

## Note on Implementation Examples

The reference materials draw from real NetHack ports:
- **TTY port** (win/tty/) - Simple, complete, good starting point
- **Curses port** (win/curses/) - Rich features, good for advanced UI

## Questions to Ask Yourself

Before diving into implementation, consider:

- **What platform am I targeting?** (Terminal, GUI toolkit, embedded hardware, web)
- **What input methods?** (Keyboard only, mouse, touch, gamepad)
- **What display capabilities?** (ASCII, tiles, colors, fonts)
- **What constraints?** (Screen size, memory, CPU, refresh rate)
- **What features?** (Minimal viable, or rich UI with menus/dialogs/persistence)

Your answers guide which patterns to follow and which references to prioritize.

## Getting Help

If you're stuck:
1. Check [Gotchas](references/gotchas.md) - your issue is probably listed
2. Compare with [Implementation Patterns](references/implementation-patterns.md) - see how other ports solved it
3. Review [Game Loop Integration](references/game-loop.md) - understand when your function is called
4. Verify against [API Reference](references/api-reference.md) - confirm you're meeting requirements

## About This Knowledge Base

This knowledge base distills analysis of NetHack's porting layer into structured, efficient reference material. It covers:
- Complete window_procs API (~40 functions)
- Core UI concepts (windows, glyphs, menus, status)
- Game loop timing and call sequences
- Real-world implementation patterns
- Common pitfalls and solutions

The material is **informational** (understanding the architecture) rather than **procedural** (step-by-step recipes). Use it to build mental models and make informed decisions.
