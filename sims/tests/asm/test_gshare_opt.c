int main() {
    int i;
    int limit = 200;
    int event_counter = 0;

    // Hide limit from optimizer so it doesn't constant-fold the loop
    __asm__ volatile ("" : "+r" (limit));

    for (i = 0; i < limit; i++) {
        if ((i & 1) == 0) {
            event_counter += 2;
        } else {
            event_counter += 1;
        }
    }

    // Force usage of result
    __asm__ volatile ("" :: "r"(event_counter));

    __asm__ volatile (
        "li x20, 0x12345678\n\t"
        "ebreak"
    );
    return 0;
}
