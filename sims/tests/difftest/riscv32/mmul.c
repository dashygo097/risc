// RUN: %bare_c
// RUN: %difftest -c 500000 -SLT

constexpr int M = 16;
constexpr int N = 16;
constexpr int K = 16;

void mmul(int C[M][K], const int A[M][N], const int B[N][K]) {
  for (int i = 0; i < M; i++) {
    for (int k = 0; k < K; k++) {
      int sum = 0;
      for (int j = 0; j < N; j++) {
        sum += A[i][j] * B[j][k];
      }
      C[i][k] = sum;
    }
  }
}

int main() {
  int A[M][N], B[N][K], C[M][K];

  for (int i = 0; i < M; i++) {
    for (int j = 0; j < N; j++) {
      A[i][j] = 1;
    }
  }

  for (int j = 0; j < N; j++) {
    for (int k = 0; k < K; k++) {
      B[j][k] = 2;
    }
  }

  mmul(C, A, B);

  return 0;
}
