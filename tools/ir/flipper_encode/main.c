/*
 * Reads "<protocol> <address hex> <command hex>" lines on stdin and prints, per line,
 * "<carrier Hz> <mark> <space> <mark> ..." (µs, starting with a mark, all repeats Flipper would send),
 * or "ERR <reason>". Built against Flipper firmware's lib/infrared/encoder_decoder by build_ir_assets.py.
 */
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "infrared.h"

int main(void) {
    char line[256], proto[64];
    unsigned long address, command;
    static uint32_t out[8192];
    while(fgets(line, sizeof line, stdin)) {
        if(sscanf(line, "%63s %lx %lx", proto, &address, &command) != 3) { puts("ERR parse"); continue; }
        InfraredProtocol p = infrared_get_protocol_by_name(proto);
        if(p == InfraredProtocolUnknown) { puts("ERR protocol"); continue; }
        InfraredMessage m = {.protocol = p, .address = (uint32_t)address, .command = (uint32_t)command, .repeat = false};
        InfraredEncoderHandler* h = infrared_alloc_encoder();
        infrared_reset_encoder(h, &m);
        int times = (int)infrared_get_protocol_min_repeat_count(p);
        if(times < 1) times = 1;
        size_t n = 0; int done = 0; int ok = 1;
        while(done < times) {
            uint32_t d; bool level;
            InfraredStatus s = infrared_encode(h, &d, &level);
            if(s == InfraredStatusError) { ok = 0; break; }
            if(d > 0) {
                if(n == 0 && !level) { /* leading space: skip */ }
                else if(n > 0 && ((n % 2 == 1) == level)) {
                    /* same level as the previous entry (odd count = last is a mark): merge */
                    out[n - 1] += d;
                } else if(n < sizeof out / sizeof out[0]) {
                    out[n++] = d;
                } else { ok = 0; break; }
            }
            if(s == InfraredStatusDone) done++;
        }
        infrared_free_encoder(h);
        if(!ok || n == 0) { puts("ERR encode"); continue; }
        /* Round trip: Flipper's own decoder must read the same protocol, address and command back. */
        InfraredDecoderHandler* dec = infrared_alloc_decoder();
        int matched = 0;
        for(size_t i = 0; i <= n && !matched; i++) {
            bool level = (i % 2 == 0);
            uint32_t d = i < n ? out[i] : 150000; /* a long final space flushes the decoder */
            const InfraredMessage* r = infrared_decode(dec, i < n ? level : false, d);
            if(!r && i == n) r = infrared_check_decoder_ready(dec);
            if(r && r->protocol == p && r->address == m.address && r->command == m.command) matched = 1;
        }
        infrared_free_decoder(dec);
        if(!matched) { puts("ERR roundtrip"); continue; }
        if(n % 2 == 0) n--; /* drop the trailing space */
        printf("%lu", (unsigned long)infrared_get_protocol_frequency(p));
        for(size_t i = 0; i < n; i++) printf(" %lu", (unsigned long)out[i]);
        putchar('\n');
    }
    return 0;
}
