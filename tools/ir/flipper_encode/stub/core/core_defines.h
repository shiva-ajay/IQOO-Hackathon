#pragma once
#ifndef MAX
#define MAX(a, b) ((a) > (b) ? (a) : (b))
#endif
#ifndef MIN
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#endif
#ifndef COUNT_OF
#define COUNT_OF(x) (sizeof(x) / sizeof(x[0]))
#endif
#ifndef UNUSED
#define UNUSED(x) (void)(x)
#endif
#ifndef FURI_BIT
#define FURI_BIT(x, n) (((x) >> (n)) & 1)
#endif
