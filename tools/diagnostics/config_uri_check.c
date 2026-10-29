#include <ctype.h>
#include <stdio.h>
#include <string.h>

/*
 * ZX VPN offline config URI sanity checker.
 * Reads one URI per line from stdin. It never prints input values because
 * config links can contain credentials or proxy secrets.
 */
static int starts_with_ci(const char *s, const char *prefix) {
    while (*prefix) {
        if (tolower((unsigned char)*s++) != tolower((unsigned char)*prefix++)) return 0;
    }
    return 1;
}

static int supported_scheme(const char *s) {
    static const char *schemes[] = {
        "vless://", "vmess://", "trojan://", "ss://", "socks://",
        "socks5://", "http://", "https://", "wireguard://", "wg://",
        "hysteria2://", "hy2://", "tuic://", "ssh://"
    };
    size_t count = sizeof(schemes) / sizeof(schemes[0]);
    for (size_t i = 0; i < count; ++i) {
        if (starts_with_ci(s, schemes[i])) return 1;
    }
    return 0;
}

static int valid_line(const char *line) {
    size_t len = strlen(line);
    while (len && (line[len - 1] == '\n' || line[len - 1] == '\r' ||
                   isspace((unsigned char)line[len - 1]))) {
        --len;
    }
    size_t start = 0;
    while (start < len && isspace((unsigned char)line[start])) ++start;
    if (start == len) return 0;

    char value[8192];
    size_t n = len - start;
    if (n >= sizeof(value)) return 0;
    memcpy(value, line + start, n);
    value[n] = '\0';

    for (size_t i = 0; i < n; ++i) {
        unsigned char ch = (unsigned char)value[i];
        if (ch < 0x20 || ch == 0x7f) return 0;
    }
    if (!supported_scheme(value)) return 0;

    const char *separator = strstr(value, "://");
    if (!separator || separator[3] == '\0') return 0;
    if (strchr(separator + 3, ' ') || strchr(separator + 3, '\t')) return 0;
    return 1;
}

int main(void) {
    char line[8192];
    unsigned long line_number = 0;
    unsigned long valid = 0, invalid = 0;

    while (fgets(line, sizeof(line), stdin)) {
        ++line_number;
        if (valid_line(line)) {
            ++valid;
            printf("line %lu: supported URI shape\n", line_number);
        } else {
            size_t len = strlen(line);
            if (len && line[len - 1] != '\n' && !feof(stdin)) {
                int ch;
                while ((ch = getchar()) != '\n' && ch != EOF) {}
            }
            ++invalid;
            printf("line %lu: blank, unsupported, or malformed URI shape\n", line_number);
        }
    }
    printf("summary: %lu candidate(s), %lu rejected line(s)\n", valid, invalid);
    return invalid ? 1 : 0;
}
