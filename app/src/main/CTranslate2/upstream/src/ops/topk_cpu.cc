#include "ctranslate2/ops/topk.h"

#include <algorithm>
#include <cstdint>
#include <numeric>
#include <type_traits>
#include <utility>

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#  include <arm_neon.h>
#  define CT2_TOPK_HAS_NEON 1
#else
#  define CT2_TOPK_HAS_NEON 0
#endif

#include "cpu/parallel.h"
#include "type_dispatch.h"

namespace ctranslate2 {
  namespace ops {

#if CT2_TOPK_HAS_NEON
    namespace {

      // 128-bit NEONレジスタで同時比較するfloat要素数。
      constexpr dim_t kNeonFloatLanes = 4;

      /**
       * float配列から最大値と最初の出現位置をNEONで検索する。
       * row例: {0.1f, 0.8f, 0.3f}、depth例: 3。
       * 戻り値例: {0.8f, 1}。rowがdepth要素を持たない場合は未定義で、例外は送出しない。
       */
      std::pair<float, int32_t> neon_argmax(
          const float* row,
          const dim_t depth) noexcept {
        if (depth < kNeonFloatLanes) {
          const float* maximum = std::max_element(row, row + depth);
          return {*maximum, static_cast<int32_t>(maximum - row)};
        }

        float32x4_t maximum_values = vld1q_f32(row);
        int32x4_t maximum_indices = {0, 1, 2, 3};
        dim_t index = kNeonFloatLanes;
        for (; index + kNeonFloatLanes <= depth; index += kNeonFloatLanes) {
          const float32x4_t values = vld1q_f32(row + index);
          const int32x4_t indices = {
              static_cast<int32_t>(index),
              static_cast<int32_t>(index + 1),
              static_cast<int32_t>(index + 2),
              static_cast<int32_t>(index + 3)};
          const uint32x4_t greater = vcgtq_f32(values, maximum_values);
          maximum_values = vbslq_f32(greater, values, maximum_values);
          maximum_indices = vbslq_s32(greater, indices, maximum_indices);
        }

        alignas(16) float lane_values[kNeonFloatLanes];
        alignas(16) int32_t lane_indices[kNeonFloatLanes];
        vst1q_f32(lane_values, maximum_values);
        vst1q_s32(lane_indices, maximum_indices);
        float maximum = lane_values[0];
        int32_t maximum_index = lane_indices[0];
        for (dim_t lane = 1; lane < kNeonFloatLanes; ++lane) {
          if (lane_values[lane] > maximum
              || (lane_values[lane] == maximum
                  && lane_indices[lane] < maximum_index)) {
            maximum = lane_values[lane];
            maximum_index = lane_indices[lane];
          }
        }
        for (; index < depth; ++index) {
          if (row[index] > maximum) {
            maximum = row[index];
            maximum_index = static_cast<int32_t>(index);
          }
        }
        return {maximum, maximum_index};
      }

    }  // namespace
#endif

    template <Device D, typename DataType, typename IndexType>
    void TopK::compute(const StorageView& x,
                       StorageView& values,
                       StorageView& indices) const {
      const dim_t depth = x.dim(-1);
      const dim_t batch_size = x.size() / depth;

      const DataType* x_data = x.data<DataType>();
      DataType* v_data = values.data<DataType>();
      IndexType* i_data = indices.data<IndexType>();

      if (_k == 1) {
        cpu::parallel_for(0, batch_size, 1, [&](dim_t begin, dim_t end) {
          for (dim_t i = begin; i < end; ++i) {
            const DataType* row = x_data + i * depth;
#if CT2_TOPK_HAS_NEON
            if constexpr (std::is_same_v<DataType, float>
                          && std::is_same_v<IndexType, int32_t>) {
              const auto [maximum, maximum_index] = neon_argmax(row, depth);
              v_data[i] = maximum;
              i_data[i] = maximum_index;
              continue;
            }
#endif
            const DataType* max = std::max_element(row, row + depth);
            v_data[i] = *max;
            i_data[i] = std::distance(row, max);
          }
        });

      } else {
        cpu::parallel_for(0, batch_size, 1, [&](dim_t begin, dim_t end) {
          for (dim_t i = begin; i < end; ++i) {
            const auto* input = x_data + (i * depth);
            auto* val = v_data + (i * _k);
            auto* ind = i_data + (i * _k);

            StorageView range({depth}, indices.dtype());
            auto* ids = range.data<IndexType>();
            std::iota(ids, ids + depth, 0);
            std::partial_sort(ids, ids + _k, ids + depth,
                              [&input](const IndexType& i1, const IndexType& i2) {
                                return input[i1] > input[i2];
                              });
            for (dim_t j = 0; j < _k; ++j) {
              ind[j] = ids[j];
              val[j] = input[ind[j]];
            }
          }
        });

      }
    }

#define DECLARE_IMPL(T)                                                 \
    template void                                                       \
    TopK::compute<Device::CPU, T, int32_t>(const StorageView& x,        \
                                           StorageView& values,         \
                                           StorageView& indices) const;

    DECLARE_ALL_TYPES(DECLARE_IMPL)

  }
}
