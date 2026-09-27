/*
 * Build-time check of IRext AC binaries, compiled from the vendored decoder (app/src/main/cpp/irext).
 * For each file path on stdin prints "OK <file> <on pattern length> <on pattern hash> <mode mask> <tmin> <tmax>"
 * or "ERR <file> <reason>". "on pattern" = power on, cool, 24 °C, fan auto, swing off.
 */
#include <stdio.h>
#include <string.h>
#include "ir_decode.h"

static UINT16 buf[USER_DATA_SIZE];

int main(void) {
    char path[1024];
    while(fgets(path, sizeof path, stdin)) {
        path[strcspn(path, "\r\n")] = 0;
        if(ir_file_open(REMOTE_CATEGORY_AC, 1, path) != IR_DECODE_SUCCEEDED) { printf("ERR %s open\n", path); continue; }
        UINT8 modes = 0; INT8 tmin = -1, tmax = -1;
        get_supported_mode(&modes);
        get_temperature_range(AC_MODE_COOL, &tmin, &tmax);
        t_remote_ac_status s; memset(&s, 0, sizeof s);
        s.ac_power = AC_POWER_ON; s.ac_mode = AC_MODE_COOL; s.ac_temp = AC_TEMP_24;
        s.ac_wind_speed = AC_WS_AUTO; s.ac_wind_dir = AC_SWING_OFF;
        memset(buf, 0, sizeof buf);
        UINT16 n = ir_decode(KEY_AC_POWER, buf, &s);
        unsigned long long h = 1469598103934665603ULL; unsigned long total = 0;
        for(int i = 0; i < n; i++) { h = (h ^ buf[i]) * 1099511628211ULL; total += buf[i]; }
        s.ac_power = AC_POWER_OFF;
        UINT16 off = ir_decode(KEY_AC_POWER, buf, &s);
        ir_close();
        if(n < 8 || off < 8) { printf("ERR %s decode %u %u\n", path, n, off); continue; }
        if(total > 1900000UL) { printf("ERR %s too_long %lu\n", path, total); continue; }
        printf("OK %s %u %016llx %u %d %d\n", path, n, h, modes, tmin, tmax);
    }
    return 0;
}
