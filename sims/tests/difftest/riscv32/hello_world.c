// RUN: %bare_c
// RUN: %difftest -c 1000

void print_str(const char *s) {
  char volatile *uart_txd = (char volatile *)(0x10000004);
  while (s) {
    *uart_txd = *s;
    ++s;
  }
}

int main() {
  print_str("hello world");
  return 0;
}
