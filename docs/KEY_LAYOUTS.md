# Number and symbol layouts

Character placement is implemented from the user-supplied screenshots. Keyboard colors, regular icons, Shift states, alphabet layouts and compact strip styling are retained.

| Page | Top row | Middle row | Bottom character row |
|---|---|---|---|
| Numbers / punctuation | 1 2 3 4 5 6 7 8 9 0 | @ # $ _ & - + ( ) / | page toggle, * \" ' : ; ! ?, Delete |
| Secondary symbols | ~ ` \| • √ π ÷ × § Δ | £ ¢ € ¥ ^ ° = { } \\ | page toggle, % © ® ™ ✓ [ ], Delete |

The third-row toggle switches these two pages directly. The footer provides the existing next-layout key, comma/period (or < and > on the secondary page), a stacked 12/34 number-pad shortcut, Space and the editor action. The next-layout key retains the earlier Mini → ABC → 123 → #+= → Mini behavior, rather than duplicating the screenshot's ABC-only return.

## Calculator-style pad

Digits are arranged 123 / 456 / 789 with 0 directly below 5. A three-row-height operator viewport on the left scrolls +, -, *, /, (, ). Four operators fit initially; scrolling reveals the parentheses. The separate phone variant has plus/pause/wait at left, parentheses and Delete at right, and a * / 0 / # footer. The right column has %, Space and Delete (calculator layout). The footer has the layout key, comma, !?# to return to the punctuation page, 0, equals, decimal point and the editor action. The calculator pad's Space retains horizontal cursor gestures. Number/phone fields use an ellipsis tools button; its tap opens the tools menu.

Number and phone editors open this pad automatically. Their Android input filters still decide which characters a field accepts. In ordinary text the pad is selected through the 12/34 shortcut and returns to ABC through the layout key. Microphone availability follows the editor field policy; ordinary message fields can still use voice while a numeric page is selected.
