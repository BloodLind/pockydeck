# Physical controller capability observation

Read-only preparation for F05, 8 September 2026. The connected Android 13 Retroid Pocket Flip 2 reports an input device with sources `KEYBOARD | GAMEPAD | JOYSTICK`, controller number 1. Android's active key layout is `/system/usr/keylayout/Vendor_2022_Product_3001.kl`.

The layout maps D-pad directions; `BUTTON_A`, `BUTTON_B`, `BUTTON_X`, `BUTTON_Y`; `BUTTON_L1`, `BUTTON_R1`, `BUTTON_L2`, `BUTTON_R2`; thumb clicks; Select, Start and Mode. Native Home, Back, app switch and volume keys remain system controls. Motion ranges expose X/Y, Z/RZ, HAT_X/HAT_Y in -1..1 and GAS/BRAKE in 0..1; all reported flat values are zero, so the app must supply its agreed stick dead zone. Keyboard mapper reports `HandlesKeyRepeat: false`.

This is capability/key-layout evidence, not evidence of events from physical button presses. F05 must still test actual event shapes, duplicate reports, held-direction repeat, native IME behavior and controller focus. No device key layout or controller setting was changed.
