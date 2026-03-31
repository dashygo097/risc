void print_str(const char *str) {
  volatile char *uart_tx = (volatile char *)0x10000004;
  while (*str) {
    *uart_tx = *str++;
  }
}

int main() {
  print_str("Hello World!\n");
  return 0;
}
