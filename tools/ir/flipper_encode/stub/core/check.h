/* Minimal stand-ins for the Flipper runtime, so its IR encoders build on a laptop. */
#pragma once
#include <stdio.h>
#include <stdlib.h>
#define furi_assert(x) do { if(!(x)) { fprintf(stderr, "assert: %s\n", #x); abort(); } } while(0)
#define furi_check(x) furi_assert(x)
#define furi_crash(...) abort()
