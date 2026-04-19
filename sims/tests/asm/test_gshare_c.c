int main() {
    int i;
    int limit = 200;       // x11
    int event_counter = 0; // x12

    // 对应汇编中的 loop_start
    for (i = 0; i < limit; i++) {
        // 对应 user logic: andi x13, x10, 1
        if ((i & 1) == 0) {
            // 偶数 (Target: do_jump)
            // 对应: addi x12, x12, 2
            event_counter += 2;
        } else {
            // 奇数 (Fall through)
            // 对应: addi x12, x12, 1
            event_counter += 1;
        }
    }

    // 对应 loop_end 后的结束逻辑
    // 为了让测试表现一致，我们手动设置 x20 并触发 unknown instruction
    __asm__ volatile (
        "li x20, 0x12345678\n\t" // Marker for success
        "ebreak"                 // Stop simulation (触发断点异常)
    );

    return 0;
}
