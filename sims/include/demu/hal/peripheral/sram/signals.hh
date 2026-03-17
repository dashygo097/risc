#pragma once

#include "../../../isa/isa.hh"
#include <cstdint>

namespace demu::hal::sram {
using namespace isa;

template <typename T> struct CacheRequest {
  uint8_t *valid;
  uint8_t *ready;
  uint8_t *op;
  addr_t *addr;
  T *data;
};

struct CacheReadOnlyRequest {
  uint8_t *valid;
  uint8_t *ready;
  addr_t *addr;
};

template <typename T> struct CacheResponse {
  uint8_t *valid;
  uint8_t *ready;
  T *data;
  bool *hit;
};

template <typename T> struct CacheSignals {
  CacheRequest<T> req;
  CacheResponse<T> resp;
};

template <typename T> struct CacheReadOnlySignals {
  CacheReadOnlyRequest req;
  CacheResponse<T> resp;
};

} // namespace demu::hal::sram
