#define UART_TXD_REG (*(volatile unsigned char *)0x10000004)
#define CLINT_MTIMECMP_LO (*(volatile unsigned int *)0x20004000)
#define CLINT_MTIMECMP_HI (*(volatile unsigned int *)0x20004004)
#define CLINT_MTIME_LO (*(volatile unsigned int *)0x2000BFF8)

#define TICK_CYCLES 20000

void print_str(const char *str) {
  while (*str) {
    UART_TXD_REG = *str++;
  }
}

volatile int timer_hits = 0;

void timer_handler_c(void) {
  unsigned int cause;
  asm volatile("csrr %0, mcause" : "=r"(cause));

  if (cause == 0x80000007) { // Machine Timer Interrupt
    UART_TXD_REG = '*';

    timer_hits++;

    CLINT_MTIMECMP_LO = CLINT_MTIME_LO + TICK_CYCLES;
    volatile unsigned int dummy = CLINT_MTIMECMP_LO;
    (void)dummy;
  } else {
    print_str("\n[FATAL] Unknown Trap!\n");
    while (1)
      ;
  }
}

extern void trap_entry(void);

int main(void) {
  print_str(" Bare-Metal System Integration Test\n\n");

  print_str("[MAIN] Setting up Trap Vector...\n");
  unsigned int handler_addr = (unsigned int)trap_entry & ~0x3;
  asm volatile("csrw mtvec, %0" ::"r"(handler_addr));

  print_str("[MAIN] Enabling Interrupts...\n");
  asm volatile("li t0, 128 \n csrs mie, t0" : : : "t0");
  asm volatile("csrsi mstatus, 8");

  print_str("[MAIN] Waiting for IRQs...\n\n");

  int last_hits = 0;

  CLINT_MTIMECMP_LO = 0xFFFFFFFF;
  CLINT_MTIMECMP_HI = 0xFFFFFFFF;

  while (1) {
    if (timer_hits != last_hits) {
      print_str("\n[MAIN] Acknowledged Timer Hit!\n");
      last_hits = timer_hits;
      if (timer_hits >= 5) {
        print_str("\n*** SYSTEM TEST PASSED !!! ***\n");
        while (1)
          ;
      }
    }

    CLINT_MTIMECMP_LO = CLINT_MTIME_LO + 100;
    CLINT_MTIMECMP_HI = 0;
  }

  return 0;
}
